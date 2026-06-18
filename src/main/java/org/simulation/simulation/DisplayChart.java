package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;
import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

/**
 * DisplayChart — graphiques comparant les 3 stratégies : NoRepLc, TCDRM, TCDRM-Pred.
 * Chaque méthode plotXxx() ouvre une fenêtre Swing.
 */
public class DisplayChart {

    // ── Couleurs des 3 stratégies ────────────────────────────
    private static final Color C_NOREP = new Color(220, 50,  47);   // rouge
    private static final Color C_TCDRM = new Color(38,  139, 210);  // bleu
    private static final Color C_PRED  = new Color(42,  161, 152);  // teal/vert

    // ═══════════════════════════════════════════════════════════
    //  API principale — comparaison 3 stratégies
    // ═══════════════════════════════════════════════════════════

    public static void plotAllComparisons(
            SimulationMetrics norep,
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        Log.println("\n[Chart] Génération des graphiques de comparaison...");

        plotResponseTime(norep, tcdrm, pred);
        plotSmoothedResponseTime(norep, tcdrm, pred);
        plotBwCostPerQuery(norep, tcdrm, pred);
        plotCumulativeBwCost(norep, tcdrm, pred);
        plotCumulativeTotalCost(norep, tcdrm, pred);
        plotTotalCostBar(norep, tcdrm, pred);
        plotSlaViolations(norep, tcdrm, pred);
        plotReplicaFactor(tcdrm, pred);
        plotPredictionMethods(pred);

        Log.println("[Chart] 9 graphiques affichés.");
    }

    // ── Fig. 1 : Temps de réponse par requête ────────────────

    public static void plotResponseTime(
            SimulationMetrics norep,
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        int n = Math.min(norep.respTimes.size(), Math.min(tcdrm.respTimes.size(), pred.respTimes.size()));
        double[] x = IntStream.range(0, n).mapToDouble(i -> i + 1).toArray();

        XYChart chart = baseXYChart(
                "Temps de réponse par requête (NoRepLc vs TCDRM vs TCDRM-Pred)",
                "Numéro de requête", "Temps de réponse (ms)", 950, 520);

        addLine(chart, "NoRepLc",     x, toArray(norep.respTimes, n), C_NOREP);
        addLine(chart, "TCDRM",       x, toArray(tcdrm.respTimes, n), C_TCDRM);
        addLine(chart, "TCDRM-Pred",  x, toArray(pred.respTimes,  n), C_PRED);

        show(chart);
    }

    public static void plotSmoothedResponseTime(
            SimulationMetrics norep,
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        int window = 30;
        int n = Math.min(norep.respTimes.size(), Math.min(tcdrm.respTimes.size(), pred.respTimes.size()));
        if (n < window) {
            Log.println("[Chart] donnees insuffisantes pour lissage (" + n + " < " + window + ")");
            return;
        }

        double[] x      = IntStream.range(window - 1, n).mapToDouble(i -> i + 1).toArray();
        double[] yNorep = movingAverage(norep.respTimes, n, window);
        double[] yTcdrm = movingAverage(tcdrm.respTimes, n, window);
        double[] yPred  = movingAverage(pred.respTimes,  n, window);

        XYChart chart = baseXYChart(
                "Temps de reponse lisse (moyenne mobile " + window + " requetes)",
                "Numero de requete", "Temps de reponse lisse (ms)", 950, 520);

        addLine(chart, "NoRepLc",    x, yNorep, C_NOREP);
        addLine(chart, "TCDRM",      x, yTcdrm, C_TCDRM);
        addLine(chart, "TCDRM-Pred", x, yPred,  C_PRED);

        show(chart);
    }

    // ── Fig. 2 : Coût BW par requête ─────────────────────────

    public static void plotBwCostPerQuery(
            SimulationMetrics norep,
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        int n = Math.min(norep.bwCosts.size(), Math.min(tcdrm.bwCosts.size(), pred.bwCosts.size()));
        double[] x = IntStream.range(0, n).mapToDouble(i -> i + 1).toArray();

        XYChart chart = baseXYChart(
                "Coût BW par requête (NoRepLc vs TCDRM vs TCDRM-Pred)",
                "Numéro de requête", "Coût BW ($)", 950, 520);

        addLine(chart, "NoRepLc",    x, toArray(norep.bwCosts, n), C_NOREP);
        addLine(chart, "TCDRM",      x, toArray(tcdrm.bwCosts, n), C_TCDRM);
        addLine(chart, "TCDRM-Pred", x, toArray(pred.bwCosts,  n), C_PRED);

        show(chart);
    }

    // ── Fig. 3 : Coût BW cumulé ──────────────────────────────

    public static void plotCumulativeBwCost(
            SimulationMetrics norep,
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        int n = Math.min(norep.cumBwCosts.size(), Math.min(tcdrm.cumBwCosts.size(), pred.cumBwCosts.size()));
        double[] x = IntStream.range(0, n).mapToDouble(i -> i + 1).toArray();

        XYChart chart = baseXYChart(
                "Coût BW cumulé — 3 stratégies",
                "Numéro de requête", "Coût BW cumulé ($)", 950, 520);

        addLine(chart, "NoRepLc",    x, toArray(norep.cumBwCosts, n), C_NOREP);
        addLine(chart, "TCDRM",      x, toArray(tcdrm.cumBwCosts, n), C_TCDRM);
        addLine(chart, "TCDRM-Pred", x, toArray(pred.cumBwCosts,  n), C_PRED);

        show(chart);
    }

    // ── Fig. 4 : Coût total cumulé ───────────────────────────

    public static void plotCumulativeTotalCost(
            SimulationMetrics norep,
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        int n = Math.min(norep.cumTotalCosts.size(), Math.min(tcdrm.cumTotalCosts.size(), pred.cumTotalCosts.size()));
        double[] x = IntStream.range(0, n).mapToDouble(i -> i + 1).toArray();

        XYChart chart = baseXYChart(
                "Coût total cumulé — 3 stratégies (amortissement économique)",
                "Numéro de requête", "Coût total cumulé ($)", 950, 520);

        addLine(chart, "NoRepLc",    x, toArray(norep.cumTotalCosts, n), C_NOREP);
        addLine(chart, "TCDRM",      x, toArray(tcdrm.cumTotalCosts, n), C_TCDRM);
        addLine(chart, "TCDRM-Pred", x, toArray(pred.cumTotalCosts,  n), C_PRED);

        show(chart);
    }

    // ── Fig. 5 : Coût total barres empilées ──────────────────

    public static void plotTotalCostBar(
            SimulationMetrics norep,
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        List<String> cats  = List.of("NoRepLc", "TCDRM", "TCDRM-Pred");
        List<Double>  cpu  = List.of(norep.totalCpuCost, tcdrm.totalCpuCost, pred.totalCpuCost);
        List<Double>  bw   = List.of(norep.totalBwCost,  tcdrm.totalBwCost,  pred.totalBwCost);
        List<Double>  sync = List.of(0.0, 0.0, pred.totalSyncCost);

        CategoryChart chart = new CategoryChartBuilder()
                .width(700).height(520)
                .title("Coût total (CPU + BW + Sync) — 3 stratégies")
                .xAxisTitle("Stratégie")
                .yAxisTitle("Coût ($)")
                .build();
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setAvailableSpaceFill(0.4);
        chart.getStyler().setOverlapped(false);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);

        chart.addSeries("CPU",  cats, cpu);
        chart.addSeries("BW",   cats, bw);
        chart.addSeries("Sync", cats, sync);

        new SwingWrapper<>(chart).displayChart();
    }

    // ── Fig. 6 : Violations SLA par période ──────────────────

    public static void plotSlaViolations(
            SimulationMetrics norep,
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        int window = 50;  // fenêtre glissante de 50 requêtes
        double[] x       = buildWindowX(norep.respTimes.size(), window);
        double[] vNorep  = slidingViolations(norep.respTimes, window, norep.p95RespTime * 0.9);
        double[] vTCDRM  = slidingViolations(tcdrm.respTimes, window, tcdrm.p95RespTime * 0.9);
        double[] vPred   = slidingViolations(pred.respTimes,  window, pred.p95RespTime  * 0.9);

        XYChart chart = baseXYChart(
                "Violations SLA latence (fenêtre glissante de " + window + " requêtes)",
                "Numéro de requête", "Violations SLA", 950, 520);

        addLine(chart, "NoRepLc",    x, vNorep, C_NOREP);
        addLine(chart, "TCDRM",      x, vTCDRM, C_TCDRM);
        addLine(chart, "TCDRM-Pred", x, vPred,  C_PRED);

        show(chart);
    }

    // ── Fig. 7 : Facteur de réplication ──────────────────────

    public static void plotReplicaFactor(
            SimulationMetrics tcdrm,
            SimulationMetrics pred) {

        XYChart chart = baseXYChart(
                "Facteur de réplication — TCDRM vs TCDRM-Pred",
                "Période", "Réplicas actifs", 950, 520);

        if (!tcdrm.replicaHistory.isEmpty()) {
            double[] xt = IntStream.range(0, tcdrm.replicaHistory.size()).mapToDouble(i -> (double)i).toArray();
            double[] yt = tcdrm.replicaHistory.stream().mapToDouble(Integer::doubleValue).toArray();
            addLine(chart, "TCDRM",      xt, yt, C_TCDRM);
        }
        if (!pred.replicaHistory.isEmpty()) {
            double[] xp = IntStream.range(0, pred.replicaHistory.size()).mapToDouble(i -> (double)i).toArray();
            double[] yp = pred.replicaHistory.stream().mapToDouble(Integer::doubleValue).toArray();
            addLine(chart, "TCDRM-Pred", xp, yp, C_PRED);
        }

        show(chart);
    }

    // ── Fig. 8 : Méthodes de prédiction (TCDRM-Pred) ────────

    public static void plotPredictionMethods(SimulationMetrics pred) {
        int total = pred.methodEMA + pred.methodRegression + pred.methodLSTM;
        if (total == 0) {
            Log.println("[Chart] Pas de données de méthodes de prédiction.");
            return;
        }

        PieChart chart = new PieChartBuilder()
                .width(600).height(480)
                .title("TCDRM-Pred — Méthodes de prédiction utilisées")
                .build();
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);

        chart.addSeries("EMA",        pred.methodEMA);
        chart.addSeries("Régression", pred.methodRegression);
        chart.addSeries("LSTM",       pred.methodLSTM);

        new SwingWrapper<>(chart).displayChart();
    }

    // ═══════════════════════════════════════════════════════════
    //  API ancienne (compatibilité NoRepLc seul)
    // ═══════════════════════════════════════════════════════════

    public static void plotNoRepResults(List<CloudletMetrics> metrics) {
        List<CloudletMetrics> sorted = metrics.stream()
                .sorted(Comparator.comparingInt(m -> m.cloudletId)).toList();

        double[] ids = sorted.stream().mapToDouble(m -> m.cloudletId).toArray();
        double[] rt  = sorted.stream().mapToDouble(m -> m.respTime).toArray();
        double[] bw  = sorted.stream().mapToDouble(m -> m.bwCost).toArray();

        XYChart chart = baseXYChart("NoRepLc — Temps de réponse",
                "Cloudlet ID", "Temps de réponse (ms)", 900, 500);
        addLine(chart, "NoRepLc", ids, rt, C_NOREP);
        show(chart);

        XYChart chart2 = baseXYChart("NoRepLc — Coût BW",
                "Cloudlet ID", "Coût BW ($)", 900, 500);
        addLine(chart2, "NoRepLc", ids, bw, C_NOREP);
        show(chart2);
    }

    // ── Helpers internes ──────────────────────────────────────

    private static XYChart baseXYChart(String title, String xLabel, String yLabel, int w, int h) {
        XYChart chart = new XYChartBuilder()
                .width(w).height(h)
                .title(title).xAxisTitle(xLabel).yAxisTitle(yLabel)
                .build();
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(new Color(248, 248, 248));
        return chart;
    }

    private static void addLine(XYChart chart, String name, double[] x, double[] y, Color color) {
        XYSeries s = chart.addSeries(name, x, y);
        s.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        s.setLineColor(color);
        s.setMarker(SeriesMarkers.NONE);
        s.setLineWidth(1.5f);
    }

    private static void show(XYChart chart) {
        new SwingWrapper<>(chart).displayChart();
    }

    private static double[] toArray(List<Double> list, int n) {
        double[] arr = new double[n];
        for (int i = 0; i < n; i++) arr[i] = list.get(i);
        return arr;
    }

    private static double[] movingAverage(List<Double> vals, int n, int window) {
        int outLen = n - window + 1;
        double[] out = new double[outLen];
        double sum = 0;
        for (int i = 0; i < window; i++) sum += vals.get(i);
        out[0] = sum / window;
        for (int i = 1; i < outLen; i++) {
            sum += vals.get(i + window - 1) - vals.get(i - 1);
            out[i] = sum / window;
        }
        return out;
    }

    private static double[] buildWindowX(int size, int window) {
        int nb = size / window;
        double[] x = new double[nb];
        for (int i = 0; i < nb; i++) x[i] = (i + 1) * window;
        return x;
    }

    private static double[] slidingViolations(List<Double> vals, int window, double threshold) {
        int nb = vals.size() / window;
        double[] out = new double[nb];
        for (int i = 0; i < nb; i++) {
            int from = i * window, to = Math.min(from + window, vals.size());
            int count = 0;
            for (int j = from; j < to; j++) if (vals.get(j) > threshold) count++;
            out[i] = count;
        }
        return out;
    }
}
