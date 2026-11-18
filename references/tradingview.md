# Exportar el setup a TradingView

Si el usuario tiene otro Claude con un MCP de TradingView, prepárale el trabajo. Entregar en una carpeta numerada (`NN - Setup TradingView`):
- un indicador **Pine v6** que replica exactamente la lógica del simulador y lo dibuja todo solo (ejemplo completo: `assets/breakout_pullback_ema.pine`);
- un `.md` de instrucciones para ese otro Claude (modelo: `PINTAR_SETUP_TRADINGVIEW.md` en la carpeta 03 del proyecto).

## Indicador

- `indicator(..., overlay=true, max_boxes_count=500, max_lines_count=500, max_labels_count=500, max_bars_back=5000)`.
- Estado en variables `var` (pend, lvl, pBar, pA, pos, entry, sl, tp, risk, eBar).
- Mismo orden que el simulador en cada vela: salida por tiempo → caducidad → nueva orden (con valores `[1]`, `lvl := hh[1]`, `pA := atr[1]`) → ejecución (`high >= lvl`, entrada `max(lvl, open)`) → SL, TP, trailing.
- Dibujos: caja roja entrada→SL y verde entrada→TP, línea azul discontinua de la orden pendiente (gris y "caducada" al vencer), etiqueta de salida tipo `TP +5.00R`, tabla resumen en `barstate.islast`, EMAs y `bgcolor` gris donde el filtro no deja operar.
- Entradas = parámetros de SQX (0 = desactivado para TP, trailing, salida por tiempo) y toggles para filtros, así una versión sirve para varias variantes.
- Entrada "Desde": TradingView solo conserva los últimos 500 dibujos de cada tipo.
- El spread no se modela en Pine.
- No puedo compilar Pine aquí: decirlo siempre.

## Instrucciones (.md) para el otro Claude

- Tabla de reglas y detalles de ejecución para que coincida con el backtest.
- Método A: cargar el Pine; si no compila, corregir solo sintaxis, nunca la lógica. Quitar antes versiones anteriores del indicador.
- Método B (si no puede cargar Pine): calcular desde OHLC (EMA α = 2/(n+1), ATR Wilder/RMA) y dibujar con la herramienta Long Position.
- Opcional: pintar las operaciones exactas del CSV, comprobando antes el desfase horario (datos SQX = NY+7) con 2–3 operaciones.
- Perfil esperado (acierto, rachas largas) para que no lo "arregle", y lista de "qué NO hacer".
- Historial suficiente para EMAs largas (EMA 1000 → ~3.000 velas).
- Avisar de que con otro feed (OANDA) algunas operaciones no coincidirán.

Cada vez que cambie la estrategia, actualizar los valores por defecto del Pine y el .md.
