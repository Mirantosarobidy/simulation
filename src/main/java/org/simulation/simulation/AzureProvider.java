package org.simulation.simulation;

 public class AzureProvider extends Provider {
     public AzureProvider() {
         this.name = "Azure";
         loadFromParams();
     }
     @Override public String getName() { return this.name; }
 }