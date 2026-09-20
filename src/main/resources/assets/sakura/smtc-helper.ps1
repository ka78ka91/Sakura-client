# Sakura Client - Windows media bridge.
#
# Reports the Windows "now playing" session (the same source the volume flyout uses, so it works
# with Spotify, NetEase Cloud Music, QQ Music, browsers, foobar2000 and anything else that registers
# a media session) as one compact JSON line per poll on stdout. Also writes the current cover art to
# a file and accepts transport commands from a command file.
#
# Must run under Windows PowerShell 5.1: the WinRT projection this script relies on is not available
# in PowerShell 7. The game launches it hidden and reads its stdout.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File smtc-helper.ps1 `
#       -ArtworkPath <path> -CommandPath <path> [-IntervalMillis 500]

param(
    [Parameter(Mandatory = $true)][string]$ArtworkPath,
    [Parameter(Mandatory = $true)][string]$CommandPath,
    [int]$IntervalMillis = 500
)

$ErrorActionPreference = 'Continue'

# stdout must be UTF-8: the default console code page would mangle non-ASCII titles on the way out.
try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
} catch {
}

Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null

# Resolve the WinRT types and the IAsyncOperation awaiter.
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

function Await($operation, $resultType) {
    $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

function JsonEscape([string]$value) {
    if ([string]::IsNullOrEmpty($value)) {
        return ''
    }

    $escaped = $value -replace '\\', '\\'
    $escaped = $escaped -replace '"', '\"'
    $escaped = $escaped -replace "`r", ' '
    $escaped = $escaped -replace "`n", ' '
    $escaped = $escaped -replace "`t", ' '
    return $escaped
}

$script:artCounter = 0
$script:lastArtKey = ''
$script:lastLine = ''

function Write-Line([string]$line) {
    # A dead reader (the game closed) throws here, which is how the helper notices it should stop.
    [Console]::Out.WriteLine($line)
    [Console]::Out.Flush()
    $script:lastLine = $line
}

function Save-Artwork($reference, [string]$path) {
    try {
        $stream = Await ($reference.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    } catch {
        return $false
    }

    if ($null -eq $stream) {
        return $false
    }

    try {
        $size = [int]$stream.Size

        if ($size -le 0 -or $size -gt 16777216) {
            return $false
        }

        $reader = [Windows.Storage.Streams.DataReader]::new($stream)

        try {
            Await ($reader.LoadAsync([uint32]$size)) ([uint32]) | Out-Null
            $bytes = New-Object byte[] $size
            $reader.ReadBytes($bytes)
            [System.IO.File]::WriteAllBytes($path, $bytes)
            return $true
        } finally {
            $reader.Dispose()
        }
    } catch {
        return $false
    } finally {
        $stream.Dispose()
    }
}

function Get-Manager {
    return Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
}

function Get-Session($manager) {
    if ($null -eq $manager) {
        return $null
    }

    $current = $manager.GetCurrentSession()

    if ($null -ne $current) {
        return $current
    }

    # No session has focus: fall back to whichever one is actually playing.
    foreach ($candidate in $manager.GetSessions()) {
        if ($candidate.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') {
            return $candidate
        }
    }

    $sessions = @($manager.GetSessions())
    return $(if ($sessions.Count -gt 0) { $sessions[0] } else { $null })
}

function Invoke-Transport($session, [string]$command) {
    if ($null -eq $session) {
        return
    }

    try {
        switch ($command.ToLowerInvariant()) {
            'playpause' { Await ($session.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
            'next' { Await ($session.TrySkipNextAsync()) ([bool]) | Out-Null }
            'previous' { Await ($session.TrySkipPreviousAsync()) ([bool]) | Out-Null }
        }
    } catch {
    }
}

function Read-Command {
    try {
        if (-not (Test-Path -LiteralPath $CommandPath)) {
            return ''
        }

        $text = [System.IO.File]::ReadAllText($CommandPath)
        [System.IO.File]::WriteAllText($CommandPath, '')

        if ([string]::IsNullOrWhiteSpace($text)) {
            return ''
        }

        return $text.Trim()
    } catch {
        return ''
    }
}

$manager = $null
$managerAt = [datetime]::MinValue

while ($true) {
    try {
        $command = Read-Command

        if ($command -eq 'quit') {
            break
        }

        # The manager is cheap to keep, but re-request it once a minute in case the OS recycled it.
        if ($null -eq $manager -or ([datetime]::UtcNow - $managerAt).TotalSeconds -gt 60) {
            $manager = Get-Manager
            $managerAt = [datetime]::UtcNow
        }

        $session = Get-Session $manager

        if ($null -eq $session) {
            if ($command -ne '') {
                Write-Line ('{"ok":true,"status":"None","ack":"' + (JsonEscape $command) + '"}')
            } else {
                Write-Line '{"ok":true,"status":"None"}'
            }

            Start-Sleep -Milliseconds $IntervalMillis
            continue
        }

        if ($command -ne '') {
            Invoke-Transport $session $command
        }

        $info = $session.GetPlaybackInfo()
        $status = $info.PlaybackStatus.ToString()
        $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        $timeline = $session.GetTimelineProperties()

        $title = ''
        $artist = ''
        $album = ''
        $artWritten = $false

        if ($null -ne $props) {
            $title = [string]$props.Title
            $artist = [string]$props.Artist
            $album = [string]$props.AlbumTitle

            $artKey = $title + '|' + $artist + '|' + $album

            if ($artKey -ne $script:lastArtKey -and $null -ne $props.Thumbnail) {
                $script:lastArtKey = $artKey

                if (Save-Artwork $props.Thumbnail $ArtworkPath) {
                    $script:artCounter++
                    $artWritten = $true
                }
            }
        }

        $position = 0.0
        $duration = 0.0

        if ($null -ne $timeline) {
            $position = [math]::Round($timeline.Position.TotalSeconds, 2)
            $duration = [math]::Round($timeline.EndTime.TotalSeconds, 2)
        }

        $app = [string]$session.SourceAppUserModelId

        $json = '{"ok":true'
        $json += ',"status":"' + (JsonEscape $status) + '"'
        $json += ',"app":"' + (JsonEscape $app) + '"'
        $json += ',"title":"' + (JsonEscape $title) + '"'
        $json += ',"artist":"' + (JsonEscape $artist) + '"'
        $json += ',"album":"' + (JsonEscape $album) + '"'
        $json += ',"pos":' + $position.ToString([System.Globalization.CultureInfo]::InvariantCulture)
        $json += ',"dur":' + $duration.ToString([System.Globalization.CultureInfo]::InvariantCulture)
        $json += ',"art":' + $script:artCounter
        $json += ',"hasArt":' + $(if ($artWritten -or $script:artCounter -gt 0) { 'true' } else { 'false' })
        $json += ',"ack":"' + (JsonEscape $command) + '"'
        $json += '}'

        Write-Line $json
    } catch {
        # Never die on a transient failure: report it and keep polling.
        try {
            Write-Line ('{"ok":false,"error":"' + (JsonEscape $_.Exception.Message) + '"}')
        } catch {
            break
        }
    }

    Start-Sleep -Milliseconds $IntervalMillis
}
