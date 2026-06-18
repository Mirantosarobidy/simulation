package org.simulation.simulation;

public class Replica {
    private final String relationName;
    private final String sourceProvider;
    private final String sourceRegion;
    private final String targetProvider; // provider cible
    private final String targetRegion;
    private final String targetDcName;
    private ExtendedVm replicaVm;    // VM hébergeant le réplica
    private double creationTime;
    private boolean active = true;

    public Replica(String relationName,
                   String sourceProvider, String sourceRegion,
                   String targetProvider, String targetRegion,
                   String targetDcName) {
        this.relationName   = relationName;
        this.sourceProvider = sourceProvider;
        this.sourceRegion   = sourceRegion;
        this.targetProvider = targetProvider;
        this.targetRegion   = targetRegion;
        this.targetDcName   = targetDcName;
    }

    public String getRelationName() { return relationName; }
    public String getTargetProvider() { return targetProvider; }
    public String getTargetRegion() { return targetRegion; }
    public String getTargetDcName() { return targetDcName; }
    public ExtendedVm getReplicaVm() { return replicaVm; }
    public void setReplicaVm(ExtendedVm vm) { this.replicaVm = vm; }
    public double getCreationTime() { return creationTime; }
    public void setCreationTime(double t) { this.creationTime = t; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }

    @Override
    public String toString() {
        return "Replica[" + relationName + " : " + sourceProvider + "/" + sourceRegion + " → " + targetProvider + "/" + targetRegion + "]";
    }
}
