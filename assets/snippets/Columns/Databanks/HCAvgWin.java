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
 * Hobbiecode - media del resultado de las operaciones ganadoras (P/L > 0).
 */
public class HCAvgWin extends DatabankColumn {

	public HCAvgWin() {
		super(L.tsq("HC Avg. Win"), DatabankColumn.Decimal2PL, ValueTypes.Maximize, 0, 0, 200);

		setTooltip(L.tsq("Average P/L of winning trades (P/L > 0)"));
	}

	//------------------------------------------------------------------------

	@Override
	public double compute(SQStats stats, StatsTypeCombination combination, OrdersList ordersList, SettingsMap settings, SQStats statsLong, SQStats statsShort) throws Exception {
		double sum = 0;
		int count = 0;

		for(int i = 0; i < ordersList.size(); i++) {
			Order order = ordersList.get(i);
			if(order.isBalanceOrder()) continue;

			double pl = getPLByStatsType(order, combination);
			if(pl > 0) {
				sum += pl;
				count++;
			}
		}

		return round2(safeDivide(sum, count));
	}
}
