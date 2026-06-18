package org.simulation.simulation;

import java.util.*;

public class PopularityPredictor {

    private static final double STABILITY_THR = 0.3;
    private static final double WINDOW_MS = 20.0;  // 20 accès discrets par fenêtre
    private static final double EMA_ALPHA = 0.3;
    private static final int    LSTM_HIDDEN = 8;
    private static final int    LSTM_LOOKBACK = 5;
    private static final int    LSTM_EPOCHS = 80;
    private static final double LSTM_LR = 0.005;
    private static final double LSTM_CLIP = 1.0;
    private static final int    MIN_HIST_LSTM = 6;
    private static final int    MIN_HIST_REG = 2;

    private static final int    MIN_WINDOWS_BEFORE_PREDICT = 2;  // accès discrets

    public enum Method { EMA, REGRESSION, LSTM }

    private final String dataItemId;
    private final List<Double> windowedSeries = new ArrayList<>();
    private double lastWindowStart = -1.0;
    private int currentCount = 0;

    private final double[][] Wf, Wi, Wg, Wo;
    private final double[] bf, bi, bg, bo;
    private final double[] Wfc;
    private double bfc = 0.0;

    public PopularityPredictor(String dataItemId) {
        this.dataItemId = dataItemId;

        int concat = 1 + LSTM_HIDDEN;
        Wf = randomMatrix(LSTM_HIDDEN, concat, 42);
        Wi = randomMatrix(LSTM_HIDDEN, concat, 43);
        Wg = randomMatrix(LSTM_HIDDEN, concat, 44);
        Wo = randomMatrix(LSTM_HIDDEN, concat, 45);
        bf = new double[LSTM_HIDDEN];
        bi = new double[LSTM_HIDDEN];
        bg = new double[LSTM_HIDDEN];
        bo = new double[LSTM_HIDDEN];
        Wfc = randomVector(LSTM_HIDDEN, 46);
    }

    public void recordAccess(double simTimestamp) {
        if (lastWindowStart < 0) lastWindowStart = simTimestamp;

        if (simTimestamp - lastWindowStart < WINDOW_MS) {
            currentCount++;
        } else {
            windowedSeries.add((double) currentCount);
            currentCount = 1;
            lastWindowStart = simTimestamp;
        }
    }

    public double predict(double tauMs) {
        List<Double> series = getWindowedSeries();
        if (series.size() < MIN_WINDOWS_BEFORE_PREDICT) return 0.0;

        return Math.max(0.0, switch (selectMethod(series)) {
            case EMA -> predictEMA(series);
            case REGRESSION -> predictRegression(series, tauMs);
            default -> predictLSTM(series);
        });
    }

    public double getDynamicPSLA() {
        List<Double> s = getWindowedSeries();
        if (s.size() < MIN_WINDOWS_BEFORE_PREDICT) return Double.MAX_VALUE;

        int n = Math.min(3, s.size());
        double sum = 0;
        for (int i = s.size() - n; i < s.size(); i++) sum += s.get(i);
        return Math.max(1.0, sum / n) + 1.0;
    }

    public Method currentMethod() { return selectMethod(getWindowedSeries()); }

    public boolean shouldDelete(double tauMs, int deltaT) {
        List<Double> series = getWindowedSeries();
        if (series.size() < deltaT) return false;

        double dynamicPsla = getDynamicPSLA();
        if (dynamicPsla == Double.MAX_VALUE) return false;

        int belowCount = 0;
        for (int i = series.size() - deltaT; i < series.size(); i++) {
            if (series.get(i) < dynamicPsla * 0.5) belowCount++;
        }
        return belowCount >= deltaT;
    }

    public List<Double> getWindowedSeries() {
        List<Double> s = new ArrayList<>(windowedSeries);
        if (currentCount > 0) s.add((double) currentCount);
        return s;
    }

    public String getDataItemId() { return dataItemId; }
    public int getHistorySize(){ return windowedSeries.size(); }

    private Method selectMethod(List<Double> series) {
        if (series.size() < MIN_HIST_REG)  return Method.EMA;
        if (coefficientOfVariation(series) < STABILITY_THR) return Method.REGRESSION;
        if (series.size() < MIN_HIST_LSTM) return Method.EMA;
        return Method.LSTM;
    }

    private double predictEMA(List<Double> series) {
        if (series.isEmpty()) return 0.0;
        double ema = series.get(0);
        for (int i = 1; i < series.size(); i++)
            ema = EMA_ALPHA * series.get(i) + (1 - EMA_ALPHA) * ema;
        return ema;
    }

    private double predictRegression(List<Double> series, double tauMs) {
        int n = series.size();
        if (n < 2) return series.isEmpty() ? 0 : series.get(0);
        double sx=0,sy=0,sxx=0,sxy=0;
        for (int i=0;i<n;i++){
            sx+=i; sy+=series.get(i);
            sxx+=(double)i*i; sxy+=(double)i*series.get(i);
        }
        double d = n*sxx - sx*sx;
        if (Math.abs(d) < 1e-9) return sy/n;
        double a = (n*sxy - sx*sy)/d;
        double b = (sy - a*sx)/n;
        return a*(n - 1 + tauMs/WINDOW_MS) + b;
    }

    private double predictLSTM(List<Double> series) {
        int n = series.size();
        if (n <= LSTM_LOOKBACK) return predictEMA(series);

        double minV = series.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double maxV = series.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double range = (maxV - minV) < 1e-9 ? 1.0 : maxV - minV;

        double[] norm = new double[n];
        for (int i=0;i<n;i++) norm[i] = (series.get(i) - minV)/range;

        trainLSTM(norm);
        double pred = forwardLSTM(Arrays.copyOfRange(norm, n - LSTM_LOOKBACK, n));
        return Math.max(0.0, pred) * range + minV;
    }

    private void trainLSTM(double[] data) {
        int n = data.length;
        for (int epoch=0;epoch<LSTM_EPOCHS;epoch++) {
            double loss=0;
            for (int t=LSTM_LOOKBACK;t<n;t++) {
                double[] x = Arrays.copyOfRange(data, t-LSTM_LOOKBACK, t);
                double yT  = data[t];

                double[] h=new double[LSTM_HIDDEN], c=new double[LSTM_HIDDEN];
                double[][] hs=new double[LSTM_LOOKBACK+1][LSTM_HIDDEN];
                double[][] cs=new double[LSTM_LOOKBACK+1][LSTM_HIDDEN];
                double[][][] fs=new double[LSTM_LOOKBACK][LSTM_HIDDEN][1];
                double[][][] is_=new double[LSTM_LOOKBACK][LSTM_HIDDEN][1];
                double[][][] gs=new double[LSTM_LOOKBACK][LSTM_HIDDEN][1];
                double[][][] os=new double[LSTM_LOOKBACK][LSTM_HIDDEN][1];

                for (int s=0;s<LSTM_LOOKBACK;s++) {
                    double[] hx=concat(new double[]{x[s]},h);
                    double[] f=sigmoid(add(matVec(Wf,hx),bf));
                    double[] i2=sigmoid(add(matVec(Wi,hx),bi));
                    double[] g=tanh_(add(matVec(Wg,hx),bg));
                    double[] o=sigmoid(add(matVec(Wo,hx),bo));
                    double[] cn=new double[LSTM_HIDDEN], hn=new double[LSTM_HIDDEN];
                    for (int j=0;j<LSTM_HIDDEN;j++){
                        cn[j]=f[j]*c[j]+i2[j]*g[j];
                        hn[j]=o[j]*Math.tanh(cn[j]);
                    }
                    hs[s+1]=hn; cs[s+1]=cn;
                    for (int j=0;j<LSTM_HIDDEN;j++){
                        fs[s][j][0]=f[j]; is_[s][j][0]=i2[j];
                        gs[s][j][0]=g[j]; os[s][j][0]=o[j];
                    }
                    h=hn; c=cn;
                }

                double yHat=bfc;
                for (int j=0;j<LSTM_HIDDEN;j++) yHat+=Wfc[j]*h[j];
                double err=yHat-yT; loss+=err*err;

                double dOut=2*err;
                double[] dH=new double[LSTM_HIDDEN];
                for (int j=0;j<LSTM_HIDDEN;j++){
                    Wfc[j]-=LSTM_LR*clip(dOut*h[j]);
                    dH[j]=clip(dOut*Wfc[j]);
                }
                bfc-=LSTM_LR*clip(dOut);

                double[] dC=new double[LSTM_HIDDEN];
                for (int s=LSTM_LOOKBACK-1;s>=0;s--) {
                    double[] hx=concat(new double[]{x[s]},hs[s]);
                    for (int j=0;j<LSTM_HIDDEN;j++){
                        double tc=Math.tanh(cs[s+1][j]);
                        double dO=clip(dH[j]*tc*os[s][j][0]*(1-os[s][j][0]));
                        double dCj=clip(dH[j]*os[s][j][0]*(1-tc*tc)+dC[j]);
                        double dF=clip(dCj*cs[s][j]*fs[s][j][0]*(1-fs[s][j][0]));
                        double dI=clip(dCj*gs[s][j][0]*is_[s][j][0]*(1-is_[s][j][0]));
                        double dG=clip(dCj*is_[s][j][0]*(1-gs[s][j][0]*gs[s][j][0]));
                        dC[j]=clip(dCj*fs[s][j][0]);
                        for (int k=0;k<hx.length;k++){
                            Wf[j][k]-=LSTM_LR*dF*hx[k];
                            Wi[j][k]-=LSTM_LR*dI*hx[k];
                            Wg[j][k]-=LSTM_LR*dG*hx[k];
                            Wo[j][k]-=LSTM_LR*dO*hx[k];
                        }
                        bf[j]-=LSTM_LR*dF; bi[j]-=LSTM_LR*dI;
                        bg[j]-=LSTM_LR*dG; bo[j]-=LSTM_LR*dO;
                    }
                }
            }
            if (loss/(n-LSTM_LOOKBACK) < 1e-5) break;
        }
    }

    private double forwardLSTM(double[] seq) {
        double[] h=new double[LSTM_HIDDEN], c=new double[LSTM_HIDDEN];
        for (double xv : seq) {
            double[] hx=concat(new double[]{xv},h);
            double[] f=sigmoid(add(matVec(Wf,hx),bf));
            double[] i2=sigmoid(add(matVec(Wi,hx),bi));
            double[] g=tanh_(add(matVec(Wg,hx),bg));
            double[] o=sigmoid(add(matVec(Wo,hx),bo));
            double[] cn=new double[LSTM_HIDDEN], hn=new double[LSTM_HIDDEN];
            for (int j=0;j<LSTM_HIDDEN;j++){
                cn[j]=f[j]*c[j]+i2[j]*g[j];
                hn[j]=o[j]*Math.tanh(cn[j]);
            }
            h=hn; c=cn;
        }
        double out=bfc;
        for (int j=0;j<LSTM_HIDDEN;j++) out+=Wfc[j]*h[j];
        return out;
    }
    private double[] matVec(double[][] W,double[] v){
        double[] r=new double[W.length];
        for(int i=0;i<W.length;i++) for(int k=0;k<v.length;k++) r[i]+=W[i][k]*v[k];
        return r;
    }
    private double[] add(double[] a,double[] b){
        double[] r=new double[a.length];
        for(int i=0;i<a.length;i++) r[i]=a[i]+b[i]; return r;
    }
    private double[] concat(double[] a,double[] b){
        double[] r=new double[a.length+b.length];
        System.arraycopy(a,0,r,0,a.length);
        System.arraycopy(b,0,r,a.length,b.length); return r;
    }
    private double[] sigmoid(double[] v){
        double[] r=new double[v.length];
        for(int i=0;i<v.length;i++) r[i]=1.0/(1.0+Math.exp(-Math.max(-20,Math.min(20,v[i]))));
        return r;
    }
    private double[] tanh_(double[] v){
        double[] r=new double[v.length];
        for(int i=0;i<v.length;i++) r[i]=Math.tanh(v[i]); return r;
    }
    private double clip(double g){ return Math.max(-LSTM_CLIP,Math.min(LSTM_CLIP,g)); }

    private double[][] randomMatrix(int rows,int cols,long seed){
        Random rng=new Random(seed);
        double scale=Math.sqrt(2.0/(cols+rows));
        double[][] m=new double[rows][cols];
        for(double[] row:m) for(int j=0;j<cols;j++) row[j]=(rng.nextDouble()*2-1)*scale;
        return m;
    }
    private double[] randomVector(int size,long seed){
        Random rng=new Random(seed);
        double[] v=new double[size];
        for(int i=0;i<size;i++) v[i]=(rng.nextDouble()*2-1)*0.1; return v;
    }
    private static double mean(List<Double> s){
        return s.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
    private static double stddev(List<Double> s){
        double m=mean(s);
        return Math.sqrt(s.stream().mapToDouble(v->(v-m)*(v-m)).average().orElse(0));
    }
    private static double coefficientOfVariation(List<Double> s){
        double m=mean(s); return m<1e-9?0:stddev(s)/m;
    }

    @Override
    public String toString(){
        return String.format("PopularityPredictor[%s|hist=%d|method=%s|psla=%.2f]",
                dataItemId, windowedSeries.size(), currentMethod(), getDynamicPSLA());
    }
}