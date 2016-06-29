package tornado.collections.graphics;

import static tornado.collections.math.TornadoMath.max;
import static tornado.collections.math.TornadoMath.min;
import static tornado.collections.types.Float3.add;
import static tornado.collections.types.Float3.cross;
import static tornado.collections.types.Float3.div;
import static tornado.collections.types.Float3.dot;
import static tornado.collections.types.Float3.max;
import static tornado.collections.types.Float3.min;
import static tornado.collections.types.Float3.mult;
import static tornado.collections.types.Float3.normalise;
import static tornado.collections.types.Float3.sub;
import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.Write;
import tornado.collections.types.Float3;
import tornado.collections.types.Float4;
import tornado.collections.types.ImageFloat;
import tornado.collections.types.ImageFloat3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.VolumeOps;
import tornado.collections.types.VolumeShort2;

public class GraphicsMath {

    private static final float INVALID = -2;

    public static void vertex2normal(@Write ImageFloat3 normals,
             @Read ImageFloat3 verticies) {

        for (@Parallel
        int y = 0; y < normals.Y(); y++)
            for (@Parallel
            int x = 0; x < normals.X(); x++) {

                final Float3 left = verticies.get(Math.max(x - 1, 0), y);
                final Float3 right = verticies.get(
                        Math.min(x + 1, verticies.X() - 1), y);
                final Float3 up = verticies.get(x, Math.max(y - 1, 0));
                final Float3 down = verticies.get(x,
                        Math.min(y + 1, verticies.Y() - 1));

                final Float3 dxv = sub(right, left);
                final Float3 dyv = sub(down, up);

                boolean invalidNormal = left.getZ() == 0 || right.getZ() == 0
                        || up.getZ() == 0 || down.getZ() == 0;
                final Float3 normal;
                if (invalidNormal) {
                    normal = new Float3(INVALID, 0f, 0f);
                } else {
                    normal = normalise(cross(dyv, dxv));
                }

                normals.set(x, y, normal);
            }
    }

    public static void depth2vertex( @Write ImageFloat3 verticies,
             @Read ImageFloat depths, @Read Matrix4x4Float invK) {

        for (@Parallel
        int y = 0; y < depths.Y(); y++)
            for (@Parallel
            int x = 0; x < depths.X(); x++) {

                final float depth = depths.get(x, y);
                final Float3 pix = new Float3(x, y, 1f);

                final Float3 vertex = (depth > 0) ? mult(rotate(invK, pix),
                        depth) : new Float3();

                verticies.set(x, y, vertex);
            }
    }

    @Deprecated
    public static final void rotate(Float3 y, Matrix4x4Float m, Float3 x) {
        for (int i = 0; i < 3; i++) {
            y.set(i, dot(m.row(i).asFloat3(), x));
        }
    }

    public static final Float3 rotate(Matrix4x4Float m, Float3 x) {
        final Float3 result = new Float3(dot(m.row(0).asFloat3(), x), dot(m
                .row(1).asFloat3(), x), dot(m.row(2).asFloat3(), x));
        return result;
    }

    public final static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public final static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public static final void getInverseCameraMatrix(Float4 k, Matrix4x4Float m) {
        m.fill(0f);

        m.set(0, 0, 1f / k.getX());
        m.set(0, 2, -k.getZ() / k.getX());
        m.set(1, 1, 1f / k.getY());
        m.set(1, 2, -k.getW() / k.getY());
        m.set(2, 2, 1);
        m.set(3, 3, 1);
    }

    /***
     * Creates a 4x4 matrix representing the intrinsic camera matrix
     * 
     * @param k
     *            - camera parameters {f_x,f_y,x_0,y_0} where {f_x,f_y}
     *            specifies
     *            the focal length of the camera and {x_0,y_0} the principle
     *            point
     * @param m
     *            - returned matrix
     */
    public static final void getCameraMatrix(Float4 k, Matrix4x4Float m) {
        m.fill(0f);

        // focal length - f_x
        m.set(0, 0, k.getX());
        // focal length - f_y
        m.set(1, 1, k.getY());

        // principle point - x_0
        m.set(0, 2, k.getZ());

        // principle point - y_0
        m.set(1, 2, k.getW());

        m.set(2, 2, 1);
        m.set(3, 3, 1);
    }

    /*
     * Performs a rigid transformation which maps one co-ordinate system to
     * another
     * [ R11 R12 R13 t1 ] R => 3x3 rotation matrix
     * T = [ R21 R22 R23 t2 ] t => 3x1 translation (column vector)
     * [ R31 R32 R33 t3 ]
     * [ 0 0 0 1 ]
     * P = [ x ] column vector representing the point to be transformed
     * [ y ]
     * [ z ]
     * [ 1 ]
     */
    public static final void rigidTransform(Matrix4x4Float T, Float3 point,
            Float3 result) {
        final Float3 col = T.column(3).asFloat3();
        result.setX(dot(T.row(0).asFloat3(), point));
        result.setY(dot(T.row(1).asFloat3(), point));
        result.setZ(dot(T.row(2).asFloat3(), point));
        add(result, col, result);

    }

    public static final Float3 rigidTransform(Matrix4x4Float T, Float3 point) {
        final Float3 translation = T.column(3).asFloat3();
        final Float3 rotation = new Float3(dot(T.row(0).asFloat3(), point),
                dot(T.row(1).asFloat3(), point),
                dot(T.row(2).asFloat3(), point));
        return add(rotation, translation);
    }

    public static final Float4 raycastPoint(final VolumeShort2 volume,
            final Float3 dim, final int x, final int y,
            final Matrix4x4Float view, float nearPlane, float farPlane,
            float smallStep, float largeStep) {

        final Float3 position = new Float3(x, y, 1f);

        // retrive translation from matrix (col 3, elements =3 )
        final Float3 origin = view.column(3).asFloat3();

        final Float3 direction = rotate(view, position);
        final Float3 invR = div(new Float3(1f, 1f, 1f), direction);

        final Float3 tbot = mult(mult(invR, origin), -1f);
        final Float3 ttop = mult(invR, sub(dim, origin));

        final Float3 tmin = min(ttop, tbot);
        final Float3 tmax = max(ttop, tbot);

        final float largestTmin = max(tmin);
        final float smallestTmax = min(tmax);

        final float tnear = max(largestTmin, nearPlane);
        final float tfar = min(smallestTmax, farPlane);

        if (tnear < tfar) {

            float t = tnear;
            float stepsize = largeStep;

            Float3 pos = add(mult(direction, t), origin);

            float f_t = VolumeOps.interp(volume, dim, pos);

            float f_tt = 0f;
            if (f_t > 0) {
                for (; t < tfar; t += stepsize) {
                    pos = add(mult(direction, t), origin);

                    f_tt = VolumeOps.interp(volume, dim, pos);

                    if (f_tt < 0f)
                        break;

                    if (f_tt < 0.8f) {
                        stepsize = smallStep;
                    }

                    f_t = f_tt;
                }

                if (f_tt < 0) {
                    t = t + ((stepsize * f_tt) / (f_t - f_tt));
                    pos = add(mult(direction, t), origin);
                    return new Float4(pos.getX(), pos.getY(), pos.getZ(), t);
                }
            }

        }

        return new Float4();
    }

}