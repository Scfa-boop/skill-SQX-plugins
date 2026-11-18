# Manejar SQX desde su GUI web

SQX tiene que estar abierto en el PC. Se maneja con el navegador integrado (`mcp__Claude_Browser__*`) o con Claude in Chrome.

| Qué | URL |
|---|---|
| SQX (Builder, Retester, Data Manager, Custom Projects…) | `http://localhost:8080/SQUANT/index.html` |
| Code Editor | `http://localhost:8080/SQEDITOR/index.html#!/editor` (o el enlace "Code Editor" arriba a la derecha) |

El puerto 5051 es el de "una sola instancia" y responde "Unable to resolve the request": no es la GUI.

## Inicio de sesión

El acceso remoto pide una contraseña (`user\settings\remote_access.xml`). **La escribe el usuario**: parar, decirle que abra el panel del navegador (Ctrl+Shift+B) e inicie sesión, y esperar a que diga "listo".

## Trucos que funcionaron

- **Captura antes de clicar por coordenadas.** Después de cada `navigate` hay que hacer una captura. Sin ella da `left_click with 'coordinate' requires a prior computer{action:"screenshot"}`.
- **Viewport de 1500x950** (`resize_window`) para ver las tablas del Data Manager y la barra del Code Editor. Con ese viewport emulado, los clics por `ref` pueden caer en otro elemento (en el diálogo "Select columns" se añadió tres veces "Net profit"). En los diálogos con listas, volver a `preset: desktop` y clicar por coordenadas tras una captura.
- **Casi todo va dentro de iframes.** `get_page_text` y los selectores normales (`.ui-grid-*`, `role=row`) no ven el contenido. Para leer una tabla:
  ```js
  [...document.querySelectorAll('iframe')].map(f => f.contentDocument.body.innerText).filter(t => t.includes('HC Avg. Win'))
  ```
  Para saber qué tiene el foco: `let a=document.activeElement; if(a.tagName==='IFRAME'){a=a.contentDocument.activeElement}; [a.tagName, a.value]`.
- **Selects de Angular** (p. ej. el tipo de swap `config.swapType`: money/percent/points). `form_input` dice que ha funcionado pero no se guarda, porque hay selects duplicados ocultos. Escribir una letra + Return guarda el modal con el valor viejo. Lo que funciona:
  1. clicar el campo vecino;
  2. `shift+Tab` para pasar el foco al select;
  3. `Home`/`End`/flechas para elegir la opción;
  4. comprobar el valor con el JS de arriba;
  5. pulsar el botón **Save**.
- **Selects con autocompletado** (p. ej. el instrumento en el editor de datos). Con la acción `type`, el texto acabó en el campo del nombre (`SP_M1_dukaSP500_darwinexs`). Lo que funciona: cerrar sin guardar (X), volver a abrir, `Tab` hasta el select y usar la acción `key` con las letras separadas por espacios (`"S P 5 0 0 _ d"`).
- En los modales, **Escape cierra sin guardar** y Enter guarda. Para editar un número: triple clic y escribir.
- **Después de guardar, comprobarlo siempre fuera de la GUI**: `data.db` con `SqxDb` sobre una copia, o el log `user\log\StrategyQuant\log_AAAA_MM_DD.log`. Ejemplo: el primer guardado del swap de SP500 tenía bien los números pero mal el tipo.
- Para esperar a un trabajo largo, sondear el log en bash (`for i in $(seq 40); do grep -q "Finished: SP_M1_dukas" log && break; sleep 5; done`).

## Recargar la interfaz y lanzar cosas

- Recargar la UI de SQX (tras compilar), lanzar proyectos o hacer retests sobre sus databanks **lo decide el usuario**. Preguntar antes.
- Si hace falta ver columnas o vistas nuevas, crear una **vista nueva**, sin tocar las suyas por defecto.

## Rutas dentro de la GUI (coordenadas aproximadas en una captura escalada a 800x507, solo orientativas)

- Data Manager: icono de la izquierda. Pestañas **Data** e **Instruments**, con un filtro arriba de la tabla. Doble clic en una fila abre el editor.
- Retester: icono de la izquierda. El panel de databanks está abajo ("DATABANKS n / STRATEGIES n"). Para lanzar: Full settings → Cross checks (todo desactivado) → Progress → Start.
- Custom Projects: "Open existing project" abre un explorador de ficheros web. Elegir la carpeta del proyecto y luego `project.cfx`.
- Code Editor: botón **Compile all** en la barra superior.
