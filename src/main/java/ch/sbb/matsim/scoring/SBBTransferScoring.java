package ch.sbb.matsim.scoring;

import ch.sbb.matsim.analysis.travelcomponents.Activity;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.PtConstants;

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
        boolean isTransitTrip = false;
        int transferCount = 0;
        double departureTime = Time.getUndefinedTime();
        double arrivalTime = Time.getUndefinedTime();
        boolean isInTransit = false;
        boolean lastWasTransfer = false;

        for (PlanElement pe : trip.getTripElements()) {
            if (pe instanceof Activity) {
                Activity act = (Activity) pe;
                if (isInTransit && act.getType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE)) {
                    transferCount++;
                    lastWasTransfer = true;
                }
            }
            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                String legMode = leg.getMode();
                boolean isTransit = TransportMode.transit_walk.equals(legMode) || this.ptModes.contains(legMode);
                if (isTransit) {
                    if (!isInTransit) {
                        departureTime = leg.getDepartureTime();
                    }
                    arrivalTime = leg.getDepartureTime() + leg.getTravelTime();
                    lastWasTransfer = false;
                }
            }
        }

        if (isTransitTrip) {
            if (lastWasTransfer) {
                transferCount--;
            }
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
