package org.simulation.simulation;

import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;

public class PopularityRegistry {

    private static PopularityRegistry instance;

    private final Map<String, PopularityPredictor> predictors = new HashMap<>();

    private PopularityRegistry() {}

    public static PopularityRegistry getInstance() {
        if (instance == null) instance = new PopularityRegistry();
        return instance;
    }

    public static void reset() { instance = null; }

    public void recordAccess(String id, double simTimestamp) {
        getOrCreate(id).recordAccess(simTimestamp);
    }

    /** Surcharge avec index d'accès discret (pas de timestamp CloudSim).
     *  Chaque appel incrémente d'une unité — accumulation rapide pour P1. */
    public void recordAccessDiscrete(String id) {
        getOrCreate(id).recordAccess(getOrCreate(id).getHistorySize() * 1.0 + 1.0);
    }

    public void recordAccess(String id) {
        recordAccess(id, CloudSim.clock());
    }

    public double predict(String id, double tauMs) {
        return getOrCreate(id).predict(tauMs);
    }

    public double getDynamicPSLA(String id, double tauMs) {
        return getOrCreate(id).getDynamicPSLA();
    }

    public boolean shouldDelete(String id, double tauMs, int deltaT) {
        return getOrCreate(id).shouldDelete(tauMs, deltaT);
    }

    public PopularityPredictor.Method currentMethod(String id) {
        return getOrCreate(id).currentMethod();
    }

    public Map<String, PopularityPredictor> getAllPredictors() {
        return Collections.unmodifiableMap(predictors);
    }

    private PopularityPredictor getOrCreate(String id) {
        return predictors.computeIfAbsent(id, PopularityPredictor::new);
    }
}