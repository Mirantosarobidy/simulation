package org.simulation.simulation;

import java.util.*;

public class PopularityTracker {

    private final Map<String, Integer> accessCount = new LinkedHashMap<>();

    private final Map<String, Integer> firstAccessQuery = new LinkedHashMap<>();

    private final Map<String, LinkedList<Double>> history = new LinkedHashMap<>();

    private int queryCounter = 0;

    private static final int MIN_HISTORY_FOR_REGRESSION = 10;
    private static final double STABILITY_THRESHOLD = 0.05;
    private static final double EMA_ALPHA = 0.3;
    private static final int LSTM_WINDOW = 5;

    public void registerAccess(String relationName) {
        queryCounter++;
        accessCount.merge(relationName, 1, Integer::sum);
        firstAccessQuery.putIfAbsent(relationName, queryCounter);
    }

    public double getPopularity(String relationName) {
        Integer count      = accessCount.get(relationName);
        Integer firstQuery = firstAccessQuery.get(relationName);
        if (count == null || firstQuery == null) return 0.0;
        return (double) count / (queryCounter - firstQuery + 1);
    }

    public int getAccessCount(String relationName) {
        return accessCount.getOrDefault(relationName, 0);
    }

    public void snapshotPopularity(String relationName) {
        double pop = getPopularity(relationName);
        history.computeIfAbsent(relationName, k -> new LinkedList<>()).add(pop);
    }

    public boolean isBelowThresholdForDeltaT(
            String relationName, double pslaRaw, int deltaT) {

        LinkedList<Double> hist = history.get(relationName);
        if (hist == null || hist.size() < deltaT) return false;

        double pslaRate = (queryCounter > 0)
                ? pslaRaw / queryCounter
                : pslaRaw;

        int start = hist.size() - deltaT;
        for (int i = start; i < hist.size(); i++) {
            if (hist.get(i) >= pslaRate) return false;
        }
        return true;
    }

    public Map<String, Integer> getAllAccessCounts() { return accessCount; }

    public int getQueryCounter() { return queryCounter; }

    public double predictPopularity(String relationName, int tau) {
        LinkedList<Double> hist = history.get(relationName);

        if (hist == null || hist.isEmpty()) return 0.0;

        if (hist.size() < MIN_HISTORY_FOR_REGRESSION) {
            return predictEMA(hist, tau);
        }

        double[] arr = hist.stream().mapToDouble(Double::doubleValue).toArray();
        double stdev = normalizedStdev(arr);

        if (stdev < STABILITY_THRESHOLD) {
            return predictLinearRegression(arr, tau);
        } else {
            return predictLSTMPyTorch(relationName, arr, tau);
        }
    }

    private double predictEMA(LinkedList<Double> hist, int tau) {
        double ema = hist.getFirst();
        for (double val : hist) {
            ema = EMA_ALPHA * val + (1 - EMA_ALPHA) * ema;
        }
        return Math.max(0.0, ema);
    }

    private double predictLinearRegression(double[] arr, int tau) {
        int n = arr.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX  += i;
            sumY  += arr[i];
            sumXY += (double) i * arr[i];
            sumX2 += (double) i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-12) return sumY / n; // pente nulle

        double a = (n * sumXY - sumX * sumY) / denom;
        double b = (sumY - a * sumX) / n;

        return Math.max(0.0, a * (n - 1 + tau) + b);
    }

    private double predictLSTMPyTorch(String relationName, double[] arr, int tau) {
        LinkedList<Double> histList = new LinkedList<>();
        for (double v : arr) histList.add(v);

        return LSTMPredictor.getInstance().predict(relationName, histList, tau);
    }

    public LinkedList<Double> getHistory(String relationName) {
        return history.getOrDefault(relationName, new LinkedList<>());
    }

    private double normalizedStdev(double[] arr) {
        double mean = Arrays.stream(arr).average().orElse(0);
        if (mean < 1e-12) return 0.0;
        double variance = Arrays.stream(arr)
                .map(v -> (v - mean) * (v - mean))
                .average().orElse(0);
        return Math.sqrt(variance) / mean;   // coefficient de variation
    }

    public PredictionMethod getMethodUsed(String relationName) {
        LinkedList<Double> hist = history.get(relationName);
        if (hist == null || hist.size() < MIN_HISTORY_FOR_REGRESSION)
            return PredictionMethod.EMA;
        double[] arr = hist.stream().mapToDouble(Double::doubleValue).toArray();
        return normalizedStdev(arr) < STABILITY_THRESHOLD
                ? PredictionMethod.REGRESSION
                : PredictionMethod.LSTM;
    }
}