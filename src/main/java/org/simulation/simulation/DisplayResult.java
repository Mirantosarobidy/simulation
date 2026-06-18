package org.simulation.simulation;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.HostEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DisplayResult {
    // ─────────────────────────────────────────────────────────
    // Affichage Datacenters
    // ─────────────────────────────────────────────────────────

    static void printDatacenters(Map<String, Datacenter> dcMap) {
        Log.println("\n╔══════════════════════════════════════════╗");
        Log.println("║              DATACENTERS                 ║");
        Log.println("╚══════════════════════════════════════════╝");
        Log.println(String.format("%-25s │ %6s │ %s",
                "Nom DC", "ID", "Statut"));
        Log.println("─".repeat(50));

        for (Map.Entry<String, Datacenter> e : dcMap.entrySet()) {
            Log.println(String.format("%-25s │ %6d │ ✓ créé",
                    e.getKey(), e.getValue().getId()));
        }
        Log.println("\nTotal : " + dcMap.size() + " datacenters");
    }

    // ─────────────────────────────────────────────────────────
    // Affichage VMs et Relations
    // ─────────────────────────────────────────────────────────

    private static void printVmsAndRelations(List<ExtendedVm> vmList, Map<String, Datacenter> dcMap) {
        Log.println("\n╔══════════════════════════════════════════╗");
        Log.println("║           VMs ET RELATIONS               ║");
        Log.println("╚══════════════════════════════════════════╝");
        Log.println(String.format(
                "%-6s │ %-8s │ %-22s │ %-8s │ %-8s │ %s",
                "VM ID", "Relation",
                "Datacenter", "Provider", "Région", "Taille(MB)"));
        Log.println("─".repeat(90));

        String currentDc = "";
        for (ExtendedVm vm : vmList) {
            // Ligne de séparation entre chaque DC
            if (!vm.getDatacenterName().equals(currentDc)) {
                currentDc = vm.getDatacenterName();
                Log.println("  ── " + currentDc + " ──");
            }
            Datacenter dc = dcMap.get(vm.getDatacenterName());
            Host host = (Host) dc.getHostList().getFirst();

            Log.println(String.format(
                    "%-6d │ %-8s │ %-22s │ %-8s │ %-8s │ %.1f",
                    vm.getId(),
                    vm.getRelationName(),
                    vm.getDatacenterName(),
                    vm.getProvider(),
                    vm.getRegion(),
                    vm.getRelationSizeMB()));

            // Affichage des capacités de la VM
            Log.println(String.format(
                    "    Capacités VM   : MIPS=%d, RAM=%dMB, PEs=%d, BW=%dMbps",
                    (int) vm.getMips(),
                    (int) vm.getRam(),
                    (int) vm.getNumberOfPes(),
                    (int) vm.getBw()));

            // Affichage des capacités du Datacenter (premier hôte)
            Log.println(String.format(
                    "    Capacités DC   : RAM=%dMB, PEs=%d, BW=%dMbps",
                    (int) host.getRam(),
                    (int) host.getNumberOfPes(),
                    (int) host.getBw()));
        }
    }

    // ─────────────────────────────────────────────────────────
    // Résumé global
    // ─────────────────────────────────────────────────────────

    private static void printSummary(List<Provider> providers,
                                     Map<String, Datacenter> dcMap,
                                     List<ExtendedVm> vmList) {
        Log.println("\n╔══════════════════════════════════════════╗");
        Log.println("║               RÉSUMÉ                     ║");
        Log.println("╚══════════════════════════════════════════╝");

        // Compter par provider
        Map<String, Long> countByProvider = new LinkedHashMap<>();
        Map<String, Long> countByRegion = new LinkedHashMap<>();
        Map<String, Long> countByDc = new LinkedHashMap<>();

        for (ExtendedVm vm : vmList) {
            countByProvider.merge(vm.getProvider(), 1L, Long::sum);
            countByRegion.merge(vm.getRegion(), 1L, Long::sum);
            countByDc.merge(vm.getDatacenterName(), 1L, Long::sum);
        }

        Log.println("\nVMs par provider :");
        countByProvider.forEach((k, v) ->
                Log.println("  " + k + " : " + v + " VMs"));

        Log.println("\nVMs par région :");
        countByRegion.forEach((k, v) ->
                Log.println("  " + k + " : " + v + " VMs"));

        Log.println("\nVMs par datacenter :");
        countByDc.forEach((k, v) ->
                Log.println("  " + k + " : " + v + " VMs"));

        Log.println("\n┌─────────────────────────────────────┐");
        Log.println("│ Providers    : " + providers.size()
                + "  (AWS, Azure, Google)     │");
        Log.println("│ Datacenters  : " + dcMap.size()
                + " (3p × 3r × 2dc)           │");
        Log.println("│ VMs          : " + vmList.size()
                + " (18dc × 5vm)              │");
        Log.println("│ Relations    : " + vmList.size()
                + " (1 relation = 1 VM)       │");
        Log.println("└─────────────────────────────────────┘");
    }

    // ─────────────────────────────────────────────────────────
    // Affichage VMs allouées par Datacenter
    // ─────────────────────────────────────────────────────────

    private static void printAllocatedVmsInDatacenters(Map<String, Datacenter> dcMap) {
        Log.println("\n╔══════════════════════════════════════════╗");
        Log.println("║   VMs ALLOUÉES PAR DATACENTER            ║");
        Log.println("╚══════════════════════════════════════════╝");
        for (Map.Entry<String, Datacenter> entry : dcMap.entrySet()) {
            String dcName = entry.getKey();
            Datacenter dc = entry.getValue();
            Log.println("\n── " + dcName + " (ID=" + dc.getId() + ") ──");
            boolean hasVm = false;
            for (HostEntity host : dc.getHostList()) {
                List<? extends Vm> vms = host.getGuestList();
                Log.println("Nombre de VMs allouées dans " + dcName + " (host " + host.getId() + ") : " + vms.size());
                for (Vm vm : vms) {
                    hasVm = true;
                    Log.println(String.format("VM ID=%d, User=%d, MIPS=%d, RAM=%dMB, PEs=%d, BW=%dMbps",
                            vm.getId(), vm.getUserId(), (int) vm.getMips(), (int) vm.getRam(), vm.getNumberOfPes(), (int) vm.getBw()));
                }
            }
            if (!hasVm) {
                Log.println("  Aucun VM alloué.");
            }
        }
    }

    static void printQueryResults(List<Query> queries) {
        Log.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        Log.println("║                                 RÉSULTATS DES QUERIES                                      ║");
        Log.println("╚══════════════════════════════════════════════════════════════════════════════════════════════╝");

        // Récupérer les métriques par cloudlet
        List<CloudletMetrics> allMetrics = QueryAnalyzer.getCloudletMetricsList();

        for (Query q : queries) {
            // ── Résumé de la query ────────────────────────────
            Log.println("\n┌─ Query #" + q.getQid()
                    + " | Région=" + q.getRegion()
                    + " | Répétitions=" + q.getNumberOfQueries()
                    + " | Fichiers=" + q.getRequiredFileSize()
                    + " (" + q.getRequiredFiles() + ")");
            Log.println(String.format(
                    "│ AvgResp=%.2f ms │ AvgCost=$%.6f │ "
                            + "AvgCPU=$%.6f │ AvgBW=$%.6f │ "
                            + "Google=%d │ AWS=%d │ Azure=%d",
                    q.getAvgResponseTime(),
                    q.getAvgQueryCost(),
                    q.getAvgCostCpu(),
                    q.getAvgCostBw(),
                    q.getGoogle(), q.getAws(), q.getAzure()));

            // ── Entête tableau cloudlets ──────────────────────
            Log.println("│");
            Log.println(String.format("│  %-6s │ %-5s │ %-12s │ %-10s │ %-10s │ %-10s │ %-10s │ %-10s │ %-10s │ %-10s │ %-10s",
                    "cId", "qId",
                    "CPU_USAGE", "CPU_TIME",
                    "DELAY_TIME", "RESP_TIME",
                    "START", "END",
                    "CPU_COST", "BW_COST", "TOTAL_COST"));
            Log.println("│  " + "─".repeat(120));

            // ── Une ligne par cloudlet de cette query ─────────
            for (CloudletMetrics m : allMetrics) {
                if (m.queryId != q.getQid()) continue;

                Log.println(String.format(
                        "│  %-6d │ %-5d │ %-12.0f │ %-10.4f │ %-10.4f │ %-10.4f │ %-10.2f │ %-10.2f │ %-10.6f │ %-10.6f │ %-10.6f",
                        m.cloudletId,
                        m.queryId,
                        m.cpuUsage,
                        m.cpuTime,
                        m.delayTime,
                        m.respTime,
                        m.start,
                        m.end,
                        m.cpuCost,
                        m.bwCost,
                        m.totalCost));
            }
            Log.println("└" + "─".repeat(122));
        }
    }

    static void printTCDRMResults(
            TCDRMStrategy tcdrm,
            List<Query> queries,
            List<CloudletMetrics> allMetrics,
            SLA sla) {

        Log.println("\n╔══════════════════════════════════════════╗");
        Log.println("║         RÉSULTATS TCDRM                  ║");
        Log.println("╚══════════════════════════════════════════╝");

        // Stats globales
        Log.println(String.format(
                "Réplicas créés    : %d",
                tcdrm.getTotalReplicasCreated()));
        Log.println(String.format(
                "Réplicas actifs   : %d",
                tcdrm.getActiveReplicaCount()));
        Log.println(String.format(
                "TSLA              : %.2f ms",  sla.tsla));
        Log.println(String.format(
                "CSLA              : $%.6f",    sla.csla));
        Log.println(String.format(
                "PSLA              : %.0f",     sla.psla));

        // Stats par query
        Log.println("\n── Par Query ──");
        Log.println(String.format("%-5s │ %-4s │ %-8s │ %-10s │ %-10s │ %-8s │ %s",
                "QID", "REG", "AvgResp", "AvgCost",
                "SLA_RESP", "SLA_COST", "Réplicas"));
        Log.println("─".repeat(75));

        for (Query q : queries) {
            long slaRespViol = allMetrics.stream()
                    .filter(m -> QueryFactory.getCloudletToQuery()
                            .get(m.cloudletId) != null &&
                            QueryFactory.getCloudletToQuery()
                                    .get(m.cloudletId).getQid() == q.getQid())
                    .filter(m -> m.respTime > sla.tsla)
                    .count();
            long slaCostViol = allMetrics.stream()
                    .filter(m -> QueryFactory.getCloudletToQuery()
                            .get(m.cloudletId) != null &&
                            QueryFactory.getCloudletToQuery()
                                    .get(m.cloudletId).getQid() == q.getQid())
                    .filter(m -> m.totalCost > sla.csla)
                    .count();

            long replicaCount = tcdrm.getReplicas().values().stream()
                    .flatMap(List::stream)
                    .filter(Replica::isActive)
                    .filter(r -> q.getRequiredFiles().contains(
                            r.getRelationName()))
                    .count();

            Log.println(String.format(
                    "%-5d │ %-4s │ %-8.2f │ %-10.6f │ %-10d │ %-8d │ %d",
                    q.getQid(), q.getRegion(),
                    q.getAvgResponseTime(), q.getAvgQueryCost(),
                    slaRespViol, slaCostViol, replicaCount));
        }

        // Détail des réplicas créés
        Log.println("\n── Réplicas actifs ──");
        tcdrm.getReplicas().forEach((fileName, replicaList) -> {
            replicaList.stream()
                    .filter(Replica::isActive)
                    .forEach(r -> Log.println("  " + r
                            + " (créé à t=" + r.getCreationTime() + ")"));
        });
    }

    static void printTCDRMPredResults(
            TCDRMPredStrategy tcdrm,
            List<Query> queries,
            List<CloudletMetrics> allMetrics,
            SLA sla) {

        Log.println("\n╔══════════════════════════════════════════╗");
        Log.println("║         RÉSULTATS TCDRM                  ║");
        Log.println("╚══════════════════════════════════════════╝");

        // Stats globales
        Log.println(String.format(
                "Réplicas créés    : %d",
                tcdrm.getTotalReplicasCreated()));
        Log.println(String.format(
                "Réplicas actifs   : %d",
                tcdrm.getActiveReplicaCount()));
        Log.println(String.format(
                "TSLA              : %.2f ms",  sla.tsla));
        Log.println(String.format(
                "CSLA              : $%.6f",    sla.csla));
        Log.println(String.format(
                "PSLA              : %.0f",     sla.psla));

        // Stats par query
        Log.println("\n── Par Query ──");
        Log.println(String.format("%-5s │ %-4s │ %-8s │ %-10s │ %-10s │ %-8s │ %s",
                "QID", "REG", "AvgResp", "AvgCost",
                "SLA_RESP", "SLA_COST", "Réplicas"));
        Log.println("─".repeat(75));

        for (Query q : queries) {
            long slaRespViol = allMetrics.stream()
                    .filter(m -> QueryFactory.getCloudletToQuery()
                            .get(m.cloudletId) != null &&
                            QueryFactory.getCloudletToQuery()
                                    .get(m.cloudletId).getQid() == q.getQid())
                    .filter(m -> m.respTime > sla.tsla)
                    .count();
            long slaCostViol = allMetrics.stream()
                    .filter(m -> QueryFactory.getCloudletToQuery()
                            .get(m.cloudletId) != null &&
                            QueryFactory.getCloudletToQuery()
                                    .get(m.cloudletId).getQid() == q.getQid())
                    .filter(m -> m.totalCost > sla.csla)
                    .count();

            long replicaCount = tcdrm.getReplicas().values().stream()
                    .flatMap(List::stream)
                    .filter(Replica::isActive)
                    .filter(r -> q.getRequiredFiles().contains(
                            r.getRelationName()))
                    .count();

            Log.println(String.format(
                    "%-5d │ %-4s │ %-8.2f │ %-10.6f │ %-10d │ %-8d │ %d",
                    q.getQid(), q.getRegion(),
                    q.getAvgResponseTime(), q.getAvgQueryCost(),
                    slaRespViol, slaCostViol, replicaCount));
        }

        // Détail des réplicas créés
        Log.println("\n── Réplicas actifs ──");
        tcdrm.getReplicas().forEach((fileName, replicaList) -> {
            replicaList.stream()
                    .filter(Replica::isActive)
                    .forEach(r -> Log.println("  " + r
                            + " (créé à t=" + r.getCreationTime() + ")"));
        });
    }

    // ─────────────────────────────────────────────────────────
    // Affichage des statistiques globales de coût
    // ─────────────────────────────────────────────────────────
    static void printCostStats(List<CloudletMetrics> metrics, List<Query> queries) {
        double cpuCost = 0.0;
        double networkCost = 0.0;
        double storageCost = 0.0;
        double penaltyCost = 0.0;
        int slaViolations = 0;
        double totalRespTime = 0.0;
        long totalInstructions = 0;
        int totalCloudlets = 0;

        for (CloudletMetrics m : metrics) {
            cpuCost += m.cpuCost;
            networkCost += m.bwCost;
            storageCost += m.cpuUsage; // Utilisé comme proxy pour stockage
            totalRespTime += m.respTime;
            totalInstructions += (long) m.cpuUsage;
            totalCloudlets++;
            // SLA violation (exemple: respTime > 1000ms)
            if (m.respTime > 1000.0) slaViolations++;
        }
        // Pénalité (exemple: 500 par violation)
        penaltyCost = slaViolations * 500.0;

        Log.println("\n--------- COST STATS ----------");
        Log.println(String.format("CPU COST: %.12f", cpuCost));
        Log.println(String.format("NETWORK COST: %.12f", networkCost));
        Log.println(String.format("STORAGE COST: %.1f", storageCost));
        Log.println(String.format("PENALTY COST: %.1f", penaltyCost));

        Log.println("Stats for the queries");
        Log.println("qid,region,numberofquery,required_file_size,required_file,avg_response_time,avg_query_cost,avg_cost_cpu,avg_cost_bw,google,aws,azure");
        for (Query q : queries) {
            Log.println(String.format("%d,\"{%s=%d}\",%d,%d,\"%s\",%.8f,%.15f,%.15f,%.15f,%d,%d,%d",
                    q.getQid(), q.getRegion(), q.getNumberOfQueries(), q.getNumberOfQueries(), q.getRequiredFileSize(),
                    q.getRequiredFiles(), q.getAvgResponseTime(), q.getAvgQueryCost(), q.getAvgCostCpu(), q.getAvgCostBw(),
                    q.getGoogle(), q.getAws(), q.getAzure()));
        }
        Log.println(String.format("Average Response Time: %.2f", totalRespTime / Math.max(1, totalCloudlets)));
        Log.println(String.format("Number of SLA violations: %d", slaViolations));
        Log.println(String.format("Million Instructions:%.0f", (double)totalInstructions));
        Log.println("================== CLOUDLET LIST END ===============");
    }

    public static void printCloudletListNoRep(
            List<CloudletMetrics> metrics,
            List<Query> queries) {

        Log.println("\n================== CLOUDLET LIST ===============");
        Log.println(String.format("%-8s\t%-4s\t%-9s\t%-8s\t%-10s\t%-9s\t%-6s\t%-8s\t%-10s\t%-8s\t%-10s",
                "cId", "qId", "CPU_USAGE", "CPU_TIME",
                "DELAY_TIME", "RESP_TIME",
                "START", "END",
                "CPU_COST", "BW_COST", "TOTAL_COST"));

        double sumResp = 0;
        int    violations = 0;
        double totalMI  = 0;

        Map<Integer, List<CloudletMetrics>> grouped = new LinkedHashMap<>();
        for (CloudletMetrics m : metrics) {
            grouped.computeIfAbsent(m.queryId,
                    k -> new ArrayList<>()).add(m);
        }

        // Afficher stats par query
        Log.println("\nStats for the queries");
        Log.println("qid,region,numberofquery,required_file_size," +
                "required_file,avg_response_time,avg_query_cost," +
                "avg_cost_cpu,avg_cost_bw,google,aws,azure");

        for (Query q : queries) {
            List<CloudletMetrics> qMetrics =
                    grouped.getOrDefault(q.getQid(), new ArrayList<>());
            if (qMetrics.isEmpty()) continue;

            double avgResp = qMetrics.stream()
                    .mapToDouble(m -> m.respTime).average().orElse(0);
            double avgCost = qMetrics.stream()
                    .mapToDouble(m -> m.totalCost).average().orElse(0);
            double avgCpu  = qMetrics.stream()
                    .mapToDouble(m -> m.cpuCost).average().orElse(0);
            double avgBw   = qMetrics.stream()
                    .mapToDouble(m -> m.bwCost).average().orElse(0);

            Log.println(String.format(
                    "%d,\"{%s=%d}\",%d,%d,\"%s\",%.2f,%.12f,%.12f,%.12f,%d,%d,%d",
                    q.getQid(),
                    q.getRegion(), qMetrics.size(),
                    qMetrics.size(),
                    q.getRequiredFileSize(),
                    q.getRequiredFiles().toString(),
                    avgResp, avgCost, avgCpu, avgBw,
                    q.getGoogle(), q.getAws(), q.getAzure()));
        }

        // Tableau détaillé
        Log.println("\ncId\tqId\tCPU_USAGE\tCPU_TIME\tDELAY_TIME\t" +
                "RESP_TIME\tSTART\tEND\tCPU_COST\tBW_COST\tTOTAL_COST");

        for (CloudletMetrics m : metrics) {
            sumResp  += m.respTime;
            totalMI  += m.cpuUsage;
            if (m.respTime > 200.0) violations++;

            Log.println(String.format(
                    "#%-6d\t%d\t%-9.0f\t%-8.2f\t%-10.2f\t%-9.2f\t%-6.0f\t%-8.2f\t%-10.6f\t%-8.5f\t%.2f",
                    m.cloudletId, m.queryId,
                    m.cpuUsage, m.cpuTime,
                    m.delayTime, m.respTime,
                    m.start, m.end,
                    m.cpuCost, m.bwCost, m.totalCost));
        }

        Log.println("================== CLOUDLET LIST END ===============");

        double avgResp = metrics.isEmpty() ? 0
                : sumResp / metrics.size();
        Log.println(String.format(
                "Average Response Time: %.2f", avgResp));
        Log.println("Number of SLA violations: " + violations);
        Log.println(String.format(
                "Million Instructions: %.2f", totalMI / 1_000_000.0));
    }
}
