package org.simulation.simulation;

import org.cloudbus.cloudsim.Log;

import java.io.*;
import java.nio.file.*;

public class ModelWeightLoader {

    public static boolean load(LSTMNetwork network, String jsonPath) {
        try {
            String json = Files.readString(Path.of(jsonPath));
            WeightsJson w = parseWeightsJson(json);

            splitAndLoad(network.getLayer1(),
                    w.lstm_l1_weight_ih,
                    w.lstm_l1_weight_hh,
                    w.lstm_l1_bias_ih,
                    w.lstm_l1_bias_hh);

            splitAndLoad(network.getLayer2(),
                    w.lstm_l2_weight_ih,
                    w.lstm_l2_weight_hh,
                    w.lstm_l2_bias_ih,
                    w.lstm_l2_bias_hh);

            network.setWFc(w.fc_weight[0]);
            network.setBFc(w.fc_bias[0]);

            Log.println("[ModelWeightLoader] Poids chargés depuis " + jsonPath + " ✓");
            return true;

        } catch (Exception e) {
            Log.println("[ModelWeightLoader] Erreur : " + e.getMessage() + " — poids Xavier conservés.");
            return false;
        }
    }

    private static void splitAndLoad(LSTMCell cell,
                                     double[][] wIh,
                                     double[][] wHh,
                                     double[]   bIh,
                                     double[]   bHh) {
        int H = cell.getWf().length;  // hiddenSize

        for (int row = 0; row < H; row++) {
            // i → Wi, f → Wf, g → Wg, o → Wo
            cell.getWi()[row] = combineRows(wIh[row],        wHh[row]);
            cell.getWf()[row] = combineRows(wIh[H + row],    wHh[H + row]);
            cell.getWg()[row] = combineRows(wIh[2*H + row],  wHh[2*H + row]);
            cell.getWo()[row] = combineRows(wIh[3*H + row],  wHh[3*H + row]);

            cell.getBi()[row] = bIh[row]       + bHh[row];
            cell.getBf()[row] = bIh[H + row]   + bHh[H + row];
            cell.getBg()[row] = bIh[2*H + row] + bHh[2*H + row];
            cell.getBo()[row] = bIh[3*H + row] + bHh[3*H + row];
        }
    }

    private static double[] combineRows(double[] ih, double[] hh) {
        double[] out = new double[ih.length + hh.length];
        System.arraycopy(ih, 0, out, 0,         ih.length);
        System.arraycopy(hh, 0, out, ih.length, hh.length);
        return out;
    }


    private static WeightsJson parseWeightsJson(String json)
            throws Exception {
        WeightsJson w = new WeightsJson();
        w.lstm_l1_weight_ih = parse2D(json, "lstm_l1_weight_ih");
        w.lstm_l1_weight_hh = parse2D(json, "lstm_l1_weight_hh");
        w.lstm_l1_bias_ih   = parse1D(json, "lstm_l1_bias_ih");
        w.lstm_l1_bias_hh   = parse1D(json, "lstm_l1_bias_hh");
        w.lstm_l2_weight_ih = parse2D(json, "lstm_l2_weight_ih");
        w.lstm_l2_weight_hh = parse2D(json, "lstm_l2_weight_hh");
        w.lstm_l2_bias_ih   = parse1D(json, "lstm_l2_bias_ih");
        w.lstm_l2_bias_hh   = parse1D(json, "lstm_l2_bias_hh");
        w.fc_weight         = parse2D(json, "fc_weight");
        w.fc_bias           = parse1D(json, "fc_bias");
        return w;
    }

    private static double[] parse1D(String json, String key) {
        int start = json.indexOf("\"" + key + "\"");
        start = json.indexOf('[', start) + 1;
        int end = json.indexOf(']', start);
        String[] parts = json.substring(start, end).trim().split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++)
            result[i] = Double.parseDouble(parts[i].trim());
        return result;
    }

    private static double[][] parse2D(String json, String key) {
        int start = json.indexOf("\"" + key + "\"");
        start = json.indexOf('[', start) + 1;
        java.util.List<double[]> rows = new java.util.ArrayList<>();
        int i = start;
        while (i < json.length()) {
            int rowStart = json.indexOf('[', i);
            if (rowStart == -1) break;
            int rowEnd = json.indexOf(']', rowStart);
            if (rowEnd == -1) break;
            String rowStr = json.substring(rowStart + 1, rowEnd).trim();
            if (!rowStr.isEmpty()) {
                String[] parts = rowStr.split(",");
                double[] row = new double[parts.length];
                for (int j = 0; j < parts.length; j++)
                    row[j] = Double.parseDouble(parts[j].trim());
                rows.add(row);
            }
            i = rowEnd + 1;
            if (i < json.length() && json.charAt(i) == ']') break;
        }
        return rows.toArray(new double[0][]);
    }


    private static class WeightsJson {
        double[][] lstm_l1_weight_ih, lstm_l1_weight_hh;
        double[]   lstm_l1_bias_ih,   lstm_l1_bias_hh;
        double[][] lstm_l2_weight_ih, lstm_l2_weight_hh;
        double[]   lstm_l2_bias_ih,   lstm_l2_bias_hh;
        double[][] fc_weight;
        double[]   fc_bias;
    }
}