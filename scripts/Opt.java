import java.io.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

/**
 * Optimizador de la estrategia Breakout Pullback EMA (solo largos) sobre XAUUSD H1.
 *
 * Logica (igual que el .sqx):
 *  - En la apertura de cada vela H1, sin posicion ni orden pendiente y con Close[1] < EMA(ema)[1],
 *    se coloca una BUY STOP en Highest(High, N)[1], valida 'bv' velas.
 *  - SL = entrada - sl x ATR(14)[1]; TP = entrada + tp x ATR(14)[1] (0 = sin TP).
 *  - Trailing: distancia tr x ATR, se activa cuando el beneficio llega a act x ATR (0 = sin trailing).
 *  - Exit after bars: cierre a la apertura de la vela 'eab' contada desde la entrada (0 = sin limite).
 * Precios bid; la compra se hace al ask (bid + SPREAD) y se cobra COMM por operacion (en precio).
 *
 * Uso:
 *   Opt grid   <M1.dat> <salida.csv>              rejilla en H1 (rapida, conservadora dentro de la vela)
 *   Opt m1grid <M1.dat> <entrada.csv> <salida.csv> <topK>   re-simula en M1 los topK mejores de la rejilla
 *   Opt detail <M1.dat> <N> <ema> <bv> <sl> <tp> <tr> <act> <eab> <prefijo salida>
 */
public class Opt {

  static final double SPREAD = 0.35, COMM = 0.10, ATR_PERIOD = 14;
  static boolean TRACE = false; static long TR_FROM, TR_TO;
  static final long IS_FROM = LocalDate.parse(System.getProperty("from", "2023-01-01")).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L;
  static final long OOS_FROM = LocalDate.parse("2025-01-01").atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L;
  static final long WARM_FROM = LocalDate.parse(System.getProperty("warm", "2022-01-01")).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L;
  static final long OOS2_FROM = LocalDate.parse("2026-01-01").atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L;
  /** true: cada setup nuevo reemplaza la orden pendiente (nivel, ATR y caducidad nuevos). false: se mantiene la orden vieja. */
  static final boolean REPLACE = Boolean.getBoolean("replace");
  /** filtro de tendencia opcional: solo setups con Close[1] > EMA(TREND)[1] (0 = sin filtro). TSLOPE > 0: ademas EMA(TREND) subiendo respecto a TSLOPE velas antes. */
  static final int TREND = Integer.getInteger("trend", 0), TSLOPE = Integer.getInteger("tslope", 0);
  static double[] TEMA;

  static final int[] G_N = {5, 8, 10, 15, 20, 30, 45, 60};
  static final int[] G_EMA = {3, 5, 10, 20};
  static final int[] G_BV = {20, 40, 60, 90, 120};
  static final double[] G_SL = {1.5, 2.0, 2.5, 3.0, 4.0};
  static final double[] G_TP = {0, 4, 6, 8, 10};
  static final double[] G_TR = {0, 3, 4, 5, 6};
  static final int[] G_EAB = {0, 24, 48, 96};
  static final double ACT = 1.0;

  // H1
  static long[] t; static double[] o, h, l, c, atr; static int n;
  static int[] ms, me; // rango de velas M1 de cada H1
  // M1
  static double[] mo, mh, ml, mc; static long[] mt;
  static int iIS, iOOS;
  static Map<Integer, double[]> EMA = new HashMap<>(), HH = new HashMap<>();

  static final class P {
    int N, ema, bv, eab; double sl, tp, tr, act;
    P(int N, int ema, int bv, double sl, double tp, double tr, double act, int eab) { this.N = N; this.ema = ema; this.bv = bv; this.sl = sl; this.tp = tp; this.tr = tr; this.act = act; this.eab = eab; }
    String csv() { return N + "," + ema + "," + bv + "," + sl + "," + tp + "," + tr + "," + act + "," + eab; }
  }
  static final class Tr { long t0, t1; double entry, exit, risk, pnl, r; String why; int bars; }
  static final class M { int n; double sumR, pf, dd, win, avgR, usd, score; }

  public static void main(String[] a) throws Exception {
    load(a[1]);
    switch (a[0]) {
      case "grid": grid(a[2]); break;
      case "trace": { TRACE = true; TR_FROM = LocalDate.parse(a[2]).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L; TR_TO = LocalDate.parse(a[3]).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L;
        sim(new P(10, 5, 90, 3.0, 8.0, 5.0, 1.0, 48), true); break; }
      case "m1grid": m1grid(a[2], a[3], Integer.parseInt(a[4])); break;
      case "wf": wf(a[2], Integer.parseInt(a[3]), Integer.parseInt(a[4]), a[5]); break;
      case "bench": benchList(a[2], a[3], Integer.parseInt(a[4]), Integer.parseInt(a[5])); break;
      case "detail": {
        P p = new P(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), Double.parseDouble(a[5]), Double.parseDouble(a[6]), Double.parseDouble(a[7]), Double.parseDouble(a[8]), Integer.parseInt(a[9]));
        detail(p, a[10]); break;
      }
    }
  }

  // ---------------------------------------------------------------- datos
  static void load(String path) throws Exception {
    SqxDat d = SqxDat.read(path, 6);
    int s = 0; while (s < d.n && d.t[s] < WARM_FROM) s++;
    int m = d.n - s;
    mt = Arrays.copyOfRange(d.t, s, d.n); mo = Arrays.copyOfRange(d.o, s, d.n); mh = Arrays.copyOfRange(d.h, s, d.n);
    ml = Arrays.copyOfRange(d.l, s, d.n); mc = Arrays.copyOfRange(d.c, s, d.n);
    d = null; System.gc();
    t = new long[m]; o = new double[m]; h = new double[m]; l = new double[m]; c = new double[m]; ms = new int[m]; me = new int[m];
    long cur = Long.MIN_VALUE;
    for (int i = 0; i < m; i++) {
      long b = Math.floorDiv(mt[i], 3600000L) * 3600000L;
      if (b != cur) { cur = b; t[n] = b; o[n] = mo[i]; h[n] = mh[i]; l[n] = ml[i]; c[n] = mc[i]; ms[n] = i; me[n] = i + 1; n++; }
      else { int k = n - 1; h[k] = Math.max(h[k], mh[i]); l[k] = Math.min(l[k], ml[i]); c[k] = mc[i]; me[k] = i + 1; }
    }
    iIS = 0; while (t[iIS] < IS_FROM) iIS++;
    iOOS = iIS; while (t[iOOS] < OOS_FROM) iOOS++;
    atr = new double[n];
    double sum = 0;
    for (int i = 1; i < n; i++) {
      double tr = Math.max(h[i] - l[i], Math.max(Math.abs(h[i] - c[i - 1]), Math.abs(l[i] - c[i - 1])));
      if (i <= ATR_PERIOD) { sum += tr; atr[i] = sum / i; }
      else atr[i] = (atr[i - 1] * (ATR_PERIOD - 1) + tr) / ATR_PERIOD;
    }
    for (int p : G_EMA) EMA.put(p, ema(p));
    if (TREND > 0) TEMA = ema(TREND);
    for (int p : G_N) HH.put(p, hh(p));
    System.err.printf("H1: %d velas, IS desde %s, OOS desde %s, ultima %s%n", n, ts(t[iIS]), ts(t[iOOS]), ts(t[n - 1]));
  }
  static double[] ema(int p) {
    double[] e = new double[n]; double k = 2.0 / (p + 1); e[0] = c[0];
    for (int i = 1; i < n; i++) e[i] = e[i - 1] + k * (c[i] - e[i - 1]);
    return e;
  }
  static double[] hh(int N) { // valor disponible en la apertura de la vela i: max(High[i-N..i-1])
    double[] r = new double[n];
    for (int i = N; i < n; i++) { double mx = -1e18; for (int j = i - N; j < i; j++) mx = Math.max(mx, h[j]); r[i] = mx; }
    return r;
  }
  static String ts(long x) { return LocalDateTime.ofEpochSecond(x / 1000, 0, ZoneOffset.UTC).toString(); }

  // ---------------------------------------------------------------- simulador
  static List<Tr> sim(P p, boolean m1) { return sim(p, m1, null, 0); }
  /** con rnd != null las entradas son aleatorias: en cada vela sin posicion, compra a mercado con probabilidad q. */
  static List<Tr> sim(P p, boolean m1, Random rnd, double q) {
    double[] em = EMA.containsKey(p.ema) ? EMA.get(p.ema) : ema(p.ema);
    double[] hv = HH.containsKey(p.N) ? HH.get(p.N) : hh(p.N);
    List<Tr> out = new ArrayList<>();
    boolean pend = false, pos = false; double lvl = 0; int pBar = 0;
    double entry = 0, sl = 0, tp = 0, risk = 0, A = 0, pA = 0; int eBar = 0; long eT = 0;
    for (int i = iIS; i < n; i++) {
      // 1) cierre por numero de velas, en la apertura
      if (pos && p.eab > 0 && i - eBar >= p.eab) { out.add(close(eT, t[i], entry, o[i], risk, "bars", i - eBar)); pos = false; }
      // 2) caducidad de la orden
      if (pend && i - pBar >= p.bv) pend = false;
      // 3) nueva orden
      if (rnd != null) { if (!pos && rnd.nextDouble() < q) { pend = true; lvl = -1e18; pBar = i; pA = atr[i - 1]; } }
      else if (!pos && (REPLACE || !pend) && c[i - 1] < em[i - 1] && (TREND == 0 || (c[i - 1] > TEMA[i - 1] && (TSLOPE == 0 || TEMA[i - 1] > TEMA[Math.max(0, i - 1 - TSLOPE)])))) { pend = true; lvl = hv[i]; pBar = i; pA = atr[i - 1]; }
      if (TRACE && t[i] >= TR_FROM && t[i] < TR_TO) System.out.printf(Locale.US, "%s  C[1]=%.2f EMA[1]=%.2f %s | HH%d=%.2f ATR=%.2f | %s%n", ts(t[i]), c[i - 1], em[i - 1], c[i - 1] < em[i - 1] ? "BAJO EMA" : "sobre EMA", p.N, hv[i], atr[i - 1], pos ? String.format(Locale.US, "EN POSICION entrada=%.2f SL=%.2f TP=%.2f", entry, sl, tp) : pend ? String.format(Locale.US, "BUY STOP %.2f (colocada %s, caduca en %d velas)", lvl, ts(t[pBar]), p.bv - (i - pBar)) : "sin orden");
      if (!pos && !pend) continue;
      double Ai = atr[i - 1];
      int k0 = m1 ? ms[i] : -1, k1 = m1 ? me[i] : 0;
      for (int k = k0; k < k1; k++) {
        double oo, hi, lo, cl;
        if (m1) { oo = mo[k]; hi = mh[k]; lo = ml[k]; cl = mc[k]; } else { oo = o[i]; hi = h[i]; lo = l[i]; cl = c[i]; k = k1; }
        if (!pos && pend) {
          if (hi + SPREAD < lvl) continue;
          entry = Math.max(lvl, oo + SPREAD); A = pA; risk = p.sl * A; sl = entry - risk;
          tp = p.tp > 0 ? entry + p.tp * A : Double.MAX_VALUE;
          pos = true; pend = false; eBar = i; eT = m1 ? mt[Math.max(k, 0)] : t[i];
          if (TRACE && t[i] >= TR_FROM && t[i] < TR_TO) System.out.printf(Locale.US, "   >>> EJECUTADA %s a %.2f  SL=%.2f TP=%.2f%n", ts(eT), entry, sl, tp);
          // misma barra de la entrada: SL si lo toca (conservador); TP solo si el cierre ya esta por encima
          if (lo <= sl) { out.add(close(eT, eT, entry, sl, risk, "sl", 0)); pos = false; break; }
          if (cl >= tp) { out.add(close(eT, eT, entry, tp, risk, "tp", 0)); pos = false; break; }
          continue;
        }
        if (pos) {
          long tx = m1 ? mt[Math.max(k, 0)] : t[i];
          if (lo <= sl) { out.add(close(eT, tx, entry, Math.min(sl, oo), risk, sl > entry - risk + 1e-9 ? "trail" : "sl", i - eBar)); pos = false; break; }
          if (hi >= tp) { out.add(close(eT, tx, entry, Math.max(tp, oo), risk, "tp", i - eBar)); pos = false; break; }
          if (p.tr > 0 && hi - entry >= p.act * A) { double ns = hi - p.tr * A; if (ns > sl) sl = ns; }
        }
      }
    }
    if (pos) out.add(close(eT, t[n - 1], entry, c[n - 1], risk, "end", n - 1 - eBar));
    return out;
  }
  static Tr close(long t0, long t1, double entry, double exit, double risk, String why, int bars) {
    if (TRACE && t1 >= TR_FROM && t1 < TR_TO) System.out.printf(Locale.US, "   <<< CIERRE %s a %.2f por %s (%.2fR)%n", ts(t1), exit, why, (exit - entry - COMM) / risk);
    Tr x = new Tr(); x.t0 = t0; x.t1 = t1; x.entry = entry; x.exit = exit; x.risk = risk; x.pnl = exit - entry - COMM; x.r = x.pnl / risk; x.why = why; x.bars = bars; return x;
  }

  static M metrics(List<Tr> tr, long from, long to) {
    M m = new M(); double gp = 0, gl = 0, eq = 0, pk = 0; int w = 0;
    for (Tr x : tr) {
      if (x.t0 < from || x.t0 >= to) continue;
      m.n++; m.sumR += x.r; m.usd += x.pnl * 100; if (x.r > 0) { gp += x.r; w++; } else gl -= x.r;
      eq += x.r; pk = Math.max(pk, eq); m.dd = Math.max(m.dd, pk - eq);
    }
    m.pf = gl > 0 ? gp / gl : (gp > 0 ? 99 : 0); m.win = m.n > 0 ? 100.0 * w / m.n : 0; m.avgR = m.n > 0 ? m.sumR / m.n : 0;
    m.score = m.n < 80 ? -99 : m.sumR / Math.max(m.dd, 5);
    return m;
  }
  static String mcsv(M m) { return String.format(Locale.US, "%d,%.2f,%.3f,%.2f,%.1f,%.3f,%.0f,%.3f", m.n, m.sumR, m.pf, m.dd, m.win, m.avgR, m.usd, m.score); }
  static final String MHEAD = "n,sumR,pf,ddR,win,avgR,usd1lot,score";

  // ---------------------------------------------------------------- rejilla H1
  static void grid(String outPath) throws Exception {
    List<P> ps = new ArrayList<>();
    for (int N : G_N) for (int e : G_EMA) for (int bv : G_BV) for (double sl : G_SL) for (double tp : G_TP) for (double tr : G_TR) for (int eab : G_EAB)
      if (tp > 0 || tr > 0 || eab > 0) ps.add(new P(N, e, bv, sl, tp, tr, tr > 0 ? ACT : 0, eab));
    System.err.println("combinaciones: " + ps.size());
    long t0 = System.currentTimeMillis();
    String[] rows = new String[ps.size()];
    double[][][][][][][] sc = new double[G_N.length][G_EMA.length][G_BV.length][G_SL.length][G_TP.length][G_TR.length][G_EAB.length];
    IntStream.range(0, ps.size()).parallel().forEach(idx -> {
      P p = ps.get(idx); List<Tr> tr = sim(p, false);
      M is = metrics(tr, IS_FROM, OOS_FROM), oos = metrics(tr, OOS_FROM, OOS2_FROM), all = metrics(tr, 0, Long.MAX_VALUE);
      rows[idx] = p.csv() + "," + mcsv(is) + "," + mcsv(oos) + "," + mcsv(all) + "," + mcsv(metrics(tr, OOS2_FROM, Long.MAX_VALUE));
      int[] g = gi(p); sc[g[0]][g[1]][g[2]][g[3]][g[4]][g[5]][g[6]] = is.score;
    });
    System.err.printf("rejilla hecha en %.1f s%n", (System.currentTimeMillis() - t0) / 1000.0);
    try (PrintWriter w = new PrintWriter(new FileWriter(outPath))) {
      w.println("N,ema,bv,sl,tp,tr,act,eab," + pre("is_", MHEAD) + "," + pre("oos_", MHEAD) + "," + pre("all_", MHEAD) + "," + pre("oos2_", MHEAD) + ",nbScore");
      for (int idx = 0; idx < ps.size(); idx++) w.println(rows[idx] + String.format(Locale.US, ",%.3f", neigh(sc, gi(ps.get(idx)))));
    }
  }
  static String pre(String p, String h) { return Arrays.stream(h.split(",")).map(x -> p + x).collect(Collectors.joining(",")); }
  static int[] gi(P p) {
    return new int[]{idx(G_N, p.N), idx(G_EMA, p.ema), idx(G_BV, p.bv), idx(G_SL, p.sl), idx(G_TP, p.tp), idx(G_TR, p.tr), idx(G_EAB, p.eab)};
  }
  static int idx(int[] g, int v) { for (int i = 0; i < g.length; i++) if (g[i] == v) return i; return -1; }
  static int idx(double[] g, double v) { for (int i = 0; i < g.length; i++) if (g[i] == v) return i; return -1; }
  /** media de la puntuacion IS de la combinacion y sus vecinas (+-1 paso en cada eje): premia zonas estables, no picos. */
  static double neigh(double[][][][][][][] sc, int[] g) {
    int[] dims = {G_N.length, G_EMA.length, G_BV.length, G_SL.length, G_TP.length, G_TR.length, G_EAB.length};
    double s = get(sc, g); int cnt = 1;
    for (int d = 0; d < 7; d++) for (int st = -1; st <= 1; st += 2) {
      int[] q = g.clone(); q[d] += st; if (q[d] < 0 || q[d] >= dims[d]) continue; s += get(sc, q); cnt++;
    }
    return s / cnt;
  }
  static double get(double[][][][][][][] s, int[] g) { return s[g[0]][g[1]][g[2]][g[3]][g[4]][g[5]][g[6]]; }

  // ---------------------------------------------------------------- re-simulacion M1 de los mejores
  static void m1grid(String in, String outPath, int topK) throws Exception {
    List<String[]> rows = new ArrayList<>(); String head;
    try (BufferedReader r = new BufferedReader(new FileReader(in))) { head = r.readLine(); String s; while ((s = r.readLine()) != null) rows.add(s.split(",")); }
    int nb = head.split(",").length - 1;
    rows.sort((x, y) -> Double.compare(Double.parseDouble(y[nb]), Double.parseDouble(x[nb])));
    List<String[]> top = rows.subList(0, Math.min(topK, rows.size()));
    String[] out = new String[top.size()];
    IntStream.range(0, top.size()).parallel().forEach(i -> {
      String[] x = top.get(i);
      P p = new P(Integer.parseInt(x[0]), Integer.parseInt(x[1]), Integer.parseInt(x[2]), Double.parseDouble(x[3]), Double.parseDouble(x[4]), Double.parseDouble(x[5]), Double.parseDouble(x[6]), Integer.parseInt(x[7]));
      List<Tr> tr = sim(p, true);
      out[i] = p.csv() + "," + x[nb] + "," + mcsv(metrics(tr, IS_FROM, OOS_FROM)) + "," + mcsv(metrics(tr, OOS_FROM, OOS2_FROM)) + "," + mcsv(metrics(tr, 0, Long.MAX_VALUE));
    });
    try (PrintWriter w = new PrintWriter(new FileWriter(outPath))) {
      w.println("N,ema,bv,sl,tp,tr,act,eab,nbScore," + pre("is_", MHEAD) + "," + pre("oos_", MHEAD) + "," + pre("all_", MHEAD));
      for (String s : out) w.println(s);
    }
  }

  // ---------------------------------------------------------------- detalle de una combinacion (M1)
  static void detail(P p, String prefix) throws Exception {
    List<Tr> tr = sim(p, true);
    try (PrintWriter w = new PrintWriter(new FileWriter(prefix + "_trades.csv"))) {
      w.println("open_time,close_time,entry,exit,risk,pnl,R,reason,bars");
      for (Tr x : tr) w.printf(Locale.US, "%s,%s,%.2f,%.2f,%.2f,%.2f,%.3f,%s,%d%n", ts(x.t0), ts(x.t1), x.entry, x.exit, x.risk, x.pnl, x.r, x.why, x.bars);
    }
    System.out.println("periodo," + MHEAD);
    System.out.println("IS 2023-2024," + mcsv(metrics(tr, IS_FROM, OOS_FROM)));
    System.out.println("OOS 2025," + mcsv(metrics(tr, OOS_FROM, OOS2_FROM)));
    System.out.println("OOS2 2026," + mcsv(metrics(tr, OOS2_FROM, Long.MAX_VALUE)));
    System.out.println("TOTAL," + mcsv(metrics(tr, 0, Long.MAX_VALUE)));
    for (int y = LocalDateTime.ofEpochSecond(IS_FROM / 1000, 0, ZoneOffset.UTC).getYear(); y <= 2026; y++) {
      long a = LocalDate.of(y, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L, b = LocalDate.of(y + 1, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L;
      System.out.println(y + "," + mcsv(metrics(tr, a, b)));
    }
    Map<String, Integer> why = new TreeMap<>(); for (Tr x : tr) why.merge(x.why, 1, Integer::sum);
    System.out.println("salidas: " + why);
  }

  // ---------------------------------------------------------------- benchmark contra entradas aleatorias
  /** percentil (0-100) de la estrategia frente a K simulaciones con entradas aleatorias y las mismas salidas. */
  static double[] bench(P p, List<Tr> real, int K, long seed) {
    int target = metrics(real, 0, Long.MAX_VALUE).n;
    double lo = 1e-4, hi = 0.5, q = 0.01;
    for (int it = 0; it < 18; it++) { // ajusta q para tener el mismo numero de operaciones
      q = Math.sqrt(lo * hi); int cnt = 0;
      for (int r = 0; r < 4; r++) cnt += metrics(sim(p, true, new Random(seed + 1000 + r), q), 0, Long.MAX_VALUE).n;
      if (cnt / 4.0 < target) lo = q; else hi = q;
    }
    M rIS = metrics(real, IS_FROM, OOS_FROM), rOOS = metrics(real, OOS_FROM, OOS2_FROM), rOOS2 = metrics(real, OOS2_FROM, Long.MAX_VALUE), rAll = metrics(real, 0, Long.MAX_VALUE);
    int bIS = 0, bOOS = 0, bAll = 0, bOOS2 = 0; double sAll = 0;
    for (int r = 0; r < K; r++) {
      List<Tr> x = sim(p, true, new Random(seed + r), q);
      M a = metrics(x, IS_FROM, OOS_FROM), b = metrics(x, OOS_FROM, OOS2_FROM), b2 = metrics(x, OOS2_FROM, Long.MAX_VALUE), c2 = metrics(x, 0, Long.MAX_VALUE);
      if (rIS.sumR > a.sumR) bIS++; if (rOOS.sumR > b.sumR) bOOS++; if (rAll.sumR > c2.sumR) bAll++; sAll += c2.sumR; if (rOOS2.sumR > b2.sumR) bOOS2++;
    }
    return new double[]{100.0 * bIS / K, 100.0 * bOOS / K, 100.0 * bAll / K, sAll / K, 100.0 * bOOS2 / K};
  }

  static void benchList(String in, String outPath, int topK, int K) throws Exception {
    List<String[]> rows = new ArrayList<>(); String head;
    try (BufferedReader r = new BufferedReader(new FileReader(in))) { head = r.readLine(); String s; while ((s = r.readLine()) != null) rows.add(s.split(",")); }
    int nb = head.split(",").length - 1;
    rows.sort((x, y) -> Double.compare(Double.parseDouble(y[nb]), Double.parseDouble(x[nb])));
    List<String[]> top = rows.subList(0, Math.min(topK, rows.size()));
    String[] out = new String[top.size()];
    IntStream.range(0, top.size()).parallel().forEach(i -> {
      String[] x = top.get(i);
      P p = new P(Integer.parseInt(x[0]), Integer.parseInt(x[1]), Integer.parseInt(x[2]), Double.parseDouble(x[3]), Double.parseDouble(x[4]), Double.parseDouble(x[5]), Double.parseDouble(x[6]), Integer.parseInt(x[7]));
      List<Tr> tr = sim(p, true);
      double[] b = bench(p, tr, K, 12345);
      out[i] = p.csv() + "," + x[nb] + "," + mcsv(metrics(tr, IS_FROM, OOS_FROM)) + "," + mcsv(metrics(tr, OOS_FROM, OOS2_FROM)) + "," + mcsv(metrics(tr, 0, Long.MAX_VALUE))
          + String.format(Locale.US, ",%.1f,%.1f,%.1f,%.2f", b[0], b[1], b[2], b[3]) + "," + mcsv(metrics(tr, OOS2_FROM, Long.MAX_VALUE)) + String.format(Locale.US, ",%.1f", b[4]);
    });
    try (PrintWriter w = new PrintWriter(new FileWriter(outPath))) {
      w.println("N,ema,bv,sl,tp,tr,act,eab,nbScore," + pre("is_", MHEAD) + "," + pre("oos_", MHEAD) + "," + pre("all_", MHEAD) + ",pctIS,pctOOS,pctAll,randAllR," + pre("oos2_", MHEAD) + ",pctOOS2");
      for (String s : out) w.println(s);
    }
  }

  // ---------------------------------------------------------------- walk-forward
  static int ym(long ms) { LocalDateTime d = LocalDateTime.ofEpochSecond(ms / 1000, 0, ZoneOffset.UTC); return d.getYear() * 12 + d.getMonthValue() - 1; }
  static String ymS(int m) { return String.format("%d-%02d", m / 12, m % 12 + 1); }

  /**
   * Walk-forward: optimiza con 'isM' meses y opera los 'oosM' siguientes; primera ventana OOS en 'oosStart' (yyyy-mm).
   * Seleccion por ventana: mejor media de vecindario de la puntuacion IS (sumR / max(DD,5), minimo 40 operaciones).
   */
  static void wf(String oosStart, int isM, int oosM, String prefix) throws Exception {
    List<P> ps = new ArrayList<>();
    for (int N : G_N) for (int e : G_EMA) for (int bv : G_BV) for (double sl : G_SL) for (double tp : G_TP) for (double tr : G_TR) for (int eab : G_EAB)
      if (tp > 0 || tr > 0 || eab > 0) ps.add(new P(N, e, bv, sl, tp, tr, tr > 0 ? ACT : 0, eab));
    int C = ps.size();
    int[][] tm = new int[C][]; float[][] tr = new float[C][];
    long t0 = System.currentTimeMillis();
    IntStream.range(0, C).parallel().forEach(i -> {
      List<Tr> x = sim(ps.get(i), false);
      int[] a = new int[x.size()]; float[] b = new float[x.size()];
      for (int k = 0; k < x.size(); k++) { a[k] = ym(x.get(k).t0); b[k] = (float) x.get(k).r; }
      tm[i] = a; tr[i] = b;
    });
    System.err.printf("%d combinaciones simuladas en %.1f s%n", C, (System.currentTimeMillis() - t0) / 1000.0);
    int first = Integer.parseInt(oosStart.substring(0, 4)) * 12 + Integer.parseInt(oosStart.substring(5, 7)) - 1;
    int last = ym(t[n - 1]);
    List<P> picks = new ArrayList<>(); List<int[]> wins = new ArrayList<>();
    try (PrintWriter w = new PrintWriter(new FileWriter(prefix + "_ventanas.csv"))) {
      w.println("is_desde,is_hasta,oos_desde,oos_hasta,N,ema,bv,sl,tp,tr,act,eab,nbScore,is_n,is_R,is_dd,oosH1_n,oosH1_R");
      for (int o = first; o <= last; o += oosM) {
        final int isA = o - isM, isB = o, oA = o, oB = o + oosM;
        double[][][][][][][] sc = new double[G_N.length][G_EMA.length][G_BV.length][G_SL.length][G_TP.length][G_TR.length][G_EAB.length];
        for (double[][][][][][] a1 : sc) for (double[][][][][] a2 : a1) for (double[][][][] a3 : a2) for (double[][][] a4 : a3) for (double[][] a5 : a4) for (double[] a6 : a5) Arrays.fill(a6, -99);
        double[][] st = new double[C][];
        IntStream.range(0, C).parallel().forEach(i -> {
          int nn = 0; double s = 0, eq = 0, pk = 0, dd = 0, on = 0, os = 0;
          for (int k = 0; k < tm[i].length; k++) {
            int m = tm[i][k];
            if (m >= isA && m < isB) { nn++; s += tr[i][k]; eq += tr[i][k]; pk = Math.max(pk, eq); dd = Math.max(dd, pk - eq); }
            else if (m >= oA && m < oB) { on++; os += tr[i][k]; }
          }
          int[] g = gi(ps.get(i)); sc[g[0]][g[1]][g[2]][g[3]][g[4]][g[5]][g[6]] = nn < 40 ? -99 : s / Math.max(dd, 5);
          st[i] = new double[]{nn, s, dd, on, os};
        });
        int best = -1; double bnb = -1e9;
        for (int i = 0; i < C; i++) { double v = neigh(sc, gi(ps.get(i))); if (v > bnb) { bnb = v; best = i; } }
        P p = ps.get(best); picks.add(p); wins.add(new int[]{oA, oB});
        w.printf(Locale.US, "%s,%s,%s,%s,%s,%.3f,%d,%.2f,%.2f,%d,%.2f%n", ymS(isA), ymS(isB - 1), ymS(oA), ymS(Math.min(oB, last + 1) - 1), p.csv(), bnb,
            (int) st[best][0], st[best][1], st[best][2], (int) st[best][3], st[best][4]);
      }
    }
    // re-simulacion M1 de los tramos OOS con los parametros elegidos en cada ventana
    List<Tr> oos = new ArrayList<>();
    for (int k = 0; k < picks.size(); k++) {
      int[] wn = wins.get(k);
      for (Tr x : sim(picks.get(k), true)) { int m = ym(x.t0); if (m >= wn[0] && m < wn[1]) oos.add(x); }
    }
    try (PrintWriter w = new PrintWriter(new FileWriter(prefix + "_oos_trades.csv"))) {
      w.println("open_time,close_time,entry,exit,risk,pnl,R,reason,bars");
      for (Tr x : oos) w.printf(Locale.US, "%s,%s,%.2f,%.2f,%.2f,%.2f,%.3f,%s,%d%n", ts(x.t0), ts(x.t1), x.entry, x.exit, x.risk, x.pnl, x.r, x.why, x.bars);
    }
    System.out.println("periodo," + MHEAD);
    System.out.println("WF OOS total," + mcsv(metrics(oos, 0, Long.MAX_VALUE)));
    for (int y = first / 12; y <= last / 12; y++) {
      long a = LocalDate.of(y, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L, b = LocalDate.of(y + 1, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L;
      System.out.println("WF " + y + "," + mcsv(metrics(oos, a, b)));
    }
  }
}
