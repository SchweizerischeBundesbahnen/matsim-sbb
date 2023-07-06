package ch.sbb.matsim.routing;

import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.router.util.LinkToLinkTravelTime;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

import java.util.Random;

public class BicycleTravelTimeAndDisutility implements TravelDisutility, TravelTime, LinkToLinkTravelTime {
	private static final Logger log = LogManager.getLogger(org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility.class);
	private final double marginalCostOfTimeS;
	private final double marginalCostOfDistanceM;
	private double marginalCostOfGradientM100M = 0.02;
	private final double sigma;
	private final double normalization;
	Person prevPerson;
	Random random;

	public BicycleTravelTimeAndDisutility(double marginalCostOfTimeS, double marginalCostOfDistanceM, double marginalCostOfGradientM100M, double sigma, double normalization) {
		this.sigma = sigma;
		this.marginalCostOfTimeS = marginalCostOfTimeS;
		this.marginalCostOfDistanceM = marginalCostOfDistanceM;
		this.marginalCostOfGradientM100M = marginalCostOfGradientM100M;
		this.normalization = normalization;
		this.random = sigma != 0 ? MatsimRandom.getLocalInstance() : null;
	}

	public BicycleTravelTimeAndDisutility(PlanCalcScoreConfigGroup cnScoringGroup, PlansCalcRouteConfigGroup plansCalcRouteConfigGroup) {
		this(-(cnScoringGroup.getModes().get(SBBModes.BIKE).getMarginalUtilityOfTraveling() / 3600.0) + cnScoringGroup.getPerforming_utils_hr() / 3600.0,-(cnScoringGroup.getModes().get(SBBModes.BIKE).getMonetaryDistanceRate() * cnScoringGroup.getMarginalUtilityOfMoney())
				- cnScoringGroup.getModes().get(SBBModes.BIKE).getMarginalUtilityOfDistance(), 0.02, plansCalcRouteConfigGroup.getRoutingRandomness(), 1);
	}

	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		double travelTime = getLinkTravelTime(link, time, person, vehicle);


		double distance = link.getLength();

		double travelTimeDisutility = marginalCostOfTimeS * travelTime;
		double distanceDisutility = marginalCostOfDistanceM * distance;

		double gradientFactor = getGradient(link);
		double gradientDisutility = marginalCostOfGradientM100M * gradientFactor * distance;

		// randomize if applicable:
		double normalRndLink = 1.;
		double logNormalRndDist = 1.;
		double logNormalRndGrad = 1.;
		if (sigma != 0.) {
			normalRndLink = 0.05 * random.nextGaussian();
			// yyyyyy are we sure that this is a good approach?  In high resolution networks, this leads to quirky detours ...  kai, sep'19
			prevPerson = person;

			logNormalRndDist = Math.exp(sigma * random.nextGaussian());
			logNormalRndGrad = Math.exp(sigma * random.nextGaussian());
			logNormalRndDist *= normalization;
			logNormalRndGrad *= normalization;
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
		double disutility = (1 + normalRndLink) * travelTimeDisutility + logNormalRndDist * distanceDisutility + logNormalRndGrad * gradientDisutility;
		// note that "normalRndLink" follows a Gaussian distribution, not a lognormal one as the others do!
		return disutility;
	}

	@Override
	public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
		double velocity = link.getFreespeed();
		if (vehicle!=null) {
			velocity = Math.min(velocity, vehicle.getType().getMaximumVelocity());
		}
		return link.getLength() / velocity / computeGradientFactor(link);
	}

	private double computeGradientFactor(Link link) {
		double factor = 1;
		if (link.getFromNode().getCoord().hasZ() && link.getToNode().getCoord().hasZ()) {
			double fromZ = link.getFromNode().getCoord().getZ();
			double toZ = link.getToNode().getCoord().getZ();
			if (toZ > fromZ) { // No positive speed increase for downhill, only decrease for uphill
				double reduction = 1 - 5 * ((toZ - fromZ) / link.getLength());
				factor = Math.max(0.1, reduction); // maximum reduction is 0.1
			}
		}

		return factor;
	}

	@Override
	public double getLinkToLinkTravelTime(Link fromLink, Link toLink, double time, Person person, Vehicle vehicle) {
		return this.getLinkTravelTime(fromLink, time, (Person)null, (Vehicle)null);
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

