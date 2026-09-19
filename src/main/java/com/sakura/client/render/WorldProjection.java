package com.sakura.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Per-frame mapping from world space onto the GUI surface, shared by the overlay modules.
 *
 * <p>The view and projection matrices are read from Fabric's world extraction context, which hands out the
 * matrices vanilla itself used for that frame. Deriving them here instead would mean re-deriving the field of
 * view, which depends on sprinting, being under water and the "FOV Effects" option — get any of that wrong and
 * every box is subtly misplaced. The extraction pass runs before the frame is drawn, so the HUD pass that
 * follows projects with this frame's matrices.</p>
 *
 * <p>Vanilla renders the world camera relative: entity positions are drawn as {@code pos - cameraPos} and the
 * view matrix carries the rotation only. That is detected here rather than assumed, because subtracting the
 * camera position from a matrix that already contains it would silently offset every overlay.</p>
 *
 * <p>All output is in GUI pixels with the origin in the top left corner, i.e. the same space
 * {@link net.minecraft.client.gui.DrawContext} draws in.</p>
 */
public final class WorldProjection {

	/** A projected point on the GUI surface. */
	public static final class ScreenPoint {

		public float x;
		public float y;
		/** Normalised depth, smaller is closer, used to order overlays. */
		public float depth;
		/** Whether the point landed inside the visible window. */
		public boolean onScreen;
	}

	/** A projected axis-aligned rectangle on the GUI surface. */
	public static final class ScreenRect {

		public float minX;
		public float minY;
		public float maxX;
		public float maxY;
		/** Depth of the closest corner. */
		public float depth;

		public float width() {
			return this.maxX - this.minX;
		}

		public float height() {
			return this.maxY - this.minY;
		}
	}

	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final Vector4f SCRATCH = new Vector4f();

	private static double cameraX;
	private static double cameraY;
	private static double cameraZ;
	/** Whether positions have to be shifted by the camera position before projecting. */
	private static boolean cameraRelative = true;
	private static boolean captured;
	private static long capturedFrame = -1;
	private static long frame;
	private static int screenWidth;
	private static int screenHeight;

	private WorldProjection() {
	}

	/** Hooks the per-frame matrix capture into the client. Call once from the mod initialiser. */
	public static void register() {
		WorldRenderEvents.END_EXTRACTION.register(WorldProjection::capture);
	}

	private static void capture(WorldExtractionContext context) {
		VIEW.set(context.viewMatrix());
		PROJECTION.set(context.cullProjectionMatrix());

		Camera camera = context.camera();
		Vec3d position = camera.getCameraPos();
		cameraX = position.x;
		cameraY = position.y;
		cameraZ = position.z;

		cameraRelative = Math.abs(VIEW.m30()) + Math.abs(VIEW.m31()) + Math.abs(VIEW.m32()) < 1.0e-3f;

		captured = true;
		capturedFrame = frame;
	}

	/**
	 * Publishes the GUI surface the overlays are about to be drawn on. Called once per frame by the overlay
	 * renderer before any module draws.
	 */
	public static void beginFrame(int width, int height) {
		frame++;
		screenWidth = width;
		screenHeight = height;
	}

	/**
	 * @return whether a world frame was captured recently enough to place overlays with. A capture older than
	 * a couple of frames means the world is no longer being rendered (menu, paused, no world yet), and drawing
	 * with those matrices would put the overlay in the wrong place.
	 */
	public static boolean isReady() {
		return captured && frame - capturedFrame <= 2 && screenWidth > 0 && screenHeight > 0;
	}

	public static int getScreenWidth() {
		return screenWidth;
	}

	public static int getScreenHeight() {
		return screenHeight;
	}

	/** @return the position the frame was rendered from, i.e. the camera, not the player */
	public static Vec3d getCameraPos() {
		return new Vec3d(cameraX, cameraY, cameraZ);
	}

	// ------------------------------------------------------------------ projection

	/**
	 * Projects a world position onto the GUI surface.
	 *
	 * @return {@code false} when the position is behind the camera and cannot be drawn at all
	 */
	public static boolean project(Vec3d position, ScreenPoint out) {
		return project(position.x, position.y, position.z, out);
	}

	/** Projects a world position onto the GUI surface, see {@link #project(Vec3d, ScreenPoint)}. */
	public static boolean project(double x, double y, double z, ScreenPoint out) {
		if (!isReady() || !projectToClipSpace(x, y, z)) {
			out.onScreen = false;
			return false;
		}

		float w = SCRATCH.w();
		float ndcX = SCRATCH.x() / w;
		float ndcY = SCRATCH.y() / w;

		out.x = (ndcX * 0.5f + 0.5f) * screenWidth;
		out.y = (0.5f - ndcY * 0.5f) * screenHeight;
		out.depth = SCRATCH.z() / w;
		out.onScreen = out.x >= 0.0f && out.x <= screenWidth && out.y >= 0.0f && out.y <= screenHeight;

		return true;
	}

	/**
	 * Projects the eight corners of a box and returns the screen rectangle that contains them.
	 *
	 * <p>A box that crosses the camera plane has corners with a non-positive {@code w}, for which the
	 * perspective divide is meaningless. Such a box is reported as not drawable instead of being drawn
	 * wrongly; a 2D overlay has no way to represent geometry that surrounds the camera.</p>
	 *
	 * @return {@code false} when the box is behind the camera or entirely off screen
	 */
	public static boolean projectBox(Box box, ScreenRect out) {
		if (!isReady()) {
			return false;
		}

		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		float depth = Float.MAX_VALUE;

		for (int corner = 0; corner < 8; corner++) {
			double x = (corner & 1) == 0 ? box.minX : box.maxX;
			double y = (corner & 2) == 0 ? box.minY : box.maxY;
			double z = (corner & 4) == 0 ? box.minZ : box.maxZ;

			if (!projectToClipSpace(x, y, z)) {
				return false;
			}

			float w = SCRATCH.w();
			float screenX = (SCRATCH.x() / w * 0.5f + 0.5f) * screenWidth;
			float screenY = (0.5f - SCRATCH.y() / w * 0.5f) * screenHeight;

			minX = Math.min(minX, screenX);
			minY = Math.min(minY, screenY);
			maxX = Math.max(maxX, screenX);
			maxY = Math.max(maxY, screenY);
			depth = Math.min(depth, SCRATCH.z() / w);
		}

		out.minX = minX;
		out.minY = minY;
		out.maxX = maxX;
		out.maxY = maxY;
		out.depth = depth;

		return maxX >= 0.0f && minX <= screenWidth && maxY >= 0.0f && minY <= screenHeight;
	}

	/**
	 * Runs one position through the view and projection matrices, leaving the clip space position in
	 * {@link #SCRATCH}.
	 *
	 * @return {@code false} when the position sits on or behind the camera plane
	 */
	private static boolean projectToClipSpace(double x, double y, double z) {
		float viewX = (float) (cameraRelative ? x - cameraX : x);
		float viewY = (float) (cameraRelative ? y - cameraY : y);
		float viewZ = (float) (cameraRelative ? z - cameraZ : z);

		SCRATCH.set(viewX, viewY, viewZ, 1.0f);
		VIEW.transform(SCRATCH);
		PROJECTION.transform(SCRATCH);

		return SCRATCH.w() > 1.0e-4f;
	}
}
