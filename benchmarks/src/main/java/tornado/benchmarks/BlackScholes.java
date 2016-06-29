package tornado.benchmarks;

import tornado.api.Parallel;
import tornado.api.Read;
import tornado.api.Write;
import tornado.collections.math.TornadoMath;
import tornado.drivers.opencl.OpenCL;
import tornado.runtime.api.TaskGraph;

public class BlackScholes {

    private static final boolean USE_JAVA = false;

    /*
     * For a description of the algorithm and the terms used, please see the
     * documentation for this sample. On invocation of kernel blackScholes, each
     * work thread calculates call price and put price values for given stock
     * price, option strike price, time to expiration date, risk free interest
     * and volatility factor.
     */

    public static void main(final String[] args) {

        final int size = Integer.getInteger("size", 16777216);
        final int iterations = Integer.getInteger("iterations", 300);
        // System.out.println("size =" + size);
        // System.out.println("iterations =" + iterations);

        final BlackScholes bs = new BlackScholes(size);

        // final TornadoCode bsCode =
        // TaskUtils.createCodeAction(BlackScholes.class, "run");

        final TaskGraph tasks = new TaskGraph();

        for (int i = 0; i < iterations; i++)
            tasks.add(BlackScholes::run, bs.randArray, bs.put, bs.call);

        tasks.streamOut(bs.put, bs.call).mapAllTo(OpenCL.defaultDevice());

        long start = 0, end = 0;

        start = System.nanoTime();
        if (USE_JAVA)
            for (int i = 0; i < iterations; i++)
                run(bs.randArray, bs.put, bs.call);
        else
            tasks.schedule().waitOn();
        end = System.nanoTime();

        final double elapsed = (end - start) * 1e-9;
        // final double exec = elapsed - compile;
        System.out.printf("time = %.8f s\n", elapsed);

        tasks.dumpTimes();
        bs.showResults(10);

        // System.out.println(tasks.toSummaryString());
    }

    /**
     * @brief Abromowitz Stegun approxmimation for PHI (Cumulative Normal
     *        Distribution Function)
     * @param X
     *            input value
     * @param phi
     *            pointer to store calculated CND of X
     */
    final static float phi(final float X) {
        final float c1 = 0.319381530f;
        final float c2 = -0.356563782f;
        final float c3 = 1.781477937f;
        final float c4 = -1.821255978f;
        final float c5 = 1.330274429f;

        final float zero = 0.0f;
        final float one = 1.0f;
        final float two = 2.0f;
        final float temp4 = 0.2316419f;

        final float oneBySqrt2pi = 0.398942280f;

        final float absX = Math.abs(X);
        final float t = one / (one + (temp4 * absX));

        final float y = (one - (oneBySqrt2pi * TornadoMath.exp((-X * X) / two)
                * t * (c1 + (t * (c2 + (t * (c3 + (t * (c4 + (t * c5))))))))));

        final float result = (X < zero) ? (one - y) : y;

        return result;
    }

    static final float S_LOWER_LIMIT = 10.0f;

    static final float S_UPPER_LIMIT = 100.0f;

    static final float K_LOWER_LIMIT = 10.0f;

    static final float K_UPPER_LIMIT = 100.0f;

    static final float T_LOWER_LIMIT = 1.0f;

    static final float T_UPPER_LIMIT = 10.0f;

    static final float R_LOWER_LIMIT = 0.01f;

    static final float R_UPPER_LIMIT = 0.05f;

    static final float SIGMA_LOWER_LIMIT = 0.01f;

    static final float SIGMA_UPPER_LIMIT = 0.10f;

    private final float randArray[];

    private final float put[];

    private final float call[];

    public BlackScholes(final int size) {
        randArray = new float[size];
        call = new float[size];
        put = new float[size];

        for (int i = 0; i < size; i++) {
            randArray[i] = (i * 1.0f) / size;
        }
    }

    /*
     * @brief Calculates the call and put prices by using Black Scholes model
     * @param s Array of random values of current option price
     * @param sigma Array of random values sigma
     * @param k Array of random values strike price
     * @param t Array of random values of expiration time
     * @param r Array of random values of risk free interest rate
     * @param width Width of call price or put price array
     * @param call Array of calculated call price values
     * @param put Array of calculated put price values
     */
    public static void run(@Read final float[] randArray,
            @Write final float[] put, @Write final float[] call) {
        for (@Parallel
        int gid = 0; gid < call.length; gid++) {
            final float two = 2.0f;
            final float inRand = randArray[gid];
            final float S = (S_LOWER_LIMIT * inRand)
                    + (S_UPPER_LIMIT * (1.0f - inRand));
            final float K = (K_LOWER_LIMIT * inRand)
                    + (K_UPPER_LIMIT * (1.0f - inRand));
            final float T = (T_LOWER_LIMIT * inRand)
                    + (T_UPPER_LIMIT * (1.0f - inRand));
            final float R = (R_LOWER_LIMIT * inRand)
                    + (R_UPPER_LIMIT * (1.0f - inRand));
            final float sigmaVal = (SIGMA_LOWER_LIMIT * inRand)
                    + (SIGMA_UPPER_LIMIT * (1.0f - inRand));

            final float sigmaSqrtT = sigmaVal * TornadoMath.sqrt(T);

            final float d1 = (TornadoMath.log(S / K) + ((R + ((sigmaVal * sigmaVal) / two)) * T))
                    / sigmaSqrtT;
            final float d2 = d1 - sigmaSqrtT;

            final float KexpMinusRT = K * TornadoMath.exp(-R * T);

            float phiD1 = phi(d1);
            float phiD2 = phi(d2);

            // call[gid] = phi(d1);
            call[gid] = (S * phiD1) - (KexpMinusRT * phiD2);
            // call[gid] = KexpMinusRT * phiD2;
            phiD1 = phi(-d1);
            phiD2 = phi(-d2);

            // put[gid] = phi(d2);
            put[gid] = (KexpMinusRT * phiD2) - (S * phiD1);
        }
    }

    public void showArray(final float ary[], final String name, final int count) {
        String line;
        line = name + ": ";
        for (int i = 0; i < count; i++) {
            if (i > 0)
                line += ", ";
            line += ary[i];
        }
        System.out.println(line);
    }

    public void showResults(final int count) {
        showArray(call, "Call Prices", count);
        showArray(put, "Put  Prices", count);
    }

}