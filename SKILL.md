---
name: hobbiecode-strategyquant
description: Trabajar con StrategyQuant X (SQX) - crear estrategias .sqx importables en AlgoWizard, leer los datos .dat de SQX, simular y validar en Java (rejilla con precisión M1, walk-forward, entradas aleatorias, Monte Carlo), exportar el setup a TradingView (Pine v6), descargar datos de Dukascopy/Darwinex y configurar instrumentos en el Data Manager, desarrollar snippets propios (columnas de databank, bloques) compilados en el Code Editor web, generar custom projects .cfx, hacer autosuficiente un EA exportado a MT5 y manejar la GUI web de SQX. Úsala siempre que el usuario hable de SQX, StrategyQuant, AlgoWizard, un .sqx/.cfx, "hazme/optimiza una estrategia", walk-forward, Monte Carlo, "pásamelo a TradingView", "actualiza los datos", Data Manager, "configura el instrumento", spread/comisión/swap, snippets, "columna en el databank", "bloque/indicador propio", Code Editor o custom project, aunque no diga la palabra skill.
---

# StrategyQuant X: estrategias, optimización y validación (Hobbiecode)

Skill de **Hobbiecode** con el método de trabajo y las herramientas para usar Claude con StrategyQuant X:
crear estrategias, validarlas con rigor, configurar datos e instrumentos y ampliar SQX con snippets propios.
Responde en el idioma del usuario (está escrita en español), con tablas, conclusiones en negrita y advertencias honestas.

> Herramienta de investigación y backtesting. Nada de lo que produce es asesoramiento financiero.
> Los resultados pasados no garantizan resultados futuros.

## 0. Configuración (lo primero, en cada PC)

La skill usa estos marcadores en sus guías y comandos. Antes de trabajar, **averigua sus valores** y recuérdalos (memoria del proyecto si existe):

| Marcador | Qué es | Cómo encontrarlo |
|---|---|---|
| `<SQX>` (`$SQX` en bash, `$SQXW` con barras `C:/…`) | Carpeta de la instalación **activa** de SQX (la que tiene `StrategyQuantX.exe` y los datos al día) | Buscar `C:\SQX*`, `C:\StrategyQuant*` o preguntar. Si hay varias, usar la de `user\data\History` con fechas más recientes y confirmarlo con el usuario |
| `<JAVA>` | El JDK que trae SQX: `<SQX>\j64\bin` (`java.exe`, `javac.exe`, `jar.exe`) | Normalmente no está en el PATH |
| `<TMP>` (`$SQXTMP`, `$SQXTMPW`) | Carpeta de trabajo **con ruta corta**, p. ej. `C:\sqxtmp` | Java y Python fallan con rutas muy largas; créala si no existe |
| `<TRABAJO>` | Carpeta donde dejar entregables | Preguntar al usuario |
| Python | Cualquier Python 3 (solo para `make_project.py`) | `python --version`; en Windows el `python` del PATH puede ser el stub de Microsoft Store |

En Git Bash, definir las variables al principio de cada comando, por ejemplo:
```bash
SQX=/c/SQX_143; SQXW=C:/SQX_143; SQXTMP=/c/sqxtmp; SQXTMPW=C:/sqxtmp; J=$SQX/j64/bin
```
(No usar `$TMP`: en Windows ya es la carpeta temporal del sistema.)

Rutas fijas dentro de la instalación:

| Cosa | Ruta |
|---|---|
| Datos | `<SQX>\user\data\History\<SÍMBOLO>\<SÍMBOLO>_<TF>.dat` |
| Instrumentos | `<SQX>\user\data\data.db` (SQLite; leer copias con `scripts/SqxDb.java`) |
| Log | `<SQX>\user\log\StrategyQuant\log_AAAA_MM_DD.log` |
| Snippets propios | `<SQX>\user\extend\Snippets\SQ\...` (se compilan en el Code Editor web, no con javac) |
| Proyectos | `<SQX>\user\projects\<Nombre>\project.cfx` |
| Ejemplos oficiales de estrategias | `<SQX>\internal\web\AlgoWizard\examples\*.sqx` (descomprimir en `<TMP>\ex`) |
| GUI web (con SQX abierto) | `http://localhost:8080/SQUANT/index.html` y Code Editor en `http://localhost:8080/SQEDITOR/index.html#!/editor`. Ver `references/gui-web.md` |

Con muchos núcleos, las rejillas de `Opt.java` van rápidas: usar `-Xmx8g` o lo que permita la RAM.

## Convenciones de entrega (recomendadas)

- Cada estrategia o entregable en una **subcarpeta numerada** de `<TRABAJO>` (`01 - Nombre`, `02 - …`): listar y usar el siguiente número libre.
- Dentro: el `.sqx` arriba, `fuente/` con el XML desempaquetado (`META-INF/MANIFEST.MF`, `version.txt`, `strategy_Portfolio.xml`), `herramientas/` si hay código, y un `RESULTADOS.md`.
- **Nunca sobrescribir una versión anterior**: cada variante es una carpeta nueva; las correcciones se añaden al `RESULTADOS.md` como sección fechada.
- El usuario comprueba en AlgoWizard antes de seguir: darle siempre cifras de referencia (nº de operaciones, PF, R por año y **la primera operación con fecha, hora y precio**).

## Flujo de trabajo

0. **Datos e instrumentos** (si faltan datos, están desfasados o hay que cambiar costes): descargar o actualizar desde el Data Manager (Dukascopy M1 o Darwinex tick), comprobarlo en el log y en `data.db`, y revisar el instrumento enlazado al símbolo. Ver `references/datos-e-instrumentos.md`.
1. **Entender la regla** y escribirla en pseudocódigo con `[1]` (solo velas cerradas). Si algo no se puede hacer con bloques estándar, decirlo (p. ej. SL en el mínimo de la vela de ruptura: SQX calcula el SL al **colocar** la orden, no al ejecutarse).
2. **Construir el .sqx** partiendo de `assets/plantilla_strategy_Portfolio.xml` o de un ejemplo oficial. Sintaxis, bloques verificados y empaquetado: `references/sqx-xml.md`.
3. **Simular** con las herramientas Java (`scripts/`): leer datos, rejilla en H1, re-simular lo mejor en M1. Ver `references/datos-y-simulador.md`. `Opt.java` es una plantilla hecha para la estrategia de ejemplo (Breakout Pullback EMA): adaptar `sim()` a cada estrategia. Comprobar siempre un precio conocido tras leer los datos.
4. **Validar la robustez** antes de recomendar nada: meseta (no pico), entradas aleatorias, walk-forward, sensibilidad parámetro a parámetro, años laterales, Monte Carlo. Ver `references/robustez.md`.
5. **Entregar**: carpeta numerada + `RESULTADOS.md` (datos, costes, parámetros, tabla por año en R, perfil: % acierto, rachas, DD, MC p95, advertencias, ficheros) + instrucciones para AlgoWizard.
6. **TradingView** si lo pide: indicador Pine v6 que replica la lógica + `.md` de instrucciones. Ver `references/tradingview.md`.
7. **Snippets propios** (columnas, bloques, indicadores, MC, MM…): escribir el `.java` en `user\extend\Snippets\SQ\<Tipo>\` siguiendo los internos, compilar con "Compile all" en el Code Editor web, recargar la UI (si el usuario lo autoriza) y comprobar contra una columna o bloque interno equivalente. Ver `references/snippets-api.md` y `assets/snippets/`.
8. **Exportar a MT5 / distribuir el EA**: integrar los indicadores Sq (`iCustom`) en el `.mq5` y comprobar con un backtest en MT5 que original e integrado dan las mismas operaciones. Ver `references/exportar-mq5.md` (`scripts/PatchEA.java` es el ejemplo para un EA concreto).
9. **Custom projects**: generar el `.cfx` a partir de las tareas del Builder, Retester y Optimizer del usuario con `scripts/make_project.py`, instalarlo en `user\projects\` y abrirlo en la GUI **sin lanzarlo**. Ver `references/proyectos-cfx.md`.

## Reglas de oro (errores que ya pasaron)

- **Decimales de los .dat**: los M1 de Dukascopy (XAUUSD, NQ, SP) usan **6 decimales en el fichero** (`SqxDat.read(path, 6)`), aunque `data.db` diga otra cosa. Con 5 los precios salen ×10 y los costes quedan 10 veces más pequeños sin que los R cambien: el error no salta a la vista. Comprobar siempre un precio real.
- Si hay varias instalaciones de SQX, usar **la activa** (la de datos más recientes). Ojo con el año de las fechas que diga el usuario.
- AND en SQX es **binario**: dos `<Block>` por `<Item key="AND">`, anidar para más condiciones.
- No editar XML con awk/sed de registros (`RS=`) ni con `&` en reemplazos: rompe el fichero. Usar Edit o un pequeño programa Java con `replaceAll("(?s)…")` (ver `scripts/Make05.java`).
- Validar el XML con `[xml](Get-Content … -Raw -Encoding UTF8)` antes de empaquetar, y empaquetar con rutas con `/` (`scripts/pack_sqx.ps1`).
- No elegir parámetros mirando el periodo fuera de muestra. Si un filtro se descubre después de ver un drawdown, **decirlo** como sesgo.
- Las cifras del simulador propio hay que confirmarlas con el backtest de SQX. No afirmar que algo se ha probado en SQX, MT5 o TradingView si no se ha hecho.
- Descomprimir y borrar en carpetas nuevas con nombre explícito (nunca `rm -rf *` tras un `cd`).
- Los instrumentos se cambian **desde la interfaz de SQX**, no escribiendo en `data.db`. Un símbolo de un bróker puede usar el instrumento de otro (p. ej. datos de Dukascopy con costes de Darwinex), así que un cambio afecta a todos los símbolos enlazados.
- **Nunca escribir contraseñas** (acceso remoto de SQX, cuentas de bróker). Si hace falta iniciar sesión, lo hace el usuario.
- En la GUI web: hacer una captura antes de clicar, saber que el contenido va dentro de iframes y comprobar cada guardado fuera de la GUI (`data.db`, el log). Recargar la interfaz, lanzar proyectos o retestear databanks **solo si el usuario lo dice**. Crear vistas y databanks nuevos en vez de tocar los suyos.
- En los `.sqx` que se entregan, solo bloques estándar, para que importen sin plugins.
- Si se aprende algo nuevo, proponer al usuario añadirlo a esta skill.
