package com.supermercerbros.gameengine.objects;

import java.util.HashMap;
import java.util.LinkedList;

import android.opengl.Matrix;

import com.supermercerbros.gameengine.collision.Bounds;
import com.supermercerbros.gameengine.collision.Collider;
import com.supermercerbros.gameengine.collision.Collision;
import com.supermercerbros.gameengine.engine.Engine;
import com.supermercerbros.gameengine.engine.Normals;
import com.supermercerbros.gameengine.motion.Movement;
import com.supermercerbros.gameengine.motion.MovementData;

/**
 * Represents a 3D mesh object.
 */
public class GameObject implements Collider {
	public static final String TAG = "GameObject";
	
	/**
	 * Contains the indices of the vertices for the elements (i.e. triangles) in
	 * this object.
	 */
	public final short[] indices;
	
	/**
	 * Contains the current object-space coordinates of the vertices used in
	 * this </code>GameObject</code>. Every three values represent one vertex.
	 */
	public final float[] verts;
	
	/**
	 * Contains either the UV coordinates (float pairs) or colors (three floats
	 * each) of the vertices in this <code>GameObject</code>.
	 * 
	 * @see <a href="http://en.wikipedia.org/wiki/UV_mapping">UV Mapping</a>
	 *      (Wikipedia)
	 */
	protected final float[] mtl;
	
	/**
	 * Contains the normals of the vertices of this <code>GameObject</code>.
	 */
	public final float[] normals;
	
	/**
	 * Contains the indices of the vertex pairs that are identical
	 * geometrically. Used in normal calculation.
	 */
	public final short[][] doubles;
	
	/**
	 * The Metadata about this GameObject.
	 */
	public final Metadata info;
	
	/**
	 * The model transformation matrix for this GameObject
	 */
	public final float[] modelMatrix = new float[16];
	/**
	 * The current Movement of the GameObject
	 */
	protected Movement motion;
	/**
	 * The MovementData containing GameObject-specific information about
	 * {@link #motion}
	 */
	protected final MovementData motionData;
	
	/**
	 * Contains the VBO offset at which this GameObject's data is loaded. This
	 * is used for multiple instances of the same primitive.
	 */
	protected int[] instanceLoaded = { -1 };
	private boolean isInstance = false;
	
	/**
	 * Used by the Engine class when loading the GameObject into buffers.
	 */
	public int iOffset = -1;
	
	
	/**
	 * 
	 * @param verts
	 *            The object-space coordinates of the object's vertices. Every
	 *            three values represent one vertex (x-, y-, and z-coordinates).
	 * @param indices
	 *            The indices of the vertices for the triangles in this object.
	 * @param normals
	 *            The coordinates of the normal vectors of the vertices.
	 * @param mtl
	 *            The UV texture coordinates of the triangles.
	 * @param mtl
	 *            A Material object to use when for rendering
	 */
	public GameObject(float[] verts, short[] indices, float[] normals,
			float[] uvs, short[][] doubles, Material mtl) {
		this.verts = verts;
		this.indices = indices;
		this.mtl = uvs;
		this.normals = (normals != null) ? normals : new float[verts.length];
		this.doubles = doubles;
		
		info = new Metadata();
		info.size = indices.length;
		info.count = verts.length / 3;
		info.mtl = mtl;
		
		motionData = new MovementData();
		
		collisions = new HashMap<Collision, Collider>();
		
		Matrix.setIdentityM(modelMatrix, 0);
		
		if (normals == null) {
			Normals.calculate(this);
		}
	}
	
	private GameObject(float[] verts, short[] indices, float[] uvs,
			float[] normals, int[] instanceLoaded, Material mtl,
			short[][] doubles) {
		this(verts, indices, normals, uvs, doubles, mtl);
		this.instanceLoaded = instanceLoaded;
		this.isInstance = true;
	}
	
	/**
	 * Returns a LinkedList containing one instance of this GameObject per
	 * Material in <code>materials</code>.
	 * 
	 * @param materials
	 *            An array of the Materials to use for the instances.
	 * @return a LinkedList of GameObjects, or null if no materials are given.
	 */
	public LinkedList<GameObject> instance(Material... materials) {
		if (materials.length == 0) {
			return null;
		}
		LinkedList<GameObject> instances = new LinkedList<GameObject>();
		for (Material material : materials) {
			instances.add(getInstance(material));
		}
		return instances;
	}
	
	/**
	 * Creates a copy of this GameObject. Subclasses should override this and
	 * construct a new instance of the same class. The default method creates a
	 * new GameObject with pointers to the same <code>verts</code>,
	 * <code>indices</code>, <code>mtl</code>, <code>normals</code>, and
	 * <code>doubles</code> arrays.
	 * 
	 * @param material
	 *            The Material to apply to the copy
	 * @return A copy of this GameObject
	 */
	protected GameObject getInstance(Material material) {
		return new GameObject(verts, indices, mtl, normals, instanceLoaded,
				material, doubles);
	}
	
	/**
	 * This method is called to tell the object to recalculate its
	 * transformation matrix for the given point in time, in milliseconds.
	 * 
	 * @param time
	 *            The time of the frame currently being calculated,
	 *            in milliseconds.
	 * 
	 * @see AnimatedMeshObject#drawMatrix(long)
	 */
	public void drawMatrix(long time) {
		synchronized (motionData) {
			if (motion != null) {
				motion.getFrame(this, motionData, time);
			}
		}
	}
	
	/**
	 * This method is called to tell the object to update its vertices for the
	 * given point in time, in milliseconds.
	 * 
	 * <p> The default implementation does nothing. To do something with the
	 * object-space (local) vertices every frame, override this method in a
	 * <code>GameObject</code> subclass. </p>
	 * 
	 * @param time
	 *            The time of the frame currently being calculated,
	 *            in milliseconds.
	 */
	public void drawVerts(long time) {
		// Subclasses can do something here.
	}
	
	/**
	 * @return true if this GameObject has been marked for deletion.
	 * 
	 * @see #markForDeletion()
	 */
	public boolean isMarkedForDeletion() {
		return info.delete;
	}
	
	/**
	 * Marks this GameObject for deletion. The Engine doesn't update this
	 * GameObject for rendering anymore, but it is not actually deleted from the
	 * Engine until {@link Engine#flushDeletedObjects()} is called. Should only
	 * be called from the Engine thread
	 */
	public void markForDeletion() {
		info.delete = true;
	}
	
	/**
	 * Sets and starts the Movement that is used to animate this GameObject's
	 * location.
	 * 
	 * @param time
	 *            The current time, in milliseconds.
	 * @param duration
	 *            The duration of the movement, in milliseconds.
	 */
	public void startMovement(Movement motion, long time, long duration) {
		synchronized (motionData) {
			this.motion = motion;
			this.motionData.set(time, duration, modelMatrix);
		}
	}
	
	public void endMovement() {
		synchronized (motionData) {
			this.motion = null;
		}
	}
	
	private final HashMap<Collision, Collider> collisions;
	private Bounds bounds;
	
	@Override
	public Bounds getBounds() {
		return bounds;
	}
	
	public void setBounds(Bounds bounds) {
		this.bounds = bounds;
	}
	
	@Override
	public float[] getMatrix() {
		return modelMatrix;
	}
	
	@Override
	public void clearCollisions() {
		collisions.clear();
	}
	
	@Override
	public void addCollision(Collider other, Collision collision) {
		collisions.put(collision, other);
	}
	
}
