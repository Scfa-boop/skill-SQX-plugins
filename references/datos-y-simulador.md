# Datos de SQX y simulador Java

## Formato .dat (lo lee `scripts/SqxDat.java`)

- Big-endian. Empieza con 15 bytes mágicos 0,1,…,14; cada 1000 registros se repite el mágico seguido de un int.
- Cada registro: cabecera de 3 bytes con códigos de 2 bits por campo (T,O,H,L,C,V): ancho (byte/short/int/long) y lógica (0 = prev−v, 1 = prev+v, 2 = absoluto).
- Tiempo en epoch ms (en la zona horaria de los datos, no UTC real); precios = entero / 10^decimales.
- **XAUUSD_M1_dukas: 6 decimales** (M1 y H1). Otros símbolos: probar y comprobar.
- Zona horaria de los datos de SQX: EET con horario de verano de EE. UU. (Nueva York + 7 h). El gráfico OANDA de TradingView iba una hora por detrás.
- Cobertura XAUUSD M1 (oct-2026): 2003-05-05 → 2026-10-21, ~8 M velas.

Comprobación rápida (rango de fechas y velas de unos días entre HHMM y HHMM):
```bash
J=$SQX/j64/bin; cd <carpeta con las clases>
$J/javac -encoding UTF-8 SqxDat.java
$J/java -cp . SqxDat "$SQXW/user/data/History/XAUUSD_M1_dukas/XAUUSD_M1_dukas_M1.dat" 6 2026-10-08,2026-10-09 0 2359
```
**Comprobar siempre que un precio conocido sale bien** (oro ~4.000 en 2026, no 40.000).

## Simulador `scripts/Opt.java` (plantilla para Breakout Pullback EMA)

Copiarlo a `herramientas/` de la carpeta nueva y adaptar la lógica de `sim()` a la estrategia. Compilar y ejecutar:
```bash
$J/javac -encoding UTF-8 SqxDat.java Opt.java
$J/java -Xmx8g -cp . [-Dflags] Opt <modo> <M1.dat> …
```

Construcción: carga M1 desde `-Dwarm`, agrega H1 con `floor(t/3600000)` y guarda el rango M1 de cada vela H1 para resolver dentro de la vela. EMA sembrada en `c[0]`; ATR de Wilder (media simple las 14 primeras, luego RMA). Highest = máximo de High[i−N..i−1] (sin mirar el futuro).

Orden de eventos en la apertura de cada vela H1 (igual que SQX):
1. Cierre por número de velas (`eab`) en la apertura.
2. Caducidad de la orden si `velas ≥ bv`.
3. Nueva orden si no hay posición, no hay orden (o `-Dreplace=true`) y se cumple el setup con `[1]`.

Dentro de la vela (M1 si `m1=true`, si no OHLC de H1, más rápido y conservador):
- Precios bid; la compra se ejecuta al ask: se dispara si `high + SPREAD ≥ nivel`, entrada `max(nivel, open + SPREAD)`.
- **ATR de referencia = ATR[1] al colocar la orden** (como SQX), no el de la vela de entrada.
- Vela de entrada: SL si `low ≤ SL`; TP solo si el cierre ya lo supera (conservador).
- Velas siguientes: primero SL (`min(sl, open)` si hay hueco), luego TP (`max(tp, open)`), luego subir trailing a `high − tr×ATR` una vez `high − entrada ≥ act×ATR`.
- Costes: `SPREAD = 0.35`, `COMM = 0.10` $/oz por operación. 1 lote = 100 oz. **No hay slippage**.

Métricas en R (R = pnl / (sl×ATR)): n, sumR, PF, ddR, win%, avgR, usd1lot. `score = sumR / max(DD, 5)` (−99 si n < 80). `nbScore` = media del score de la combinación y sus vecinas ±1 paso en cada eje → premia mesetas.

### Modos
| Modo | Uso |
|---|---|
| `grid <dat> out.csv` | rejilla completa en H1 (80–144k combinaciones en segundos). Rangos en los arrays `G_*`. Excluye combinaciones sin salida aparte del SL |
| `m1grid <dat> in.csv out.csv topK` | re-simula en M1 los topK por nbScore |
| `bench <dat> in.csv out.csv topK K` | compara con K simulaciones de entradas aleatorias (mismas salidas, mismo nº de operaciones ajustando q por bisección). Ordena por la última columna |
| `detail <dat> N ema bv sl tp tr act eab prefijo` | IS / OOS / OOS2 / total / por año, salidas por motivo y `prefijo_trades.csv` |
| `trace <dat> desde hasta` | log vela a vela (setup, nivel, "caduca en N velas", ejecuciones). Sirve para responder "¿por qué no entró el día X?" |
| `wf <dat> 2023-01 mesesIS mesesOOS prefijo` | walk-forward; escribe `_ventanas.csv` y `_oos_trades.csv` |

Flags: `-Dfrom=AAAA-MM-DD` (inicio IS), `-Dwarm=` (inicio de carga, ≥ 1 año antes; más si hay EMAs largas), `-Dreplace=true|false`, `-Dtrend=<EMA> -Dtslope=<velas>`. Las fronteras IS 2023–24 / OOS 2025 / OOS2 2026 están fijas en el código: cambiarlas para otra estrategia.

Otras herramientas:
- `scripts/MC.java`: `java -cp . MC trades.csv 10000` → mediana, p90, p95, p99 del DD máximo (R) y de la racha de pérdidas (baraja el orden de operaciones; columna 7 = R).
- Curva con riesgo fijo %: awk con `e *= 1 + riesgo*R`.
- Tablas en Git Bash: `sort -t, -kN -gr file.csv | head | column -s, -t`.

## Ajustes para que AlgoWizard coincida

- Datos `XAUUSD_M1_dukas` de la instalación activa, timeframe H1, **precisión M1** (no solo apertura de vela).
- Spread 0,35 $ (35 puntos con tick 0,01), comisión ~0,10 $/oz.
- Tamaño fijo para comparar en R, o 0,5 % de riesgo para la curva en dinero.
- Si hay EMAs largas (p. ej. 1000), empezar los datos un año antes (≥ 1.200 velas de margen).
- Desviación > 10–15 % frente a mis cifras: pedirle la lista de operaciones y comparar la primera que difiera (con `trace`).
