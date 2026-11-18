# Validación de robustez

Orden de trabajo que funcionó (Breakout Pullback EMA, XAUUSD H1):

1. **Rejilla amplia + meseta**: elegir el centro de una zona estable por `nbScore`, no el máximo. (BarsValid 80 era un pico aislado → se eligió 90.)
2. **Entradas aleatorias** (`bench`, 300–500 simulaciones): con un mercado muy alcista casi todo gana (el 99 % de combinaciones ganaba en 2025). Lo que cuenta es superar a entradas al azar con las mismas salidas. Objetivo: > 95 % en IS y OOS.
3. **Sensibilidad un parámetro cada vez** (total y peor año), y barridos finos de los parámetros frágiles. Señalar el parámetro ruidoso (allí BarsValid: con la orden sin reemplazar, la caducidad cambia toda la cadena de operaciones).
4. **Años laterales** como prueba de estrés (2021–22 en el oro) y un año nunca usado en ninguna optimización (2019).
5. **Walk-forward** (`wf`): probar varias ventanas IS/OOS (12/3, 24/3, 24/6, 36/3, 36/6 meses). Selección por ventana = mejor nbScore con ≥ 40 operaciones. Si los parámetros saltan entre ventanas pero se repite una **familia**, fijar un representante de la familia en vez de cambiar cada trimestre.
6. **Monte Carlo** (`MC`): dimensionar con el DD del p95, no con el máximo histórico. Calcular la racha de pérdidas esperada.
7. **Riesgo**: con stops por ATR, el tamaño fijo no significa nada; recomendar 0,5–1 % por operación y dar la curva a ese riesgo.

## Trampas

- Excluir combinaciones cuya única salida es el SL: el WF elegía una que aguantaba todo el rally de 2021 (+380R en una operación). Al excluirlas, poner −99 en su celda para que cuenten como vecinas malas.
- **No mirar el OOS para elegir**. Si el usuario señala un problema en el OOS ("pedazo de drawdown en 2026"), analizarlo (desglose del DD, MC) y, si se añade un filtro, decir claramente que está sesgado y justificarlo con una meseta amplia (allí EMA 700–1500 × pendiente 60–240) y un menor DD en Monte Carlo.
- Revisar que las columnas derivadas comparan lo que deben (una vez se restó nº de operaciones en vez de R).
- Estrategias que no reemplazan la orden pendiente son dependientes del camino: con otro feed (OANDA vs Dukascopy) cambian operaciones concretas; solo coinciden en promedio.
- Reemplazar la orden en cada setup se probó y fue peor (el doble de operaciones y de DD, mismo R): el máximo viejo actúa de filtro.
- Señales siempre con `[1]` y máximos/mínimos sobre velas cerradas.

## Qué poner en RESULTADOS.md

Datos y rango de fechas, costes, parámetros (con zona estable), tabla por año en R (ops, R, PF, DD, % acierto), comparación con la versión anterior, percentil frente a aleatorio, robustez, perfil (acierto, rachas, meses negativos, duración media), MC (mediana / p95), curva a 0,5 % y 1 %, advertencias (sesgos, mercado alcista, simulador propio) y lista de ficheros.
