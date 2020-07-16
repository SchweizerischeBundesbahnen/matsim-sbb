package ch.sbb.matsim.scoring;

import ch.sbb.matsim.events.ParkingCostEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.scoring.SumScoringFunction;

/**
 * @author mrieser
 */
public class SBBParkingCostScoring implements SumScoringFunction.ArbitraryEventScoring {

	private final double marginalUtilityOfParkingPrice_util_money;
	private double score = 0.0;

	public SBBParkingCostScoring(double marginalUtilityOfParkingPrice_util_money) {
		this.marginalUtilityOfParkingPrice_util_money = marginalUtilityOfParkingPrice_util_money;
	}

	@Override
	public void handleEvent(Event event) {
		if (event instanceof ParkingCostEvent) {
			ParkingCostEvent pce = (ParkingCostEvent) event;
			double monetaryAmount = pce.getMonetaryAmount();
			double scoreDelta = monetaryAmount * this.marginalUtilityOfParkingPrice_util_money;
			this.score += scoreDelta;
		}
	}

	@Override
	public void finish() {
	}

	@Override
	public double getScore() {
		return this.score;
	}
}
