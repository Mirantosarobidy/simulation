package org.simulation.simulation;

public class AWSProvider extends Provider {

    public AWSProvider() {
        this.name = "AWS";
        loadFromParams();
    }

    @Override
    public String getName() { return this.name; }
}