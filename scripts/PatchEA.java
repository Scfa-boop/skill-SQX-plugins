import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Hace autosuficiente el EA exportado por SQX: integra SqHighest y SqATR dentro del propio .mq5
 * y elimina las dependencias de indicadores externos de StrategyQuant.
 * Uso: PatchEA <origen.mq5> <destino.mq5>
 */
public class PatchEA {
  static String s;

  static void rep(String from, String to) throws Exception {
    int n = s.split(java.util.regex.Pattern.quote(from), -1).length - 1;
    if (n != 1) throw new Exception("esperaba 1 aparicion y hay " + n + " de: " + from);
    s = s.replace(from, to);
  }

  public static void main(String[] a) throws Exception {
    s = new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8);
    String nl = s.contains("\r\n") ? "\r\n" : "\n";

    // 1) cabecera
    rep("//|    Backtested on XAUUSD_M1_dukas / H1, 2018.01.01 - 2026.10.23 " + nl,
        "//|    Backtested on XAUUSD_M1_dukas / H1, 2018.01.01 - 2026.10.23 " + nl
      + "//|" + nl
      + "//|    Version autosuficiente: los indicadores SqHighest y SqATR de" + nl
      + "//|    StrategyQuant estan integrados en este fichero (funciones" + nl
      + "//|    hcHighest y hcATR). No hace falta instalar ningun indicador." + nl);

    // 2) fuera los tester_indicator de SQX (no se necesitan)
    int before = s.length();
    s = s.replaceAll("#property tester_indicator \"Sq[A-Za-z]+\"\\r?\\n", "");
    if (s.length() == before) throw new Exception("no habia tester_indicator");

    // 3) handles: los dos indicadores de SQX ya no se crean con iCustom
    rep("   indicatorHandles[HIGHEST_1] = iCustom(NULL,0, \"SqHighest\", BreakoutPeriod, PRICE_HIGH);",
        "   indicatorHandles[HIGHEST_1] = 254;   // integrado: se calcula en hcHighest()");
    rep("   indicatorHandles[ATR_1] = iCustom(NULL, 0, \"SqATR\", ATRPeriod);",
        "   indicatorHandles[ATR_1] = 254;   // integrado: se calcula en hcATR()");
    rep("#define HIGHEST_1 2     //iCustom(NULL,0, \"SqHighest\", BreakoutPeriod, PRICE_HIGH)",
        "#define HIGHEST_1 2     //Highest(High, BreakoutPeriod) - integrado, ver hcHighest()");
    rep("#define ATR_1 3     //iCustom(NULL, 0, \"SqATR\", ATRPeriod)",
        "#define ATR_1 3     //ATR(ATRPeriod) de Wilder - integrado, ver hcATR()");

    // 4) lectura de valores: los indices integrados se calculan aqui
    rep("   if(shift < 0){" + nl + "      shift = 0;" + nl + "   }" + nl + nl + "   if(indyIndex < 255) {",
        "   if(shift < 0){" + nl + "      shift = 0;" + nl + "   }" + nl + nl
      + "   // indicadores integrados (antes SqHighest y SqATR de StrategyQuant)" + nl
      + "   if(indyIndex == HIGHEST_1 || indyIndex == ATR_1) {" + nl
      + "      ArrayResize(buffer, bars);" + nl
      + "      for(int a=0; a<bars; a++){" + nl
      + "         int curShift = shift + bars - 1 - a;   // mismo orden que CopyBuffer: el mas antiguo primero" + nl
      + "         buffer[a] = (indyIndex == HIGHEST_1) ? hcHighest(BreakoutPeriod, curShift) : hcATR(ATRPeriod, curShift);" + nl
      + "      }" + nl
      + "      if(doRounding){" + nl + "         roundValues(buffer);" + nl + "      }" + nl
      + "      return(true);" + nl
      + "   }" + nl + nl
      + "   if(indyIndex < 255) {");

    // 5) funciones integradas, justo antes de roundValues
    rep("void roundValues(double &buffer[]){",
        "//+------------------------------------------------------------------+" + nl
      + "//| Indicadores integrados                                           |" + nl
      + "//+------------------------------------------------------------------+" + nl + nl
      + "// Maximo de los High de las 'period' velas que terminan en 'shift' (igual que SqHighest con PRICE_HIGH)." + nl
      + "double hcHighest(int period, int shift){" + nl
      + "   if(period <= 0) period = 14;" + nl
      + "   if(Bars(_Symbol, _Period) < shift + period) return 0;" + nl
      + "   int idx = iHighest(_Symbol, _Period, MODE_HIGH, period, shift);" + nl
      + "   if(idx < 0) return 0;" + nl
      + "   return iHigh(_Symbol, _Period, idx);" + nl
      + "}" + nl + nl
      + "// ATR con suavizado de Wilder, la misma formula que SqATR:" + nl
      + "//    ATR[i] = ((k - 1) * ATR[i-1] + TR[i]) / k,  con k = min(i + 1, period)" + nl
      + "// Se calcula sobre las ultimas velas (minimo 600) en vez de todo el historial: el peso de las" + nl
      + "// velas mas antiguas es (13/14)^600, asi que el resultado es el mismo a efectos practicos." + nl
      + "double hcATR(int period, int shift){" + nl
      + "   if(period <= 0) period = 14;" + nl
      + "   static datetime cacheTime = 0;" + nl
      + "   static int cachePeriod = 0;" + nl
      + "   static double cacheValue = 0;" + nl
      + "   datetime t = iTime(_Symbol, _Period, shift);" + nl
      + "   if(t != 0 && t == cacheTime && period == cachePeriod) return cacheValue;" + nl + nl
      + "   int avail = Bars(_Symbol, _Period) - shift;" + nl
      + "   if(avail < 2) return 0;" + nl
      + "   int n = MathMin(avail, MathMax(600, period * 50));" + nl
      + "   double h[], l[], c[];" + nl
      + "   if(CopyHigh(_Symbol, _Period, shift, n, h) != n || CopyLow(_Symbol, _Period, shift, n, l) != n" + nl
      + "      || CopyClose(_Symbol, _Period, shift, n, c) != n) return 0;" + nl + nl
      + "   double atr = h[0] - l[0];   // las copias van de la vela mas antigua [0] a la mas reciente [n-1]" + nl
      + "   for(int i = 1; i < n; i++){" + nl
      + "      double tr = MathMax(MathAbs(l[i] - c[i-1]), MathMax(h[i] - l[i], MathAbs(h[i] - c[i-1])));" + nl
      + "      int k = MathMin(i + 1, period);" + nl
      + "      atr = ((k - 1) * atr + tr) / k;" + nl
      + "   }" + nl + nl
      + "   cacheTime = t;" + nl
      + "   cachePeriod = period;" + nl
      + "   cacheValue = atr;" + nl
      + "   return atr;" + nl
      + "}" + nl + nl
      + "//+------------------------------------------------------------------+" + nl + nl
      + "void roundValues(double &buffer[]){");

    Files.write(Paths.get(a[1]), s.getBytes(StandardCharsets.UTF_8));
    System.out.println("ok");
  }
}
