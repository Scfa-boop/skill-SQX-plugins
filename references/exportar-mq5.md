# Exportar a MQL5 y hacer el EA autosuficiente

Los EA que exporta SQX para MT5 dependen de indicadores propios de SQX a través de `iCustom` ("SqHighest", "SqATR"…),
y declaran unas 42 líneas `#property tester_indicator "Sq…"`. Para regalarlos o distribuirlos con el código abierto,
los indicadores se integran en el propio `.mq5`.

## Cómo encontrar lo que usa de verdad
- Mira los `#define <INDICADOR>_N` y `initIndicators()`: ahí están los handles reales (p. ej. `iCustom(NULL,0,"SqHighest",…)`).
- Los demás `iCustom` del final del fichero (`SessionOpen/High/Low/Close` → SqSessionOHLC, `sqSRPercRank…` → SqSRPercentRank)
  son librería genérica: comprueba con grep que no se llaman (solo aparece su definición).
- El código fuente de los indicadores de SQX está en `<SQX>\custom_indicators\MetaTrader5\Indicators\Sq*.mq5`.

## Equivalencias verificadas
| Indicador SQX | Sustituto | Nota |
|---|---|---|
| SqHighest(N, PRICE_HIGH) | `iHigh(iHighest(MODE_HIGH, N, shift))` | Idéntico |
| SqATR(P) | Recursión de Wilder `((k-1)·ATR + TR)/k`, k = min(i+1, P), sobre ≥ 600 velas | **No** es el `iATR` de MT5 (media simple) |

## Cómo integrarlos
`scripts/PatchEA.java` (Java de SQX): handle = 254 para los índices integrados; en `loadIndicatorValues(uchar…)` intercepta esos
índices y rellena el buffer con `hcHighest()` / `hcATR()`, en el mismo orden que CopyBuffer (el más antiguo primero);
elimina los `tester_indicator`. Si la estrategia usa otros indicadores Sq, añade su cálculo igual.

## Verificación (obligatoria)
1. Compilar: `MetaEditor64.exe /compile:"…" /inc:"<carpeta del terminal MT5>\MQL5" /log:"…"`
   (si el terminal es portable, el include es su carpeta MQL5, no la de %APPDATA%).
2. Backtest en MT5 del original y del integrado con el mismo `.ini` (Strategy Tester por línea de comandos o desde la interfaz). Hay que **preguntar antes de cerrar** el
   terminal si está abierto. Comparar el `final balance` del log del agente y el CSV que escribe el EA
   (`Tester\Agent-127.0.0.1-3000\MQL5\Files\<EA>.csv`, UTF-16) línea a línea.
3. Borrar después las copias de prueba de `MQL5\Experts` y los CSV del agente.

Resultado de referencia (26-10-2026, Breakout Pullback EMA v05, XAUUSD H1 2019–2026, M1 OHLC): original e integrado idénticos,
210 operaciones y 529 líneas iguales en el CSV.
