package org.simulation.simulation;

/**
 * Réseau LSTM 2 couches + couche fully-connected.
 *   input_size=1, hidden_size=64, num_layers=2, output_size=1
 */
public class LSTMNetwork {

    private final int        hiddenSize;
    private final LSTMCell   layer1;
    private final LSTMCell   layer2;

    // Couche FC : y = W_fc · h + b_fc
    private double[] W_fc;   // [hiddenSize]
    private double   b_fc;

    public LSTMNetwork(int hiddenSize) {
        this.hiddenSize = hiddenSize;
        this.layer1     = new LSTMCell(1, hiddenSize);
        this.layer2     = new LSTMCell(hiddenSize, hiddenSize);
        this.W_fc       = new double[hiddenSize];
        this.b_fc       = 0.0;

        initXavier(layer1);
        initXavier(layer2);
    }

    public double forward(double[] sequence) {
        double[] h1 = new double[hiddenSize];
        double[] c1 = new double[hiddenSize];
        double[] h2 = new double[hiddenSize];
        double[] c2 = new double[hiddenSize];

        for (double val : sequence) {
            double[] x = { val };

            double[][] out1 = layer1.forward(x, h1, c1);
            h1 = out1[0];
            c1 = out1[1];

            double[][] out2 = layer2.forward(h1, h2, c2);
            h2 = out2[0];
            c2 = out2[1];
        }

        // FC : scalaire = W_fc · h2 + b_fc
        double out = b_fc;
        for (int j = 0; j < hiddenSize; j++)
            out += W_fc[j] * h2[j];

        return Math.max(0.0, out);
    }

    // ── Getters pour chargement des poids ─────────────────────

    public LSTMCell getLayer1()  { return layer1; }
    public LSTMCell getLayer2()  { return layer2; }
    public double[] getWFc()     { return W_fc; }
    public double   getBFc()     { return b_fc; }
    public void     setWFc(double[] w) { this.W_fc = w; }
    public void     setBFc(double b)   { this.b_fc = b; }

    // ── Initialisation Xavier ─────────────────────────────────

    private void initXavier(LSTMCell cell) {
        double scale = Math.sqrt(2.0 / (1 + hiddenSize));
        randomize(cell.getWf(), scale);
        randomize(cell.getWi(), scale);
        randomize(cell.getWg(), scale);
        randomize(cell.getWo(), scale);
    }

    private void randomize(double[][] W, double scale) {
        java.util.Random rng = new java.util.Random(42);
        for (double[] row : W)
            for (int j = 0; j < row.length; j++)
                row[j] = (rng.nextDouble() * 2 - 1) * scale;
    }
}