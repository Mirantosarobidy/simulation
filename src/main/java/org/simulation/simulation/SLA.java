package org.simulation.simulation;

public class SLA {
    public final double tsla;   // seuil temps de réponse (ms)
    public final double csla;   // seuil coût monétaire ($)
    public final double psla;   // seuil popularité
    public final double deltaT; // fenêtre d'observation pour suppression

    public SLA(double tsla, double csla, double psla, double deltaT) {
        this.tsla   = tsla;
        this.csla   = csla;
        this.psla   = psla;
        this.deltaT = deltaT;
    }
}
