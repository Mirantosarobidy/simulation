package org.simulation.simulation;

import java.util.ArrayList;
import java.util.List;

public class Query {
    private final int qid;
    private final String region;
    private final int numberOfQueries;
    private final int requiredFileSize;
    private final List<String> requiredFiles;

    private double avgResponseTime = 0;
    private double avgQueryCost = 0;
    private double avgCostCpu = 0;
    private double avgCostBw = 0;
    private int    countGoogle = 0;
    private int    countAws = 0;
    private int    countAzure = 0;

    private int aws = 0;
    private int azure = 0;
    private int google = 0;

    public void incrementAws() { this.aws++; }
    public void incrementAzure() { this.azure++; }    // Compteurs d'exécution par provider
    public void incrementGoogle() { this.google++; }


    public Query(int qid, String region, int numberOfQueries, List<String> requiredFiles) {
        this.qid = qid;
        this.region = region;
        this.numberOfQueries = numberOfQueries;
        this.requiredFiles = new ArrayList<>(requiredFiles);
        this.requiredFileSize = requiredFiles.size();
    }

    public int getQid() { return qid; }
    public String getRegion() { return region; }
    public int getNumberOfQueries() { return numberOfQueries; }
    public int getRequiredFileSize(){ return requiredFileSize; }
    public List<String> getRequiredFiles() { return requiredFiles; }

    public double getAvgResponseTime() { return avgResponseTime; }
    public double getAvgQueryCost() { return avgQueryCost; }
    public double getAvgCostCpu() { return avgCostCpu; }
    public double getAvgCostBw() { return avgCostBw; }
    public int    getGoogle() { return countGoogle; }
    public int    getAws() { return countAws; }
    public int    getAzure() { return countAzure; }

    // ── Setters métriques ─────────────────────────────
    public void setAvgResponseTime(double v) { this.avgResponseTime = v; }
    public void setAvgQueryCost(double v) { this.avgQueryCost = v; }
    public void setAvgCostCpu(double v) { this.avgCostCpu = v; }
    public void setAvgCostBw(double v) { this.avgCostBw = v; }
    public void setGoogle(int v) { this.countGoogle = v; }
    public void setAws(int v) { this.countAws = v; }
    public void setAzure(int v) { this.countAzure = v; }

    @Override
    public String toString() {
        return String.format("Query[qid=%d, region=%s, nbQ=%d, files=%s]", qid, region, numberOfQueries, requiredFiles);
    }
}
