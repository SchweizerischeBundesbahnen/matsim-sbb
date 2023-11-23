package ch.sbb.matsim.scoring;

import java.util.Set;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * @author mrieser
 */
public class SBBTransferScoring implements SumScoringFunction.TripScoring {

	private final SBBScoringParameters params;
	private final Set<String> ptModes;
	private final TransitSchedule transitSchedule;
	private double score = 0.0;

	public SBBTransferScoring(SBBScoringParameters params, Set<String> ptModes, TransitSchedule transitSchedule) {
		this.params = params;
		this.ptModes = ptModes;
		this.transitSchedule = transitSchedule;
	}

	@Override
	public void handleTrip(TripStructureUtils.Trip trip) {
		OptionalTime departureTime = OptionalTime.undefined();
		double arrivalTime = 0;
		int transferCount = 0;
		boolean prevIsRail = false;
		boolean isRail;
		boolean firstScoring = true;

		for (Leg leg : trip.getLegsOnly()) {
			String legMode = leg.getMode();
			boolean isTransit = this.ptModes.contains(legMode);
			if (isTransit) {
				isRail = transitSchedule.getTransitLines().get(((TransitPassengerRoute) leg.getRoute()).getLineId()).getRoutes().get(((TransitPassengerRoute) leg.getRoute()).getRouteId()).getTransportMode().equals("rail");
				if (!departureTime.isUndefined() & (isRail != prevIsRail)) {
					double travelTime = arrivalTime - departureTime.seconds();
					this.scoreTransitTrip(travelTime, transferCount);
					if (!firstScoring) {
						this.score += params.getTransferUtilityRailOePNV();
					}
					firstScoring = false;
					transferCount = 0;
					departureTime = leg.getDepartureTime();
				} else {
					if (departureTime.isUndefined()) {
						departureTime = leg.getDepartureTime();
					} else {
						transferCount++;
					}
				}
				arrivalTime = leg.getDepartureTime().seconds() + leg.getTravelTime().seconds();
				prevIsRail = isRail;
			}
		}
		if (transferCount > 0) {
			double travelTime = arrivalTime - departureTime.seconds();
			this.scoreTransitTrip(travelTime, transferCount);
			if (!firstScoring) {
				this.score += params.getTransferUtilityRailOePNV();
			}
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
