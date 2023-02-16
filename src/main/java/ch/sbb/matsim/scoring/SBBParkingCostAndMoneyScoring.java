/* *********************************************************************** *
 * project: org.matsim.*
 * CharyparNagelOpenTimesScoringFunctionFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.scoring;

import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.ScoringParameters;

/**
 * An adaption of the arbitrary money scoring function that takes into account a different utility of money for parking prices.
 * (I am not entirely sure why we assume this.)
 */
public final class SBBParkingCostAndMoneyScoring implements SumScoringFunction.ArbitraryEventScoring {

	private final double marginalUtilityOfMoneyParkingCost;
	private final double marginalUtilityOfMoney;
	private double score;

	public SBBParkingCostAndMoneyScoring(final ScoringParameters params, final double marginalUtilityOfMoneyParkingCost) {
		this.marginalUtilityOfMoney = params.marginalUtilityOfMoney;
		this.marginalUtilityOfMoneyParkingCost = marginalUtilityOfMoneyParkingCost;
	}


	@Override
	public void finish() {
	}

	@Override
	public double getScore() {
		return this.score;
	}

	@Override
	public void handleEvent(Event event) {
		if (event instanceof PersonMoneyEvent moneyEvent) {
			double scoreDelta;
			if (moneyEvent.getPurpose().endsWith("parking cost")) {
				scoreDelta = -1.0 * Math.abs(moneyEvent.getAmount() * this.marginalUtilityOfMoneyParkingCost);
				//in some versions of sbb scoring excel, marginal utility of money for parking cost is negative,
				// whereas the marginal utility of money is positive.
			} else {
				scoreDelta = moneyEvent.getAmount() * this.marginalUtilityOfMoney;
			}
			this.score += scoreDelta;
		}
	}
}
