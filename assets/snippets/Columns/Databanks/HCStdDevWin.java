package SQ.Columns.Databanks;

import com.strategyquant.lib.L;
import com.strategyquant.lib.SettingsMap;
import com.strategyquant.tradinglib.DatabankColumn;
import com.strategyquant.tradinglib.Order;
import com.strategyquant.tradinglib.OrdersList;
import com.strategyquant.tradinglib.SQStats;
import com.strategyquant.tradinglib.StatsTypeCombination;
import com.strategyquant.tradinglib.ValueTypes;

/**
 * Hobbiecode - desviacion tipica (muestral, n-1) del resultado de las operaciones ganadoras (P/L > 0).
 */
public class HCStdDevWin extends DatabankColumn {

	public HCStdDevWin() {
		super(L.tsq("HC StdDev Win"), DatabankColumn.Decimal2PL, ValueTypes.Minimize, 0, 0, 200);

		setTooltip(L.tsq("Sample standard deviation of winning trades P/L (P/L > 0)"));
	}

	//------------------------------------------------------------------------

	@Override
	public double compute(SQStats stats, StatsTypeCombination combination, OrdersList ordersList, SettingsMap settings, SQStats statsLong, SQStats statsShort) throws Exception {
		double sum = 0, sumSq = 0;
		int count = 0;

		for(int i = 0; i < ordersList.size(); i++) {
			Order order = ordersList.get(i);
			if(order.isBalanceOrder()) continue;

			double pl = getPLByStatsType(order, combination);
			if(pl > 0) {
				sum += pl;
				sumSq += pl * pl;
				count++;
			}
		}

		if(count < 2) return 0;

		double mean = sum / count;
		double variance = (sumSq - count * mean * mean) / (count - 1);

		return round2(Math.sqrt(Math.max(variance, 0)));
	}
}
