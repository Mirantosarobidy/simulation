package org.simulation.simulation;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SimulationParameters {

    public static final int NB_REPEAT = 1000; // conforme article (section 4)


    public static final String[] PROVIDERS     = {"Google", "AWS", "Azure"};
    public static final String[] REGIONS       = {"UE", "US", "AS"};
    public static final int      DC_PER_REGION = 2;
    public static final int      VM_PER_DC     = 5;
    public static final int      VM_COUNT      = PROVIDERS.length * REGIONS.length * DC_PER_REGION * VM_PER_DC;

    // ── VM ────────────────────────────────────────────────────
    public static final int    PES_NUMBER  = 1;
    public static final int    VM_RAM      = 1024;
    public static final String VMM         = "Xen";

    // ── Cloudlet ──────────────────────────────────────────────
    public static final long CLOUDLET_LENGTH_MIN   = 1000L;
    public static final int  CLOUDLET_LENGTH_RANGE = 7500;
    public static final long CLOUDLET_FILE_SIZE    = 10L;
    public static final long CLOUDLET_OUTPUT_SIZE  = 10L;
    public static final int  MAX_SUBMIT_DELAY_MS   = 5000;
    public static final int  MIN_SUBMIT_DELAY_MS   = 400;

    public static final double RESP_TIME_SLO_SIMPLE  = 200.0;   // ms — simple queries

    public static final double RESP_TIME_SLO_COMPLEX = 400.0;   // ms — complex queries

    public static final double RESP_TIME_SLO = RESP_TIME_SLO_SIMPLE;

    public static final double COST_SLO_SIMPLE  = 0.015;

    public static final double COST_SLO_COMPLEX = 0.040;

    public static final double COST_SLO = COST_SLO_SIMPLE;

    public static final double POPULARITY_SLO = 200.0;   // nombre d'accès brut

    public static final int DELTA_T = 3;

    public static final double BW_INTRA_DC_MBPS        = 10_000.0;
    public static final double LAT_INTRA_DC_MS         = 1.0;
    public static final double BW_INTRA_REGION_MBPS    = 1_000.0;
    public static final double LAT_INTRA_REGION_MS     = 10.0;
    public static final double BW_INTER_REGION_MBPS    = 100.0;
    public static final double LAT_UE_US_MS            = 40.0;
    public static final double LAT_US_AS_MS            = 50.0;
    public static final double LAT_UE_AS_MS            = 75.0;
    public static final double BW_INTER_PROVIDER_MBPS  = 20.0;
    public static final double LAT_INTER_PROVIDER_MS   = 60.0;
    public static final double BW_BROKER_DC_MBPS       = 100.0;

    public static final int LSTM_TAU            = 3;
    public static final int LSTM_WINDOW_SIZE    = 15;

    public static final Map<String, Double> LAT_BROKER_DC = new HashMap<>() {{
        put("US",  10.0);
        put("UE",  80.0);
        put("AS", 120.0);
    }};

    // ── CPU cost : $/10^6 MI — Table 1 de l'article ──────────
    public static final Map<String, Map<String, Double>> CPU_PRICE_PER_MI =
            new HashMap<>() {{
                put("Google", new HashMap<>() {{
                    put("US", 0.020); put("UE", 0.025); put("AS", 0.027);
                }});
                put("AWS", new HashMap<>() {{
                    put("US", 0.020); put("UE", 0.018); put("AS", 0.020);
                }});
                put("Azure", new HashMap<>() {{
                    put("US", 0.0095); put("UE", 0.0090); put("AS", 0.0080);
                }});
            }};

    public static final Map<String, Map<String, Double>> PER_GB_STORAGE_COST =
            new HashMap<>() {{
                put("Google", new HashMap<>() {{put("US", 0.006); put("UE", 0.006); put("AS", 0.0066);}});
                put("AWS", new HashMap<>() {{put("US", 0.0096); put("UE", 0.008); put("AS", 0.0096);}});
                put("Azure", new HashMap<>() {{put("US", 0.0120); put("UE", 0.0096); put("AS", 0.0090);}});
            }};

    // ── Coûts BW intra-DC ($/GB) — Table 1 ───────────────────
    public static final Map<String, Map<String, Double>> BW_INTRA_DC_COST =
            new HashMap<>() {{
                put("Google", new HashMap<>() {{
                    put("US", 0.0015); put("UE", 0.002); put("AS", 0.004);
                }});
                put("AWS", new HashMap<>() {{
                    put("US", 0.0015); put("UE", 0.002); put("AS", 0.004);
                }});
                put("Azure", new HashMap<>() {{
                    put("US", 0.0015); put("UE", 0.002); put("AS", 0.004);
                }});
            }};

    // ── Coûts BW inter-région ($/GB) — Table 1 ───────────────
    public static final Map<String, Map<String, Double>> BW_INTER_REGION_COST =
            new HashMap<>() {{
                for (String p : new String[]{"Google", "AWS", "Azure"}) {
                    put(p, new HashMap<>() {{
                        put("US", 0.008); put("UE", 0.008); put("AS", 0.008);
                    }});
                }
            }};

    // ── Coûts BW inter-provider ($/GB) — Table 1 ─────────────
    public static final Map<String, Map<String, Double>> BW_INTER_PROVIDER_COST =
            new HashMap<>() {{
                for (String p : new String[]{"Google", "AWS", "Azure"}) {
                    put(p, new HashMap<>() {{
                        put("US", 0.01); put("UE", 0.01); put("AS", 0.01);
                    }});
                }
            }};

    // ── Ressources VM ─────────────────────────────────────────
    public static final Map<String, Map<String, Integer>> MIPS_BY_REGION =
            new HashMap<>() {{
                put("AWS",    new HashMap<>() {{ put("US",4000); put("UE",3600); put("AS",3200); }});
                put("Azure",  new HashMap<>() {{ put("US",3600); put("UE",4400); put("AS",3000); }});
                put("Google", new HashMap<>() {{ put("US",3400); put("UE",3200); put("AS",4200); }});
            }};
    public static final Map<String, Map<String, Integer>> RAM_BY_REGION =
            new HashMap<>() {{
                put("AWS",    new HashMap<>() {{ put("US",4096); put("UE",3072); put("AS",2048); }});
                put("Azure",  new HashMap<>() {{ put("US",3072); put("UE",4096); put("AS",2048); }});
                put("Google", new HashMap<>() {{ put("US",2048); put("UE",2048); put("AS",4096); }});
            }};
    public static final Map<String, Map<String, Integer>> PES_BY_REGION =
            new HashMap<>() {{
                put("AWS",    new HashMap<>() {{ put("US",8); put("UE",6); put("AS",4); }});
                put("Azure",  new HashMap<>() {{ put("US",6); put("UE",8); put("AS",4); }});
                put("Google", new HashMap<>() {{ put("US",4); put("UE",4); put("AS",8); }});
            }};
    public static final Map<String, Map<String, Long>> BW_BY_REGION =
            new HashMap<>() {{
                put("AWS",    new HashMap<>() {{ put("US",10000L); put("UE",8000L); put("AS",6000L); }});
                put("Azure",  new HashMap<>() {{ put("US",8000L);  put("UE",10000L);put("AS",5000L); }});
                put("Google", new HashMap<>() {{ put("US",8000L);  put("UE",7000L); put("AS",10000L);}});
            }};

    // ── Helpers ───────────────────────────────────────────────
    public static long randomCloudletLength(Random rng) {
        return CLOUDLET_LENGTH_MIN + rng.nextInt(CLOUDLET_LENGTH_RANGE);
    }
    public static double randomSubmitDelay(Random rng) {
        return MIN_SUBMIT_DELAY_MS + rng.nextInt(MAX_SUBMIT_DELAY_MS);
    }
    public static String randomRegion(Random rng) {
        return REGIONS[rng.nextInt(REGIONS.length)];
    }

    private SimulationParameters() {}
}