# Custom projects de SQX (.cfx)

Ejemplo de referencia: un proyecto Build → Retest OOS → Walk-forward para SP500 H1, solo largos, instalado en `<SQX>SerprojectsSP500_H1_Basicoproject.cfx` y abierto sin lanzarlo.

## Formato

Un `.cfx` es un **zip** con:
- `config.xml`:
  ```xml
  <Project name="…" version="143.2708">
    <Tasks><Task type="Build" name="Build-Task1" taskXMLFile="Build-Task1.xml" title="1 - Generar" active="true" …/> …</Tasks>
    <Databanks><Databank name="1-Build" view="Default - Main data" syncType="…" position="…"/> …</Databanks>
  </Project>
  ```
- una XML por tarea: `Build-Task1.xml`, `Retest-Task1.xml`, `Optimize-Task1.xml`, `LoadFromFiles-…`, `SaveToFiles-…`, `ClearDatabanks-…`, `GoToTask-…`, `WaitFor-…`, `CallExternalScript-…`.

**Plantillas**: el Builder, el Retester y el Optimizer sueltos también son proyectos, en `user\projects\Builder|Retester|Optimizer\project.cfx`. Sus XML de tarea llevan los ajustes actuales del usuario. Descomprimir con `<SQX>\j64\bin\jar xf project.cfx` en una carpeta nueva.

## Puntos clave del XML de una tarea (dentro de `<Data>`)

- `<Setups><Setup dateFrom="2013.01.01" dateTo="…" testPrecision="1|2" session="…" engine="MetaTrader5 (hedged)">` con `<Chart symbol="SP_M1_dukas" timeframe="H1" spread="6"/>`, `<Commissions><Method …/></Commissions>` y `<Swap …/>` (mismos formatos que en `data.db`; ver `datos-e-instrumentos.md`).
- `<OutOfSample showGraph="false"><Range dateFrom="…" dateTo="…"/></OutOfSample>`.
- Databanks de la tarea: `<Databanks><Databank label="Output databank" name="Output" value="1-Build"/> … <Databank name="Input" value="…"/>`. El Builder tiene **dos** Output (Results y Last generation) y un Input ("Strategies to improve").
- `<Resources><Symbols><Symbol …><InstrumentInfo …>` es una copia en caché del instrumento. Se puede quitar: SQX lo recarga sin avisar de instrumentos sin resolver.
- Builder: `<Param key="MarketOpenSession">` (p. ej. `SP500_darwinex`). Opciones de trading en `<BuildTradingOptions><Params><Param key="LimitTimeRange">…`. `SignalTimeRangeFrom/To` van en segundos (9000 = 02:30, 84600 = 23:30).
- Optimizer: `<Optimization type="1|2|3">` (1 = simple, 2 = walk-forward, 3 = matriz WF; sacado del JS de `internal\web`). Precisión forzada a 1. El `RiskMoneyManagement` de la plantilla era de EURUSD/MT4 FixedSize 0,1: copiar el del Builder.
- Filtros: `<Condition use="true" column="NetProfit" sampleType="20">` con comparador `>` y valor `0`. **sampleType 10 = IS, 20 = OOS**.
- Parada del Builder: `<StopCondition type="databank-full" passedStrategies="1000" restartCount="5" …/>`.

## Generarlo

`scripts/make_project.py` (Python con `xml.etree`):
1. carga las tres tareas de plantilla;
2. cambia fechas, motor, precisión, gráfico, spread, comisiones, swap y rango OOS;
3. encadena los databanks `1-Build → 2-Retest → 3-Optimized`;
4. copia el money management del Builder al Optimizer;
5. escribe `config.xml` y el zip, y vuelve a parsear cada entrada como comprobación.

Las constantes de mercado (símbolo, TF, spread, costes, fechas) están al principio del script; cambiarlas en cada proyecto.

```bash
PY=python   # cualquier Python 3
$PY -I $SQXTMPW/tools/make_project.py <carpeta con Builder/Retester/Optimizer descomprimidos> <salida.cfx> <Nombre>
```
Python y Java pueden fallar con rutas muy largas (`[Errno 2] No such file or directory`). Trabajar en `<TMP>	ools`.

Si el usuario ya tiene su propio generador de proyectos, usarlo o seguir su estilo. Las tareas *Call external script* guardan rutas absolutas a scripts: revisarlas al mover proyectos entre PCs.

## Instalarlo y comprobarlo

1. Crear `user\projects\<Nombre>\` y copiar el fichero ahí como `project.cfx`. Dejar también una copia en la carpeta numerada de entrega, con un `LEEME.md`.
2. En la GUI: Custom Projects → refrescar → "Open existing project" → elegir la carpeta → `project.cfx` → Open.
3. Leer el "Settings summary" de cada tarea (innerText del iframe) y comprobar símbolo, fechas, costes, databanks y el walk-forward (OOS %, número de ejecuciones) en Full settings.
4. **No lanzarlo** sin que el usuario lo diga. Dejar escrito en el `LEEME.md` lo que tiene que revisar: filtros, ventana horaria, dirección y parámetros del WF.

SQX vuelve a guardar el `project.cfx` al abrirlo o ejecutarlo (añade databanks como Results o Strategies to optimize), así que el tamaño cambia.
