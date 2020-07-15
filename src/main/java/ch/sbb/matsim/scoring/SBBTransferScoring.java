package ch.sbb.matsim.scoring;

import java.util.Set;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.utils.misc.OptionalTime;

/**
 * @author mrieser
 */
public class SBBTransferScoring implements SumScoringFunction.TripScoring {

    private final SBBScoringParameters params;
    private final Set<String> ptModes;
    private final Set<String> ptFeederModes;
    private double score = 0.0;

    public SBBTransferScoring(SBBScoringParameters params, Set<String> ptModes, Set<String> ptFeederModes) {
        this.params = params;
        this.ptModes = ptModes;
        this.ptFeederModes = ptFeederModes;
    }

    @Override
    public void handleTrip(TripStructureUtils.Trip trip) {
		OptionalTime departureTime = OptionalTime.undefined();
		double arrivalTime = 0;
		int transitLegsCount = 0;

		for (Leg leg : trip.getLegsOnly()) {
			String legMode = leg.getMode();
			boolean isTransit = this.ptModes.contains(legMode) || this.ptFeederModes.contains(legMode);
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
