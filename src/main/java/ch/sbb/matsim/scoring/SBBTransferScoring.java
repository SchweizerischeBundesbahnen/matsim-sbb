package ch.sbb.matsim.scoring;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.utils.misc.Time;

import java.util.Set;

/**
 * @author mrieser
 */
public class SBBTransferScoring implements SumScoringFunction.TripScoring {

    private final SBBScoringParameters params;
    private final Set<String> ptModes;
    private double score = 0.0;

    public SBBTransferScoring(SBBScoringParameters params, Set<String> ptModes) {
        this.params = params;
        this.ptModes = ptModes;
    }

    @Override
    public void handleTrip(TripStructureUtils.Trip trip) {
        double departureTime = Time.getUndefinedTime();
        double arrivalTime = 0;
        int transitLegsCount = 0;

        for (Leg leg : trip.getLegsOnly()) {
            String legMode = leg.getMode();
            boolean isTransit = this.ptModes.contains(legMode);
            if (isTransit) {
                transitLegsCount++;
                if (Time.isUndefinedTime(departureTime)) {
                    departureTime = leg.getDepartureTime();
                }
                arrivalTime = leg.getDepartureTime() + leg.getTravelTime();
            }
        }
        if (transitLegsCount > 1) {
            int transferCount = transitLegsCount - 1;
            double travelTime = arrivalTime - departureTime;
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
