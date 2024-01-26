package ch.sbb.matsim.scoring;

import java.util.List;
import java.util.Set;

import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import static java.util.EnumSet.range;

/**
 * @author mrieser
 */
public class SBBTransferScoring implements SumScoringFunction.TripScoring {

	private final SBBScoringParameters params;
	private final Set<String> ptModes;
	private final TransitSchedule transitSchedule;
	private final RaptorStaticConfig raptorStaticConfig;
	private double score = 0.0;

	public SBBTransferScoring(SBBScoringParameters params, Set<String> ptModes, TransitSchedule transitSchedule, RaptorStaticConfig raptorStaticConfig) {
		this.params = params;
		this.ptModes = ptModes;
		this.transitSchedule = transitSchedule;
		this.raptorStaticConfig = raptorStaticConfig;
	}

	@Override
	public void handleTrip(TripStructureUtils.Trip trip) {
		OptionalTime departureTime = OptionalTime.undefined();
		double penalty = 0;
		int first = 0;
		if (this.raptorStaticConfig.isUseModeToModeTransferPenalty()) {
			String mode = null;
			String nextMode = null;
			List<Leg> legs = trip.getLegsOnly();
			int i = 0;
			for (Leg leg : legs) {
				if (this.ptModes.contains(leg.getMode())) {
					nextMode = transitSchedule.getTransitLines().get(((TransitPassengerRoute) leg.getRoute()).getLineId()).getRoutes().get(((TransitPassengerRoute) leg.getRoute()).getRouteId()).getTransportMode();
					if (mode != null) {
						penalty = this.raptorStaticConfig.getModeToModeTransferPenalty(mode, nextMode);
						if (penalty != 0) {
							scoreTripPart(departureTime, legs.subList(first, i));
							this.score += penalty;
							first = i;
							departureTime = leg.getDepartureTime();
						}
					}
					mode = nextMode;
				}
				i++;
			}
			scoreTripPart(departureTime, legs.subList(first, i));
		} else {
			scoreTripPart(departureTime, trip.getLegsOnly());
		}
	}

	private void scoreTripPart(OptionalTime departureTime, List<Leg> legs) {
		int transitLegsCount = 0;
		double arrivalTime = 0;
		for (Leg leg: legs) {
			String legMode = leg.getMode();
			boolean isTransit = this.ptModes.contains(legMode);
			if (isTransit) {
				transitLegsCount++;
				if (departureTime.isUndefined()) {
					departureTime = leg.getDepartureTime();
				}
				arrivalTime = leg.getDepartureTime().seconds() + leg.getTravelTime().seconds();
			}
		}
		if (transitLegsCount > 1) {
			int transferCount = transitLegsCount - 1;
			double travelTime = arrivalTime - departureTime.seconds();
			this.scoreTransitTrip(travelTime, transferCount);
		}

	}
	private void scoreTransitTrip(double travelTime, int transferCount) {
		double partialScore = this.params.getBaseTransferUtility() + (travelTime / 3600) * this.params.getTransferUtilityPerTravelTime_utilsPerHour();
		if (partialScore < this.params.getMinimumTransferUtility()) {
			partialScore = this.params.getMinimumTransferUtility();
		}
		if (partialScore > this.params.getMaximumTransferUtility()) {
			partialScore = this.params.getMaximumTransferUtility();
		}
		this.score += partialScore * transferCount;
	}

	@Override
	public void finish() {
	}

	@Override
	public double getScore() {
		return this.score;
	}
}
