# API de snippets de SQX 143 (extraída de las fuentes, 23-10-2026)

Fuentes de referencia (solo lectura): `<SQX>\internal\extend\Snippets\SQ\...` (~550 bloques) y `internal\extend\Code\<Plataforma>\...`.
Snippets nuevos: `user\extend\Snippets\SQ\<Tipo>\...`. Plantillas: `user\extend\Code\<Plataforma>\blocks\<Clase>.tpl` (mismo nombre que la clase).
Imports: `com.strategyquant.lib.*`, `com.strategyquant.datalib.*`, `com.strategyquant.tradinglib.*`, `SQ.Internal.*`.

**Compilación**: el núcleo `com.strategyquant.lib` va embebido en el exe, así que **javac externo no compila** (`package com.strategyquant.lib does not exist`, `class file for …ISQCloneable not found`). Tampoco se puede usar jcmd contra la JVM de SQX. Lo que funciona (probado el 23-10-2026) es compilar uno mismo en el **Code Editor web**:
1. Abrir `http://localhost:8080/SQEDITOR/index.html#!/editor` con el tamaño normal del panel (`preset: desktop`, no 1500x950) y hacer una captura.
2. Pulsar **Compile all** en la barra superior.
3. Esperar 13–20 s hasta ver `Snippet(s) compilation successful. 0 error(s)`. Si hay errores, salen en la pestaña Log del editor.
4. Confirmar en el log de SQX: `SnippetsCompiler - Starting to compile Snippets` … `Compiling Snippets done in 13s`.
5. Al final pregunta si recarga la interfaz de SQX. **Eso lo decide el usuario**: sin recargar, los bloques o columnas nuevos no aparecen.
6. **Recargar la interfaz** (solo si el usuario lo pide; probado el 25-10-2026). No hay botón de recarga aparte: se vuelve a pulsar **Compile all** y en el diálogo "Compilation was successful. Do you want to reload StrategyQuant UI…?" se pulsa **Yes** (hacia (658, 401) en una captura de 800x695; **No** está hacia (614, 401)). Tarda unos 20 s y el editor sigue abierto. Comprobarlo en el log de SQX: tiene que salir `CodeEditorServlet - Reloading registered custom classes...` sin excepciones después. Tras recargar, las columnas o bloques nuevos ya se pueden elegir en las vistas y en el Builder (las estrategias ya guardadas siguen mostrando 0 hasta retestearlas).

Ruido conocido del log al compilar, que no indica fallo: `Error deleting autocomplete file …cache.json` (NoSuchFileException) y `Unauthorized request '/codeeditor/list'. Invalid token`.

Las clases quedan en `internal\tmp\compiled\SQ\...`, que se vacía, así que son temporales. SQX recompila `user\extend` al arrancar o con Compile all. Documentación oficial: `<SQX>\Extending_SQX_es.pdf`.

Prefijo usado en esta skill: **HC** (Hobbiecode) en las clases y `hc` en las funciones MQL. Errores vistos antes: "Cannot find block 'X'" significa que no se ha compilado. `NoSuchMethodException … <init>()` significa que las clases auxiliares dentro de Blocks necesitan un constructor vacío.

## Columnas de databank (probadas)

Plantillas en `assets/snippets/Columns/Databanks/`:
- `HCAvgWin` y `HCAvgLoss`: media de las operaciones ganadoras y de las perdedoras. HCAvgLoss sale **negativa**, mientras que la Avg. Loss de SQX la muestra en positivo.
- `HCStdDevWin` y `HCStdDevLoss`: desviación típica **muestral (n−1)**. La `StandardDev` interna usa la poblacional (n).

Están instaladas en `user\extend\Snippets\SQ\Columns\Databanks\`. Las dos medias coinciden al céntimo con Avg. Win y Avg. Loss de SQX en 11 estrategias. Las desviaciones aún no se han contrastado con un cálculo independiente.

Estructura: `extends DatabankColumn`, constructor `super(L.tsq("Nombre"), DatabankColumn.Decimal2PL, ValueTypes.Maximize|Minimize, 0, min, max)` + `setTooltip(...)`. En `compute(...)`, recorrer `ordersList`, saltar `order.isBalanceOrder()` y usar `getPLByStatsType(order, combination)`, `safeDivide`, `round2`. Imports explícitos: `com.strategyquant.lib.L`, `com.strategyquant.lib.SettingsMap` y `com.strategyquant.tradinglib.{DatabankColumn, Order, OrdersList, SQStats, StatsTypeCombination, ValueTypes}`. No necesita `.tpl`. Referencias internas: `internal\extend\Snippets\SQ\Columns\Databanks\AvgWin.java`, `AvgLoss.java`, `StandardDev.java`.

Comprobar una columna nueva en la GUI (ver `gui-web.md`):
1. Ir a Retester → databank → crear una vista **nueva** → "add columns" → buscar "HC" → Add → Save.
2. Las estrategias que ya estaban en el databank muestran $0 en la columna nueva, porque las estadísticas se calculan al generar o retestear. Hay que retestearlas, previo permiso del usuario.
3. Comparar con la columna interna equivalente.

La vista creada el 23-10 es `user\settings\views\databanks\HC Win-Loss.vw`.

## Clases base

| Tipo | Base | Método a sobrescribir |
|---|---|---|
| Indicador | `IndicatorBlock` | `OnInit()`, `OnBarUpdate()` → `Value.set(0,x)` |
| Condición / patrón | `ConditionBlock` | `boolean OnBlockEvaluate()` |
| Valor / precio | `ValueBlock` | `double OnBlockEvaluate(int relativeShift)` |
| Comparación | `ComparisonBlock` (`LeftRightComparisonBlockAbstract`) | `boolean OnEvaluateComparison()` |
| Acción / orden | `ActionBlock` | `void OnAction()` |
| Columna databank | `DatabankColumn` | `double compute(SQStats, StatsTypeCombination, OrdersList, SettingsMap, SQStats L, SQStats S)` |
| Columna trades | `TradelistColumn` | `Object getValue(Order)` |
| Columna WF | `WalkForwardColumn` | `double compute(WalkForwardResult)` |
| MC retest | `MonteCarloRetest` (`ModifySettings`/`ModifyData`) | `modifySettings(rng, settings)` / `modifyData(rng, TickEvent, globalATR)` + `getClone()` |
| MC manipulación | `MonteCarloManipulation` | `modifyTrades(rng, OrdersList)` |
| Money management | `MoneyManagementMethod` | `computeTradeSize(strategy, symbol, orderType, price, sl, tickSize, pointValue, sizeStep)` |
| Método de salida | `ExitMethod` | `setForOrder(ILiveOrder, StrategyBase)` con `order.registerEvent(...)` |
| Opción de trading | `TradingOption` | `boolean OnBarUpdate(StrategyBase)` (false = no opera), `getClone()` |
| Fórmula SL/PT | `FormulaBlock` + `@Formula(formula="SLPT")` | `double evaluateFormula(strategy, symbol, price, direction)` |
| What-If | `WhatIf` | `void filter(OrdersList)` |
| Custom analysis | `CustomAnalysisMethod` | `boolean filterStrategy(project, task, databank, ResultsGroup)` |
| Gráfico de análisis | `TradeAnalysisChart` | `AbstractChart draw(OrdersList, plType, tradePeriod)` |
| Fitness de PortfolioMaster | `PortfolioMasterFitness` | `double compute(ResultsGroup, sampleType)` |

## Anotaciones

- `@BuildingBlock(name, display="X(@Chart@#Period#)[#Shift#]", returnType=ReturnTypes.Number|Price|PriceRange|Boolean|Action)`
- `@Indicator(oscillator, middleValue, min, max, step)`, `@ParameterSet(set="Period=14")` (se puede repetir)
- `@Parameter(name, defaultValue, minValue, maxValue, step, builderMinValue/MaxValue/Step, category, showIfDefault, postfix, defaultChartIndex)`
- `@Output(name, color=Colors.Red) DataSeries Value`
- `@Help`, `@SortOrder`, `@CategoryOrder`, `@OppositeBlock("X")` o `@OppositeBlock(value, oscillator=true, middleValue, field="Level")`
- `@ClassConfig(name, display)` para MC, MM, salidas y WhatIf. Otras: `@ForEngine("*,-MC,-SP,-SA")`, `@IgnoreInBuilder`, `@Editor(type=Editors.Formula|Selection, ...)`

## Indicador y sus condiciones

- La condición obtiene el indicador de la caché generada: `Strategy.Indicators.MiInd(Input, Period)`.
- Después lee `ind.Value.getRounded(Shift)`.
- Nombres de las condiciones: `XRising/XFalling/XHigher/XLower/XCrossUp/XCrossDown/XChangesUp/XChangesDown`, todas en el mismo paquete `SQ.Blocks.Indicators.X`.
- Precios: `Chart.High(shift)`, `Chart.Close.get(1)`, `Chart.getInstrumentInfo().decimals/tickSize`, `SQUtils.round(x,d)`.
- Por convención la barra [0] es la que acaba de abrir y la [1] es la última cerrada.

## Plantillas (FreeMarker)

- **MT5 nativo**: `iRSI(<@printInput block />, <@printParam block "#Period#" />, <@printComputedFromParam block />)`. Genera un handle que se crea en OnInit.
- **MT5 indicador propio**: `iCustom(<@printInput block />, "'SqX'", <@printParam block "#Period#" />)`. Necesita el `.mq5` en `custom_indicators\MetaTrader5\Indicators`.
- **MT5 condición**: `sqGetIndicatorValue(${getIndyShortName(getIndicatorId(block))}, <@printShift block "0" />) > <@printParam block "#Level#" />`.
- **Función propia**: el patrón del usuario es `hcFuncion(...)`, con el código en `Code\MetaTrader5\CustomFunctions\HC_*.mq5`.
- **Macros**: `printInput`, `printParam`, `printShift`, `printComputedFromParam`, `printBlock`, `printBlockChild`, `compress_single_line`. Están en `mt5Blocks.inc`, `mt5Parameters.inc` y `global\globalFunctions.inc`.
- **Cobertura de plataformas**: si un bloque no tiene .tpl para una plataforma, no se puede exportar a ella. Hay que hacer al menos MT5, MT4 y PseudoCode.

### Rachas semanales (25-10-2026)

- `HCMaxConsecWinWeeks` y `HCMaxConsecLossWeeks` (Integer, Maximize/Minimize). Suman `getPLByStatsType` por semana de **cierre** (lunes-domingo, hora de los datos; clave `floorDiv(floorDiv(CloseTime, día) + 3, 7)`), recorren las semanas en orden (TreeMap) y devuelven la racha máxima de semanas con P/L > 0 o < 0.
- Las semanas sin operaciones no cortan la racha (se ignoran); una semana con P/L = 0 sí la corta.
- Probadas en SQX 143 (25-10-2026): las dos columnas dan valores correctos.
- Compiladas el 25-10-2026 con Compile all: 0 errores, 12 s, `.class` en `internal\tmp\compiled\SQ\Columns\Databanks\`.

**Ojo con Compile all:** guarda antes todos los editores abiertos (cambia la fecha de esos `.java` aunque no se hayan tocado). Y en el Code Editor **no usar el viewport emulado 1500x950**: con él los clics por coordenadas caen en otro botón (abrió "Save as…"). Usar `preset: desktop`; el botón Compile all queda hacia (316, 47) en una captura de 800 px de ancho.
