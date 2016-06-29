package tornado.benchmarks.spmv;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;

public class SpmvTornadoDummy extends BenchmarkDriver {

    private final CSRMatrix<float[]> matrix;

    private float[] v, y;

    public SpmvTornadoDummy(int iterations, CSRMatrix<float[]> matrix) {
        super(iterations);
        this.matrix = matrix;
    }

    @Override
    public void setUp() {
        v = new float[matrix.size];
        y = new float[matrix.size];

        Benchmark.populateVector(v);
    }

    @Override
    public void tearDown() {
        v = null;
        y = null;

        super.tearDown();
    }

    @Override
    public void code() {
        LinearAlgebraArrays.spmv(matrix.vals, matrix.cols, matrix.rows, v,
                matrix.size, y);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate() {
        return true;
    }

    public void printSummary() {
        System.out.printf("id=tornado-dummy, elapsed=%f, per iteration=%f\n",
                getElapsed(), getElapsedPerIteration());
    }

}