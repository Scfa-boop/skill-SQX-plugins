import java.nio.file.*; import java.util.*;
/** Monte Carlo: baraja el orden de las operaciones y mide DD maximo y racha de perdidas. Uso: MC <trades.csv> <n> */
public class MC { public static void main(String[] a) throws Exception {
  List<String> L = Files.readAllLines(Paths.get(a[0])); double[] r = new double[L.size() - 1];
  for (int i = 1; i < L.size(); i++) r[i - 1] = Double.parseDouble(L.get(i).split(",")[6]);
  int N = Integer.parseInt(a[1]); double[] dd = new double[N]; int[] st = new int[N]; Random rnd = new Random(7);
  for (int k = 0; k < N; k++) { double[] x = r.clone(); for (int i = x.length - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); double t = x[i]; x[i] = x[j]; x[j] = t; }
    double eq = 0, pk = 0, m = 0; int c = 0, mc = 0; for (double v : x) { eq += v; pk = Math.max(pk, eq); m = Math.max(m, pk - eq); if (v < 0) { c++; mc = Math.max(mc, c); } else c = 0; } dd[k] = m; st[k] = mc; }
  Arrays.sort(dd); Arrays.sort(st);
  System.out.printf("operaciones=%d | DD max (R): mediana %.1f, p90 %.1f, p95 %.1f, p99 %.1f | racha perdidas: mediana %d, p90 %d, p95 %d, p99 %d%n", r.length, dd[N/2], dd[N*9/10], dd[N*95/100], dd[N*99/100], st[N/2], st[N*9/10], st[N*95/100], st[N*99/100]);
}}
