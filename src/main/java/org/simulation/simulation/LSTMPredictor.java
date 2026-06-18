package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.util.LinkedList;

public class LSTMPredictor {

    private static LSTMPredictor instance;

    private final LSTMNetwork network;
    private final boolean weightsLoaded;

    private static final int HIDDEN_SIZE  = 64;
    private static final int WINDOW_SIZE  = SimulationParameters.LSTM_WINDOW_SIZE;

    private LSTMPredictor() {
        network = new LSTMNetwork(HIDDEN_SIZE);
        weightsLoaded = ModelWeightLoader.load(network, "weights.json");

        if (weightsLoaded) {
            Log.println("[LSTMPredictor] Modèle chargé");
        } else {
            Log.println("[LSTMPredictor] Poids Xavier — générez weights.json depuis Colab.");
        }
    }

    public static LSTMPredictor getInstance() {
        if (instance == null) instance = new LSTMPredictor();
        return instance;
    }

    /**
     * Prédit popHat(fi, t+τ).
     */
    public double predict(String relationName, LinkedList<Double> history, int tau) {

        if (history.isEmpty()) return 0.0;

        if (history.size() < WINDOW_SIZE) {
            double ema = fallbackEMA(history);
            Log.println(String.format("[LSTMPredictor] %s : hist court → EMA=%.6f", relationName, ema));
            return ema;
        }

        double[] window = history.stream()
                .skip(Math.max(0, history.size() - WINDOW_SIZE))
                .mapToDouble(Double::doubleValue)
                .toArray();

        for (int step = 0; step < tau; step++) {
            double pred = network.forward(window);
            double[] next = new double[WINDOW_SIZE];
            System.arraycopy(window, 1, next, 0, WINDOW_SIZE - 1);
            next[WINDOW_SIZE - 1] = Math.max(0.0, pred);
            window = next;
        }

        double result = Math.max(0.0, window[WINDOW_SIZE - 1]);
        Log.println(String.format("[LSTMPredictor] %s : popHat(τ=%d)=%.6f", relationName, tau, result));
        return result;
    }

    private double fallbackEMA(LinkedList<Double> history) {
        double alpha = 0.3, ema = history.getFirst();
        for (double v : history)
            ema = alpha * v + (1 - alpha) * ema;
        return Math.max(0.0, ema);
    }
}