package org.simulation.simulation;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Vm;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ExtendedVm extends Vm {

    private final String region;
    private final String provider;
    private final String datacenterName;

    private String relationName;

    private double relationSizeMB = 450.0;

    private List<ExtendedVm> connectedVms = new ArrayList<>();

    private int    nbRequests     = 0;
    private double tFirstRequest  = -1;
    private double tLastRequest   = -1;
    private LinkedList<Double> popularityHistory = new LinkedList<>();

    private boolean replicaActive = true;

    public ExtendedVm(int id, int userId,
                      double mips, int numberOfPes,
                      int ram, long bw, long size,
                      String vmm, CloudletScheduler cloudletScheduler,
                      String region, String provider,
                      String datacenterName) {
        super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
        this.region = region;
        this.provider = provider;
        this.datacenterName = datacenterName;
    }

    public String getRegion()  { return region; }
    public String getProvider() { return provider; }
    public String getDatacenterName() { return datacenterName; }

    public void setRelationName(String relationName) {
        this.relationName = relationName;
    }

    public String getRelationName() {
        return relationName;
    }

    public void setRelationSizeMB(double size) {
        this.relationSizeMB = size;
    }

    public double getRelationSizeMB() {return relationSizeMB;}

    public void setConnectedVms(List<ExtendedVm> connectedVms) {
        this.connectedVms = connectedVms;
    }

    public List<ExtendedVm> getConnectedVms() {
        return connectedVms;
    }

    public void registerRequest(double currentTime) {
        nbRequests++;
        if (tFirstRequest < 0) tFirstRequest = currentTime;
        tLastRequest = currentTime;
    }

    public double getPopularity(double currentTime) {
        if (tFirstRequest < 0) return 0;
        return nbRequests / (currentTime - tFirstRequest + 1);
    }

    public void updatePopularityHistory(double value, int maxPeriods) {
        popularityHistory.add(value);
        if (popularityHistory.size() > maxPeriods)
            popularityHistory.removeFirst();
    }

    public LinkedList<Double> getPopularityHistory() {
        return popularityHistory;
    }

    public void setReplicaActive(boolean active) {
        this.replicaActive = active;
    }

    public boolean isReplicaActive() {
        return replicaActive;
    }


    @Override
    public String toString() {
        return String.format("VM%d[%s | %s | %s | %.0fMB]", getId(), relationName, datacenterName, provider + "_" + region, relationSizeMB);
    }

}