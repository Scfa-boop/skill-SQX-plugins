import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Version 05: la 04 mas filtro de tendencia Close[1] > EMA(TrendEMAPeriod)[1] y EMA(TrendEMAPeriod)[1] > EMA(TrendEMAPeriod)[121]. Uso: Make05 <origen.xml> <destino.xml> */
public class Make05 {
  static final String TID = "BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB";

  static String ema(String ind, String shift) {
    return ind + "<Item key=\"EMA\" name=\"(EMA) Exponential Moving Average\" display=\"EMA(#Period#)[#Shift#]\" mI=\"MovingAverage\" returnType=\"price\" categoryType=\"indicator\">\n"
        + ind + "  <Param key=\"#Chart#\" name=\"Chart\" type=\"data\" controlType=\"dataVar\" defaultValue=\"0\">0</Param>\n"
        + ind + "  <Param key=\"#ComputedFrom#\" name=\"Computed From\" type=\"int\" controlType=\"combo\" values=\"Close=0,Open=1,High=2,Low=3,Median=4,Typical=5,Weighted=6\" defaultValue=\"0\">0</Param>\n"
        + ind + "  <Param key=\"#Period#\" name=\"Period\" type=\"int\" defaultValue=\"14\" genMinValue=\"-1000003\" genMaxValue=\"-1000004\" paramType=\"period\" controlType=\"jspinnerVar\" minValue=\"1\" maxValue=\"10000\" step=\"1\" builderStep=\"1\" variable=\"true\" variableType=\"INT\">" + TID + "</Param>\n"
        + ind + "  <Param key=\"#Shift#\" name=\"Shift\" type=\"int\" defaultValue=\"1\" controlType=\"jspinnerVar\" minValue=\"0\" maxValue=\"1000\" genMinValue=\"-1000001\" genMaxValue=\"-1000002\" paramType=\"shift\" step=\"1\" builderStep=\"1\">" + shift + "</Param>\n"
        + ind + "</Item>\n";
  }
  static String close(String ind) {
    return ind + "<Item key=\"Close\" name=\"(C) Close\" display=\"Close[#Shift#]\" mI=\"Price\" returnType=\"price\" categoryType=\"priceValue\">\n"
        + ind + "  <Param key=\"#Chart#\" name=\"Chart\" type=\"data\" controlType=\"dataVar\" defaultValue=\"0\">0</Param>\n"
        + ind + "  <Param key=\"#Shift#\" name=\"Shift\" type=\"int\" defaultValue=\"1\" controlType=\"jspinnerVar\" minValue=\"0\" maxValue=\"1000\" genMinValue=\"-1000001\" genMaxValue=\"-1000002\" paramType=\"shift\" step=\"1\" builderStep=\"1\" variable=\"false\">1</Param>\n"
        + ind + "</Item>\n";
  }
  static String greater(String ind, String left, String right) {
    return ind + "<Item key=\"IsGreater\" name=\"(&gt;) Is greater\" display=\"#Left# &gt; #Right#\" mI=\"Comparisons\" returnType=\"boolean\" categoryType=\"operators\">\n"
        + ind + "  <Block key=\"#Left#\">\n" + left + ind + "  </Block>\n"
        + ind + "  <Block key=\"#Right#\">\n" + right + ind + "  </Block>\n"
        + ind + "</Item>\n";
  }

  public static void main(String[] a) throws Exception {
    String s = new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8);
    String endMark = "                  </Item>\n                </Block>\n              </Item>\n            </If>";
    int st = s.indexOf("                  <Item key=\"IsLower\"");
    int en = s.indexOf(endMark);
    if (st < 0 || en < 0) throw new Exception("no encuentro el bloque IsLower");
    String isLower = s.substring(st, en + "                  </Item>\n".length());
    // re-indenta el IsLower 4 espacios mas para meterlo dentro de un AND
    String isLowerIn = isLower.replaceAll("(?m)^(?=.)", "    ");
    // SQX usa AND binarios: AND(IsLower, AND(Close > EMA larga, EMA larga subiendo))
    String i8 = "                          "; // indentacion de los items dentro del AND interior
    String g1 = greater(i8, close(i8 + "    "), ema(i8 + "    ", "1"));
    String g2 = greater(i8, ema(i8 + "    ", "1"), ema(i8 + "    ", "121"));
    String and = "                  <Item key=\"AND\">\n"
        + "                    <Block>\n" + isLowerIn + "                    </Block>\n"
        + "                    <Block>\n"
        + "                      <Item key=\"AND\">\n"
        + "                        <Block>\n" + g1 + "                        </Block>\n"
        + "                        <Block>\n" + g2 + "                        </Block>\n"
        + "                      </Item>\n"
        + "                    </Block>\n"
        + "                  </Item>\n";
    s = s.substring(0, st) + and + s.substring(en + "                  </Item>\n".length());
    s = s.replace("<Strategy name=\"Breakout_PullbackEMA_XAU_H1_WF\"", "<Strategy name=\"Breakout_PullbackEMA_XAU_H1_WF_Trend\"");
    s = s.replaceAll("(?s)<Description>.*?</Description>",
        "<Description>Breakout tras retroceso a la EMA, solo largos, XAUUSD H1. Version walk-forward con filtro de tendencia. "
        + "Setup: sin posicion ni orden pendiente, Close[1] &lt; EMA(EMAPeriod)[1], Close[1] &gt; EMA(TrendEMAPeriod)[1] y EMA(TrendEMAPeriod)[1] &gt; EMA(TrendEMAPeriod)[121] "
        + "(EMA larga por encima y subiendo). Orden BUY STOP en Highest(High, BreakoutPeriod)[1], valida BarsValid velas (no se reemplaza). "
        + "SL = SLATRMult x ATR(ATRPeriod), TP = TPATRMult x ATR. Sin trailing y sin cierre por tiempo. "
        + "Valores: BreakoutPeriod 30, EMA 5, TrendEMA 1000, BarsValid 120, SL 2 ATR, TP 10 ATR, ATR 14. Riesgo recomendado 0,5 % por operacion.</Description>");
    String var = "      <variable makeExternal=\"true\">\n        <id>" + TID + "</id>\n        <name>TrendEMAPeriod</name>\n        <type>int</type>\n        <value>1000</value>\n        <paramType></paramType>\n        <makeExternal>true</makeExternal>\n      </variable>\n    </Variables>";
    s = s.replace("    </Variables>", var);
    Files.write(Paths.get(a[1]), s.getBytes(StandardCharsets.UTF_8));
    System.out.println("ok");
  }
}
