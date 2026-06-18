package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.util.Map;

public abstract class Provider {

    protected String name;

    // ── Ressources de calcul ──────────────────────────────────
    protected final java.util.Map<String, Integer> mipsByRegion    = new java.util.LinkedHashMap<>();
    protected final java.util.Map<String, Integer> ramByRegion     = new java.util.LinkedHashMap<>();
    protected final java.util.Map<String, Integer> pesByRegion     = new java.util.LinkedHashMap<>();
    protected final java.util.Map<String, Long>    bwByRegion      = new java.util.LinkedHashMap<>();

    // ── Coûts ─────────────────────────────────────────────────
    protected final java.util.Map<String, Double>  cpuCostPerHour      = new java.util.LinkedHashMap<>();
    protected final java.util.Map<String, Double>  ioCostPerGB         = new java.util.LinkedHashMap<>();
    protected final java.util.Map<String, Double>  bwIntraDcCost       = new java.util.LinkedHashMap<>();
    protected final java.util.Map<String, Double>  bwInterRegionCost   = new java.util.LinkedHashMap<>();
    protected final java.util.Map<String, Double>  bwInterProviderCost = new java.util.LinkedHashMap<>();

    // ── Getters ressources ────────────────────────────────────

    public abstract String getName();

    public int    getMips(String region)    { return mipsByRegion.getOrDefault(region, 1000); }
    public int    getRam(String region)     { return ramByRegion.getOrDefault(region, 1024); }
    public int    getPes(String region)     { return pesByRegion.getOrDefault(region, 1); }
    public long   getBw(String region)      { return bwByRegion.getOrDefault(region, 1000L); }

    // ── Getters coûts ─────────────────────────────────────────

    // ── Nouveau getter ─────────────────────────────────────────
    public double getCpuCostPerMI(String region) {
        return SimulationParameters.CPU_PRICE_PER_MI
                .getOrDefault(getName(), Map.of())
                .getOrDefault(region, 0.020);
    }

    public double getIoCostPerGB(String region) {
        return ioCostPerGB.getOrDefault(region, 0.01);
    }

    public double getBwIntraDcCost(String region) {
        return bwIntraDcCost.getOrDefault(region, 0.001);
    }

    public double getBwInterRegionCost(String region) {
        return bwInterRegionCost.getOrDefault(region, 0.008);
    }

    public double getBwInterProviderCost(String region) {
        return bwInterProviderCost.getOrDefault(region, 0.01);
    }

    protected void loadFromParams() {
        String pName = getName();

        for (String region : SimulationParameters.REGIONS) {
            // Ressources
            mipsByRegion.put(region,
                    SimulationParameters.MIPS_BY_REGION
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 1000));
            ramByRegion.put(region,
                    SimulationParameters.RAM_BY_REGION
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 1024));
            pesByRegion.put(region,
                    SimulationParameters.PES_BY_REGION
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 1));
            bwByRegion.put(region,
                    SimulationParameters.BW_BY_REGION
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 1000L));

            // Coûts
            cpuCostPerHour.put(region,
                    SimulationParameters.CPU_PRICE_PER_MI
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 0.02));
            ioCostPerGB.put(region,
                    SimulationParameters.PER_GB_STORAGE_COST
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 0.01));
            bwIntraDcCost.put(region,
                    SimulationParameters.BW_INTRA_DC_COST
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 0.001));
            bwInterRegionCost.put(region,
                    SimulationParameters.BW_INTER_REGION_COST
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 0.008));
            bwInterProviderCost.put(region,
                    SimulationParameters.BW_INTER_PROVIDER_COST
                            .getOrDefault(pName, Map.of()).getOrDefault(region, 0.01));
        }
    }

    // ── Affichage debug ───────────────────────────────────────

    public void printConfig() {
        Log.println("\n── Provider : " + getName() + " ──");
        for (String region : SimulationParameters.REGIONS) {
            Log.println(String.format(
                    "  [%s] MIPS=%d | RAM=%dMB | PEs=%d | BW=%dMbps"
                            + " | CPU=%.4f$/h | IO=%.4f$/GB"
                            + " | BW_intra=%.4f | BW_inter=%.4f | BW_prov=%.4f",
                    region,
                    getMips(region), getRam(region),
                    getPes(region), getBw(region),
                    getCpuCostPerMI(region), getIoCostPerGB(region),
                    getBwIntraDcCost(region),
                    getBwInterRegionCost(region),
                    getBwInterProviderCost(region)));
        }
    }
}