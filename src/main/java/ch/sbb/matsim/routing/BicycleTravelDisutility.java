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

import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

import java.util.Random;

/**
 * @author smetzler, dziemke
 * based on RandomizingTimeDistanceTravelDisutility and adding more components
 */
class BicycleTravelDisutility implements TravelDisutility {
	private static final Logger LOG = LogManager.getLogger(BicycleTravelDisutility.class);

	private final double marginalCostOfTimeS;
	private final double marginalCostOfDistanceM;

	private final double marginalCostOfGradientM100M;



	private final double normalization;
	private final double sigma;

	private final Random random;

	private final TravelTime timeCalculator;

	// "cache" of the random value
	private double normalRndLink;
	private double logNormalRndDist;
	private double logNormalRndInf;
	private double logNormalRndComf;
	private double logNormalRndGrad;
	private double logNormalRndUserDef;
	private Person prevPerson;


	BicycleTravelDisutility(PlanCalcScoreConfigGroup cnScoringGroup,
			PlansCalcRouteConfigGroup plansCalcRouteConfigGroup, TravelTime timeCalculator, double normalization) {
		final PlanCalcScoreConfigGroup.ModeParams bicycleParams = cnScoringGroup.getModes().get(SBBModes.BIKE);
		if (bicycleParams == null) {
			throw new NullPointerException("Mode " + SBBModes.BIKE + " is not part of the valid mode parameters " + cnScoringGroup.getModes().keySet());
		}		this.marginalCostOfDistanceM = -(bicycleParams.getMonetaryDistanceRate() * cnScoringGroup.getMarginalUtilityOfMoney())
				- bicycleParams.getMarginalUtilityOfDistance();


		this.marginalCostOfTimeS = -(bicycleParams.getMarginalUtilityOfTraveling() / 3600.0) + cnScoringGroup.getPerforming_utils_hr() / 3600.0;

		this.marginalCostOfGradientM100M = 0.02;

		this.timeCalculator = timeCalculator;

		this.normalization = normalization;
		this.sigma = plansCalcRouteConfigGroup.getRoutingRandomness();
		this.random = sigma != 0 ? MatsimRandom.getLocalInstance() : null;
	}

	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);



		double distance = link.getLength();

		double travelTimeDisutility = marginalCostOfTimeS * travelTime;
		double distanceDisutility = marginalCostOfDistanceM * distance;

		double gradientFactor = getGradient(link);
		double gradientDisutility = marginalCostOfGradientM100M * gradientFactor * distance;

		// randomize if applicable:
		if (sigma != 0.) {
			if (person==null) {
				throw new RuntimeException("you cannot use the randomzing travel disutility without person.  If you need this without a person, set"
						+ "sigma to zero.") ;
			}
			normalRndLink = 0.05 * random.nextGaussian();
			// yyyyyy are we sure that this is a good approach?  In high resolution networks, this leads to quirky detours ...  kai, sep'19
			if (person != prevPerson) {
				prevPerson = person;

				logNormalRndDist = Math.exp(sigma * random.nextGaussian());
				logNormalRndInf = Math.exp(sigma * random.nextGaussian());
				logNormalRndComf = Math.exp(sigma * random.nextGaussian());
				logNormalRndGrad = Math.exp(sigma * random.nextGaussian());
				logNormalRndUserDef = Math.exp(sigma * random.nextGaussian());
				logNormalRndDist *= normalization;
				logNormalRndInf *= normalization;
				logNormalRndComf *= normalization;
				logNormalRndGrad *= normalization;
				logNormalRndUserDef *= normalization;
				// this should be a log-normal distribution with sigma as the "width" parameter.   Instead of figuring out the "location"
				// parameter mu, I rather just normalize (which should be the same, see next). kai, nov'13

				/* The argument is something like this:<ul>
				 * <li> exp( mu + sigma * Z) with Z = Gaussian generates lognormal with mu and sigma.
				 * <li> The mean of this is exp( mu + sigma^2/2 ) .
				 * <li> If we set mu=0, the expectation value is exp( sigma^2/2 ) .
				 * <li> So in order to set the expectation value to one (which is what we want), we need to divide by exp( sigma^2/2 ) .
				 * </ul>
				 * Should be tested. kai, jan'14 */
			}
		} else {
			normalRndLink = 1.;
			logNormalRndDist = 1.;
			logNormalRndInf = 1.;
			logNormalRndComf = 1.;
			logNormalRndGrad = 1.;
			logNormalRndUserDef = 1.;
		}
		double disutility = (1 + normalRndLink) * travelTimeDisutility + logNormalRndDist * distanceDisutility + logNormalRndGrad * gradientDisutility;
		// note that "normalRndLink" follows a Gaussian distribution, not a lognormal one as the others do!
		return disutility;
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		return 0;
	}

	static double getGradient(Link link ) {

		if (!link.getFromNode().getCoord().hasZ() || !link.getToNode().getCoord().hasZ()) return 0.;

		var fromZ = link.getFromNode().getCoord().getZ();
		var toZ = link.getToNode().getCoord().getZ();
		var gradient = (toZ - fromZ) / link.getLength();
		// No positive utility for downhill, only negative for uphill
		return Math.max(0, gradient);
	}
}