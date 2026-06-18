package org.simulation.simulation;

public class CloudletMetrics {
    public final int cloudletId;
    public final int queryId;
    public double cpuUsage;
    public double cpuTime;
    public double delayTime;
    public double respTime;
    public double start;
    public double end;
    public double cpuCost;
    public double bwCost;
    public double totalCost;

    public double replicaCost = 0.0;
    
    public CloudletMetrics(int cloudletId, int queryId) {
        this.cloudletId = cloudletId;
        this.queryId    = queryId;
    }
}