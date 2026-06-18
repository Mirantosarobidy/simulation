package org.simulation.simulation;

public class LSTMCell {

    private final int inputSize;
    private final int hiddenSize;

    // Poids [hiddenSize × (inputSize + hiddenSize)]
    private final double[][] Wf, Wi, Wg, Wo;
    // Biais [hiddenSize]
    private final double[] bf, bi, bg, bo;

    public LSTMCell(int inputSize, int hiddenSize) {
        this.inputSize  = inputSize;
        this.hiddenSize = hiddenSize;
        int concat = inputSize + hiddenSize;

        Wf = new double[hiddenSize][concat];
        Wi = new double[hiddenSize][concat];
        Wg = new double[hiddenSize][concat];
        Wo = new double[hiddenSize][concat];

        bf = new double[hiddenSize];
        bi = new double[hiddenSize];
        bg = new double[hiddenSize];
        bo = new double[hiddenSize];
    }

    public double[][] forward(double[] x, double[] h, double[] c) {
        // Concatène [h, x]
        double[] hx = concat(h, x);

        double[] f = sigmoid(addBias(matVec(Wf, hx), bf));
        double[] i = sigmoid(addBias(matVec(Wi, hx), bi));
        double[] g = tanh   (addBias(matVec(Wg, hx), bg));
        double[] o = sigmoid(addBias(matVec(Wo, hx), bo));

        double[] c_new = new double[hiddenSize];
        double[] h_new = new double[hiddenSize];

        for (int j = 0; j < hiddenSize; j++) {
            c_new[j] = f[j] * c[j] + i[j] * g[j];
            h_new[j] = o[j] * Math.tanh(c_new[j]);
        }

        return new double[][] { h_new, c_new };
    }

    public double[][] getWf() { return Wf; }
    public double[][] getWi() { return Wi; }
    public double[][] getWg() { return Wg; }
    public double[][] getWo() { return Wo; }
    public double[]   getBf() { return bf; }
    public double[]   getBi() { return bi; }
    public double[]   getBg() { return bg; }
    public double[]   getBo() { return bo; }


    private double[] matVec(double[][] W, double[] v) {
        double[] out = new double[W.length];
        for (int r = 0; r < W.length; r++)
            for (int c = 0; c < v.length; c++)
                out[r] += W[r][c] * v[c];
        return out;
    }

    private double[] addBias(double[] v, double[] b) {
        double[] out = new double[v.length];
        for (int j = 0; j < v.length; j++) out[j] = v[j] + b[j];
        return out;
    }

    private double[] sigmoid(double[] v) {
        double[] out = new double[v.length];
        for (int j = 0; j < v.length; j++)
            out[j] = 1.0 / (1.0 + Math.exp(-v[j]));
        return out;
    }

    private double[] tanh(double[] v) {
        double[] out = new double[v.length];
        for (int j = 0; j < v.length; j++)
            out[j] = Math.tanh(v[j]);
        return out;
    }

    private double[] concat(double[] a, double[] b) {
        double[] out = new double[a.length + b.length];
        System.arraycopy(a, 0, out, 0,        a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}