package com.sakura.client.safety;

import java.util.Locale;

/**
 * Prints the statistics of the intervals {@link HumanizedDistribution} produces.
 *
 * <p>This is the proof that the humanisation is real rather than a claim in a comment. It samples the
 * distribution and reports the mean, the variance, the observed extremes and a histogram, next to the same
 * statistics for a uniform draw over the same range. A uniform draw has a flat histogram and a variance of
 * {@code (max - min)^2 / 12}; the log-normal has a peak near the middle, a right-hand tail, and a visibly
 * larger variance, which is exactly the difference an observer profiles.</p>
 *
 * <p>Run it directly — the safety package has no Minecraft dependencies, so it works on a plain JVM:</p>
 *
 * <pre>
 * java -cp build/classes/java/main com.sakura.client.safety.DistributionCheck
 * </pre>
 */
public final class DistributionCheck {

	/** Samples per range. Large enough for the tails to show up, small enough to read. */
	private static final int SAMPLES = 1000;

	/** Histogram columns. */
	private static final int BUCKETS = 16;

	private DistributionCheck() {
	}

	public static void main(String[] args) {
		report("AutoClicker interval", 50.0, 125.0, "ms");
		report("TriggerBot interval", 50.0, 125.0, "ms");
		report("Rotation reaction delay", 100.0, 250.0, "ms");
		report("AutoTotem switch delay", 0.0, 200.0, "ms");
	}

	private static void report(String label, double min, double max, String unit) {
		double[] logNormal = new double[SAMPLES];
		double[] uniform = new double[SAMPLES];
		double sum = 0.0;
		double sumSquares = 0.0;
		double uniformSum = 0.0;
		double uniformSumSquares = 0.0;
		double lowest = Double.MAX_VALUE;
		double highest = -Double.MAX_VALUE;

		HumanizedDistribution.reset();

		for (int index = 0; index < SAMPLES; index++) {
			double drawn = HumanizedDistribution.between(min, max);
			double flat = uniform(min, max);

			logNormal[index] = drawn;
			uniform[index] = flat;

			sum += drawn;
			sumSquares += drawn * drawn;
			uniformSum += flat;
			uniformSumSquares += flat * flat;
			lowest = Math.min(lowest, drawn);
			highest = Math.max(highest, drawn);
		}

		double mean = sum / SAMPLES;
		double variance = sumSquares / SAMPLES - mean * mean;
		double uniformMean = uniformSum / SAMPLES;
		double uniformVariance = uniformSumSquares / SAMPLES - uniformMean * uniformMean;

		System.out.println();
		System.out.printf(Locale.ROOT, "=== %s: range [%.1f, %.1f] %s, %d samples ===%n",
				label, min, max, unit, SAMPLES);
		System.out.printf(Locale.ROOT, "  humanized  mean %8.2f   variance %9.2f   sd %7.2f   min %7.2f   max %7.2f%n",
				mean, variance, Math.sqrt(variance), lowest, highest);
		System.out.printf(Locale.ROOT, "  uniform    mean %8.2f   variance %9.2f   sd %7.2f%n",
				uniformMean, uniformVariance, Math.sqrt(uniformVariance));
		System.out.printf(Locale.ROOT, "  theoretical uniform variance (max-min)^2/12 = %.2f%n",
				(max - min) * (max - min) / 12.0);
		System.out.printf(Locale.ROOT, "  variance ratio humanized/uniform = %.2fx%n", variance / uniformVariance);
		System.out.printf(Locale.ROOT, "  inside range: %d/%d (%d outside, clamped)%n",
				countInside(logNormal, min, max), SAMPLES, SAMPLES - countInside(logNormal, min, max));

		histogram("  humanized", logNormal, min, max);
		histogram("  uniform  ", uniform, min, max);
	}

	private static void histogram(String label, double[] values, double min, double max) {
		int[] buckets = new int[BUCKETS];
		double width = (max - min) / BUCKETS;

		if (width <= 0.0) {
			return;
		}

		for (double value : values) {
			int index = (int) ((value - min) / width);

			if (index >= BUCKETS) {
				index = BUCKETS - 1;
			}

			buckets[Math.max(0, index)]++;
		}

		int peak = 0;

		for (int bucket : buckets) {
			peak = Math.max(peak, bucket);
		}

		for (int index = 0; index < BUCKETS; index++) {
			// 40 columns is the widest scale that still fits a terminal line next to the labels.
			int columns = peak == 0 ? 0 : Math.round(buckets[index] * 40.0f / peak);
			System.out.printf(Locale.ROOT, "%s %7.1f..%7.1f |%s %d%n",
					label, min + index * width, min + (index + 1) * width, "#".repeat(columns), buckets[index]);
		}
	}

	private static int countInside(double[] values, double min, double max) {
		int inside = 0;

		for (double value : values) {
			// A clamped value sits exactly on an endpoint, so endpoints count as inside.
			if (value >= min && value <= max) {
				inside++;
			}
		}

		return inside;
	}

	private static double uniform(double min, double max) {
		return min + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * (max - min);
	}
}
