/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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
package ch.sbb.matsim.routing;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;


/**
 * @author smetzler, dziemke
 */
public final class BicycleTravelDisutilityFactory implements TravelDisutilityFactory {

	private static final Logger LOG = LogManager.getLogger(BicycleTravelDisutilityFactory.class);

	@Inject	PlanCalcScoreConfigGroup cnScoringGroup;
	@Inject	PlansCalcRouteConfigGroup plansCalcRouteConfigGroup;
	
	private static int normalisationWrnCnt = 0;

	/* package-private */
	public BicycleTravelDisutilityFactory(){}
	
	@Override
	public TravelDisutility createTravelDisutility(TravelTime timeCalculator) {
		double sigma = plansCalcRouteConfigGroup.getRoutingRandomness();
		
		double normalization = 1;
		if ( sigma != 0. ) {
			normalization = 1. / Math.exp(sigma * sigma / 2);
			if (normalisationWrnCnt < 10) {
				normalisationWrnCnt++;
				LOG.info(" sigma: " + sigma + "; resulting normalization: " + normalization);
			}
		}
		return new BicycleTravelDisutility(cnScoringGroup, plansCalcRouteConfigGroup, timeCalculator, normalization);
	}
}