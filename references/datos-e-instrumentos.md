# Descargar datos (Dukascopy, Darwinex) y configurar instrumentos en SQX

Todo esto se hace en el **Data Manager de SQX** (pestañas Data, Instruments, Sessions, Broker, Connections). No hay una API de descarga documentada ni un `sqcli` que funcione con SQX abierto ("another instance of StrategyQuant X is running"). No hay que escribir un descargador propio.

## Cómo se maneja SQX desde fuera

- SQX abierto en el PC. **La GUI web se abre en http://localhost:8080** (`/SQUANT/index.html`); el 5051 responde "Unable to resolve the request". En el navegador integrado, ampliar el viewport a 1500x950 para ver la tabla del Data Manager. Actualizar: pestaña Data → filtrar → marcar fila → "Update selected" (unos 20 s). Antes se apuntaba aquí el **5051** (el puerto que usa la instancia; ver `Preventing multiple instances: The app is already running on port 5051` en el log).
- **Acceso remoto**: `<SQX>\user\settings\remote_access.xml` (`<Remote-Access enabled="true">`, con contraseña). Con el acceso desactivado, el log da `Unauthorized request. Remote access is disabled`.
- La contraseña la escribe **el usuario**, nunca Claude. Si el navegador pide login, parar y pedirle que inicie sesión.
- Para manejar la GUI usar el navegador (built-in o Claude in Chrome) o computer use. Hacer una captura y leer la página antes de cada clic: la interfaz cambia entre versiones.

## Descargar o actualizar datos

En el Data Manager, pestaña **Data**: elegir el símbolo, pulsar **Update/Download** (o **Add symbol** si es nuevo), elegir la fuente y esperar a que termine la cola.

| Fuente | Plugin | Tipo de datos | Nombre del símbolo en `History` |
|---|---|---|---|
| Dukascopy | DataSourceDukascopy | **M1** | `<SIM>_M1_dukas` (p. ej. `XAUUSD_M1_dukas`, `NQ_M1_dukas` = USATECHIDXUSD, `SP_M1_dukas` = USA500IDXUSD, `DJ_M1_dukas` = USA30IDXUSD) |
| Darwinex | DataSourceDarwinex | **TICK** | `<SIM>_darwinex` (p. ej. `XAUUSD_darwinex`, `NDXm_darwinex`) |
| Otras | DataSourceFiles (CSV), SQEquityData (`sq_equity\<letra>\<TICKER>`), SQFuturesData (`sq_futures\@\@NQ`), Crypto | | |

- Las dos fuentes bajan del CDN de SQX (`https://cdn.strategyquantcdn.com`). Se ve en `user\log\StrategyQuant\log_AAAA_MM_DD.log`:
  ```
  DownloadDukascopySymbolJob - Downloading NQ_M1_dukas from server: https://cdn.strategyquantcdn.com
  DarwinexDownloadJob - Downloading XAUUSD from server: https://cdn.strategyquantcdn.com
  DownloadDispatcher - Finished: XAUUSD_darwinex, already executed: 0, in queue: 0
  ```
  Para confirmar que terminó, buscar `Finished: <símbolo>` en el log de hoy (`grep -i "download\|Finished" log_…`).
- Tiempos medidos el 22-10-2026: actualizar un M1 de Dukascopy tarda unos 20 s. NDXm tick de Darwinex desde 2018 tardó unos 3 min y ocupa 7,9 GB. XAUUSD tick de Darwinex desde oct-2017 tardó unos 12 min y ocupa 5,9 GB.
- Los ficheros quedan en `<SQX>\user\data\History\<SÍMBOLO>\<SÍMBOLO>_<TF>.dat`. Con datos M1, SQX crea a su lado los marcos derivados (`_H1`, `_H4`, `_D1`…). Los de tick son `<SÍMBOLO>_TICK.dat`.
- **Usar la instalación activa `<SQX>`** (si hay varias, las otras pueden tener datos viejos).
- Antes de bajar ticks, comprobar el espacio en disco (`Get-PSDrive C`).

### Comprobar después de descargar

1. Fechas y número de filas en la tabla `DATA` de `data.db` (ver abajo, con `SqxDb`). Comparar `DATETO` con la fecha esperada (suele ser el día anterior).
2. Leer el `.dat` con `SqxDat` y comprobar un precio conocido. XAUUSD_M1_dukas, NQ_M1_dukas y SP_M1_dukas (verificado 23-10-2026: apertura 22-10 a las 16:30 = 7688,139) usan **6 decimales en el fichero**, aunque la columna `DECIMALS` de la tabla diga 3. Siempre comprobar con un precio real. El formato de los `_TICK.dat` de Darwinex **no está verificado** con SqxDat.
3. La zona horaria de todos estos datos es **EETUS** (Nueva York + 7 h todo el año). La marca de tiempo es la apertura de la vela. En los índices de EE. UU., la apertura de las 9:30 NY cae a las 16:30. Las acciones de `sq_equity` van en hora de Nueva York.

## Configurar instrumentos

Lo normal es hacerlo en el Data Manager, pestaña **Instruments**: buscar el instrumento (`XAUUSD_darwinex`, `NDX_darwinex`…), editar y guardar. Al guardar, SQX hace una copia `user\data\_backups\data.db.<AAAAMMDD_HHMMSS>.bak`.

### Dónde se guarda

`<SQX>\user\data\data.db` es una base **SQLite**. Las de futuros y acciones (`data_futures.h2.db`, `data_stock.h2.db`) son H2 y no se han conseguido abrir.

| Tabla | Para qué |
|---|---|
| `INSTRUMENTS` | Especificación: `POINTVALUE`, `TICKSIZE`, `TICKSTEP`, `DEFAULTSPREAD` (en ticks/puntos), `COMMISSIONS` (XML), `SWAP` (XML), `DEFAULTSLIPPAGE`, `ORDERSIZESTEP`, `MIN_DISTANCE`, `BROKER_ID`, `DATATYPE`, `SECTOR`… |
| `DATA` | Cada símbolo de datos: `SYMBOL` → `INSTRUMENT` que usa, `TIMEFRAME` (M1/TICK), `TIMEZONE`, `DATEFROM`/`DATETO` (epoch ms), `ROWS`, `DECIMALS`, `SOURCE` (2 = Dukascopy, 4 = Darwinex), `USYMBOL` (nombre en la fuente), `BROKER_ID` |
| `BROKER` | 3 Dukascopy (`_dukascopy`, EETUS), **4 Darwinex** (`_darwinex`, EETUS), 8 FTMO, 10 Monevis, 2 RoboForex (EET), 9 The5ers (Asia/Jerusalem), 5 ICMarkets, 6 Pepperstone, 7 OANDA, 11 Darwinex Zero. El sufijo del instrumento viene de aquí. |
| `SESSIONS` / `ELEMENTS` | Sesiones de mercado (Forex_245, Forex_247, US_Index_Futures, US_Stocks…) |

**Ojo**: un símbolo de datos puede usar el instrumento de otro bróker. Por ejemplo, `XAUUSD_M1_dukas` y `NQ_M1_dukas` usan `XAUUSD_darwinex` y `NDX_darwinex`: datos de Dukascopy con los costes de Darwinex. Al cambiar un instrumento cambian los backtests de todos los símbolos enlazados a él.

### Valores actuales (22-10-2026, fijados ese día)

| Instrumento | Point value | Tick size / step | Spread | Comisión | Swap |
|---|---|---|---|---|---|
| `XAUUSD_darwinex` | 100 | 0,01 / 0,01 | **20** (antes 15) | PercentageBased 0,005 % | points, long −24 / short 33,6 (antes −28 / 36,9), triple el miércoles, 23:00 |
| `NDX_darwinex` | 10 | 0,1 / 0,1 | **15** (antes 14) | SizeBased **5,5** por lote (antes 6) | money, long −24 / short 20,64 (antes −22,28 / 19,66), triple el viernes, 23:00 |
| `SP500_darwinex` (23-10-2026, web Darwinex Zero; `SP_M1_dukas` enlazado aquí) | 10 | 0,1 / 0,1 | 6 (= 0,6 pt) | SizeBased **0,55** por lote (0,275 por orden × 2) | money, long −11,56 / short 5,18 (antes points −10,44 / 5,50), triple el viernes, 23:00 |
| `XTIUSD_darwinex` | 1000 | 0,01 | 5 | SizeBased 0,005 | money 2,8 / −9,1 |
| `CHFJPY_darwinex` | 653,47 | 0,01 / 0,001 | 2,5 | SizeBased 6 | money −6 / 0 |
| `EURUSD_darwinex` | 100000 | 0,0001 / 0,00001 | 0,6 | 0 | points −11,2 / 3,7 |
| `XAUUSD_dukascopy` | 100 | 0,001 | 361 | 0 | points −460,53 / 247,5 |

Formatos XML de las columnas:
```xml
<Method type="SizeBased" use="true"><Params><Param key="Commission" className="SizeBased">5.5</Param></Params></Method>
<Method type="PercentageBased" use="true"><Params><Param key="CommissionPct" className="PercentageBased">0.005</Param></Params></Method>
<Method type="None" use="true"><Params/></Method>      <!-- sin comisión: NO poner use="false" en el único método -->
<Swap use="true" type="points|money" long="-24" short="33.6" tripleSwapOn="WEDNESDAY|FRIDAY|NEVER" rolloutHour="23:00"/>
```
Si se pone `use="false"` en el único método de comisión, el proyecto da `Cannot get main chart commissions config … Method xml element not found` (y un error de MoneyManagement en cadena). La solución es usar `type="None" use="true"` y volver a cargar el proyecto.

### De dónde sacar los valores

- **Web de Darwinex Zero**: https://www.darwinexzero.com/assets → pestaña Indices → buscar el ticker. Para leer la tabla: `document.querySelectorAll('table')` → innerText. La comisión que aparece es **por orden**; en SQX (SizeBased, ida y vuelta) se pone el doble.
- **Editar en la GUI** (navegador integrado): el modal es un iframe y no sale en el árbol de accesibilidad. Los `<select>` nativos no se ven abiertos: hay que enfocarlos con Tab y usar las teclas (Home, o letras con la acción `key`, una por una). **Escape cierra el modal sin guardar** y **Enter guarda**. Comprobar siempre el resultado en `data.db`.

Mirar la especificación del contrato en el MT5 del bróker (carpeta `Bases\<Servidor>` del terminal). También se puede preguntar al usuario. Conversión:
- `POINTVALUE` = valor en $ de un movimiento de 1,0 del precio para 1 lote (oro 100 oz → 100; NDX → 10).
- `DEFAULTSPREAD` se da en ticks del instrumento (oro tick 0,01 → spread 20 = 0,20 $).
- Comprobar el point value con una operación real: (PL − comisión) / puntos / lotes.

### Enlazar un símbolo de datos a otro instrumento

Ejemplo del 23-10-2026: `SP_M1_dukas` pasó de `USA500.IDX_dukascopy` (spread 460, point value 1) a `SP500_darwinex`.
1. Data Manager → pestaña **Data** → filtrar → doble clic en la fila.
2. Orden de los campos: nombre del símbolo → Tab → bróker → Tab → instrumento.
3. Elegir el instrumento con la acción `key` y las letras separadas (`"S P 5 0 0 _ d"`). Con `type` se estropea el nombre del símbolo.
4. Guardar y comprobar `DATA.INSTRUMENT` en `data.db`.

Las estrategias y los proyectos que ya existían conservan la copia vieja de costes y fechas hasta que se vuelve a elegir el símbolo.

En la tabla `SESSIONS` no hay columna `NAME` (`no such column: NAME`): usar `select *`.

### Leer la configuración (solo lectura, sobre una copia)

```bash
D=$SQXTMP/db; mkdir -p $D; cp $SQX/user/data/data.db $D/   # rutas cortas: Java falla con rutas muy largas
J=$SQX/j64/bin; $J/javac -d $D <carpeta de esta skill>/scripts/SqxDb.java
CP="$SQXTMPW/db;$SQXW/internal/libs/sqlite-jdbc.jar"
$J/java -cp "$CP" SqxDb $SQXTMPW/db/data.db tables
$J/java -cp "$CP" SqxDb $SQXTMPW/db/data.db sql "select * from INSTRUMENTS where INSTRUMENT like '%darwinex%'"
$J/java -cp "$CP" SqxDb $SQXTMPW/db/data.db sql "select SYMBOL,INSTRUMENT,TIMEFRAME,TIMEZONE,datetime(DATETO/1000,'unixepoch'),ROWS,SOURCE,USYMBOL from DATA order by ID desc"
```
Para ver qué cambió, hacer un `diff` de la misma consulta (ordenada) entre la copia actual y el `.bak` más reciente de `_backups`.

### Reglas

- **Cambiar instrumentos desde la interfaz de SQX**, no escribiendo en `data.db`. Si alguna vez hay que tocar el fichero: SQX cerrado, copia de seguridad fechada antes y permiso explícito del usuario.
- Cada `.sqx` y cada proyecto `.cfx` guarda **su propia copia** de los datos del instrumento (`<InstrumentInfo … pointValue= tickSize= …>`). Al cambiar el instrumento hay que volver a elegir el símbolo en AlgoWizard o en el proyecto para que lo recoja.
- Después de cambiar costes, decirle al usuario qué símbolo, marco temporal y fechas poner en AlgoWizard (si no, sale el último símbolo usado) y con qué precisión ("1 minute data tick simulation" como mínimo, o tick real si hay datos de Darwinex).
- Costes recomendados para los backtests: NQ_M1_dukas con spread 15, slippage 5 y la comisión del instrumento. XAUUSD_M1_dukas con spread 20, slippage 10 y la comisión del instrumento.
- Si cambian spread o comisiones, actualizar también las constantes del simulador propio (`SPREAD`, `COMM` en `Opt.java`) para que las cifras sigan siendo comparables.
