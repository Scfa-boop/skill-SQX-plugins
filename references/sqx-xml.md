# Formato .sqx y sintaxis XML verificada

## Contenido del zip

```
META-INF/MANIFEST.MF     exactamente "Manifest-Version: 1.0\r\n\r\n" (25 bytes)
version.txt              "1" sin salto de línea
strategy_Portfolio.xml   UTF-8 sin BOM
```

Los ejemplos oficiales solo traen MANIFEST.MF y el XML; los del usuario (y los nuestros) también `version.txt`. Funciona así.

Crear los auxiliares en PowerShell:
```powershell
[IO.File]::WriteAllText("$src\META-INF\MANIFEST.MF", "Manifest-Version: 1.0`r`n`r`n")
Set-Content "$src\version.txt" -Value "1" -NoNewline -Encoding ascii
```
Empaquetar: `scripts/pack_sqx.ps1 -Src <carpeta fuente> -Out <fichero.sqx>` (valida el XML y usa entradas con `/`). No usar `Compress-Archive` (en PS 5.1 mete `\` en las rutas). Comprobar con `unzip -l fichero.sqx` en Git Bash.

## Esqueleto

La plantilla completa y probada está en `assets/plantilla_strategy_Portfolio.xml` (versión 05: largos, BUY STOP en Highest, SL/TP por ATR, filtro EMA larga). Partir de ella.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<StrategyFile Version="3.9.133" AppVersion="SQX Build 143.2708">
  <Strategy name="Nombre" engine="MetaTrader5 (hedged)">
    <Note width="0" height="0" left="0" top="0" type="NONE" />
    <Description>texto con &lt; &gt; escapados</Description>
    <MoneyManagement type="FixedSize"><params><Param key="#Size#" varname="null" type="double">1</Param></params></MoneyManagement>
    <GlobalSLPT>…globalSL / globalPT fijos a 0…</GlobalSLPT>
    <Rules><Events>
      <Event key="OnInit" />
      <Event key="OnBarUpdate"><Rule type="IfThen" name="…" everyTick="false"><Descriptions /><If>…</If><Then>…</Then></Rule></Event>
      <Event key="OnDeinit" />
    </Events></Rules>
    <Variables>…</Variables>
    <Datas><data><id>0</id><chart>Main chart</chart><symbol>NULL</symbol><timeFrame>0</timeFrame></data></Datas>
  </Strategy>
  <ATMs source="none" enable="false" />
</StrategyFile>
```
(Los ejemplos oficiales usan `Version="3.9.130"` con un bloque `<options>`; ambos formatos aparecen.)

## Variables (parámetros externos)

```xml
<variable makeExternal="true"><id>33333333-3333-3333-3333-333333333333</id><name>BreakoutPeriod</name><type>int</type><value>30</value><paramType></paramType><makeExternal>true</makeExternal></variable>
```
Los bloques las referencian poniendo el GUID como texto del Param, con `variable="true" variableType="INT"` (o `"DOUBLE"`). GUIDs legibles tipo `1111…`, `AAAA…` funcionan. `type` = `int` o `double` (valor `2.0`).

Mapa usado en Breakout Pullback EMA: 1111 MagicNumber (11111), 2222 EMAPeriod, 3333 BreakoutPeriod, 4444 BarsValid, 5555 SLATRMult, 9999 TPATRMult, 7777 ATRPeriod, AAAA ExitAfterBars, BBBB TrendEMAPeriod, 6666/8888 TrailingATRMult/TrailingActivationATR.

## Bloques verificados (copiar los atributos exactos de la plantilla)

- **AND**: `<Item key="AND">` con **exactamente dos** `<Block>`. Para 3+ condiciones, anidar.
- **MarketPositionIsFlat** (`mI="StrategyControl"`, params `#Symbol#`=Current, `#MagicNumber#`=GUID, `#Comment#`).
- **PendingOrderDoesntExist**: igual + `#Direction#` (`values="Long=1,Short=-1,Any=0"`).
- **IsLower / IsGreater** (`mI="Comparisons"`, `name="(&lt;) Is lower"` / `"(&gt;) Is greater"`), hijos `<Block key="#Left#">` y `<Block key="#Right#">`. Hay un ejemplo con `&amp;amp;gt;` doble escapado: no copiarlo.
- **Close / High / Low** (`mI="Price"`, `categoryType="priceValue"`, `#Chart#`=0, `#Shift#`).
- **EMA** (`mI="MovingAverage"`, `#ComputedFrom#` 0=Close…, `#Period#`, `#Shift#`).
- **Highest** (`mI="HighestIndex"`, `#ComputedFrom#`=**2** para High).
- Aritmética: `Plus`, `Minus`, `Multiplication` (`mI="Functions"`, `returnType="pricenumber"`), `DoubleVariable` (`#Variable#`=GUID), `Number`.
- **EnterAtStop** (`mI="Open"`, `returnType="order"`): `#Price#` con `SQ.Formulas.Price.UseFormula` + `<Block key="#Value#" … valueType="price">`; `#Direction#`; `#Size#` = `SQ.Formulas.Size.UseGlobalMM`; `#BarsValid#`; `#ReplaceExisting#`; `#AllowDuplicateTrades#`; y los métodos de salida:
  - `#ExitAfterBars.ExitAfterBars#` (int, 0 = sin límite)
  - `#ProfitTarget.ProfitTarget#` y `#StopLoss.StopLoss#` → `SQ.Formulas.SLPT.ATRBasedValue` con `#Value#` (multiplicador, double) y `#AtrPeriod#`
  - `#TrailingStop.TrailingStop#` → `SQ.Formulas.RangeLevel.ATRBasedValue`; `#TrailingStop.TrailingActivation#` → `SQ.Formulas.Range.ATRBasedValue`
  - `#MoveSL2BE.MoveSL2BE#` / `#MoveSL2BE.SL2BEAddPips#`
  - Desactivar: `SQ.Formulas.RangeLevel.None` (trailing, BE) y `SQ.Formulas.Range.None` (activación, add pips).
  - SL/TP a un precio: `SQ.Formulas.SLPT.PriceLevel` con un `<Block key="#Value#" … valueType="price">` (p. ej. `Low[1]`).
- Formulas disponibles en Snippets.jar — SLPT: ATRBasedValue, FixedValue, None, PctValue, PriceLevel, ValueFromFormula. RangeLevel / Range: ATRBasedValue, FixedValue, None, PriceLevel, PriceRange.

**Ojo**: "no reemplazar la orden pendiente" se consigue con la condición `PendingOrderDoesntExist`, aunque `#ReplaceExisting#` esté a true. Quitando esa condición se obtiene la variante que reemplaza.

**Trailing**: su sintaxis salió de internals de SQX, no de un ejemplo funcionando. Si se usa, pedir al usuario que confirme que no aparece vacío al importar.

## Cómo descubrir / verificar un bloque nuevo

1. Buscar en ejemplos reales (Git Bash):
   ```bash
   grep -ho 'key="[A-Za-z]*"' $SQXTMP/ex/*/strategy_Portfolio.xml | sort | uniq -c
   grep -h -m1 -A12 '<Item key="EMA"' $SQXTMP/ex/*/strategy_Portfolio.xml
   unzip -p fichero.sqx strategy_Portfolio.xml | grep -A12 'key="RSI"'   # estrategias de tus databanks
   ```
2. Fórmulas y nombres de parámetros en el jar (no hay `strings`):
   ```bash
   unzip -l $SQX/internal/libs/Snippets.jar | grep Formulas/SLPT/
   unzip -p $SQX/internal/libs/Snippets.jar SQ/Formulas/SLPT/ATRBasedValue.class | tr -c '[:print:]' '\n' | grep -E '.{3,}'
   ```
3. Plantillas del builder: `<SQX>\internal\web\BUILDER\templates\` y rangos de generación en `<SQX>\internal\plugins\TaskBuild\task_forex.xml`.
4. Bloques custom ya instalados: `<SQX>\user\extend\Snippets\SQ\Blocks` (no usarlos en entregas salvo que lo pida).

## Comprobar el fichero generado

```powershell
$x=[xml](Get-Content "$src\strategy_Portfolio.xml" -Raw -Encoding UTF8)
$x.StrategyFile.Strategy.Variables.variable | ft name,type,value
```
Revisar después el árbol `<If>` (anidamiento de AND, periodos y shifts) y que todos los GUID usados existen en `<Variables>`.

## Modificar un XML existente

Copiar la carpeta `fuente` a la nueva carpeta numerada y aplicar cambios con Edit o con un programa Java pequeño (`scripts/Make05.java` es el ejemplo: añade condiciones generando bloques con helpers `ema()`, `close()`, `greater()`). Compilar con `javac -encoding UTF-8`. jshell destroza las barras invertidas de las regex.
