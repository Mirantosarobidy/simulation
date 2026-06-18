package org.simulation.simulation;

 public class GoogleProvider extends Provider {
     public GoogleProvider() {
         this.name = "Google";
         loadFromParams();
     }
     @Override public String getName() { return this.name; }
 }