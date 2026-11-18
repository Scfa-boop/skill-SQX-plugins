package SQ.Columns.Databanks;

import java.util.Map;
import java.util.TreeMap;

import com.strategyquant.lib.L;
import com.strategyquant.lib.SettingsMap;
import com.strategyquant.tradinglib.DatabankColumn;
import com.strategyquant.tradinglib.Order;
import com.strategyquant.tradinglib.OrdersList;
import com.strategyquant.tradinglib.SQStats;
import com.strategyquant.tradinglib.StatsTypeCombination;
import com.strategyquant.tradinglib.ValueTypes;

/**
 * Hobbiecode - maxima racha de semanas consecutivas perdedoras.
 * Agrupa el P/L por semana de cierre (lunes 00:00 a domingo 23:59, hora de los datos).
 * Solo cuentan las semanas con operaciones: una semana sin operaciones no corta la racha,
 * una semana con P/L = 0 si la corta.
 */
public class HCMaxConsecLossWeeks extends DatabankColumn {

	private static final long DAY_MS = 24L * 60 * 60 * 1000;

	public HCMaxConsecLossWeeks() {
		super(L.tsq("HC Max Consec. Loss Weeks"), DatabankColumn.Integer, ValueTypes.Minimize, 0, 0, 20);

		setTooltip(L.tsq("Maximum consecutive losing weeks (weekly P/L < 0, weeks without trades are skipped)"));
	}

	//------------------------------------------------------------------------

	@Override
	public double compute(SQStats stats, StatsTypeCombination combination, OrdersList ordersList, SettingsMap settings, SQStats statsLong, SQStats statsShort) throws Exception {
		TreeMap<Long, Double> weeklyPL = new TreeMap<Long, Double>();

		for(int i = 0; i < ordersList.size(); i++) {
			Order order = ordersList.get(i);
			if(order.isBalanceOrder()) continue;

			// 01-01-1970 fue jueves: +3 dias hace que las semanas empiecen en lunes
			long week = Math.floorDiv(Math.floorDiv(order.CloseTime, DAY_MS) + 3, 7);
			weeklyPL.merge(week, getPLByStatsType(order, combination), Double::sum);
		}

		int streak = 0;
		int maxStreak = 0;

		for(Map.Entry<Long, Double> e : weeklyPL.entrySet()) {
			if(e.getValue() < 0) {
				streak++;
				maxStreak = Math.max(streak, maxStreak);
			} else {
				streak = 0;
			}
		}

		return maxStreak;
	}
}
