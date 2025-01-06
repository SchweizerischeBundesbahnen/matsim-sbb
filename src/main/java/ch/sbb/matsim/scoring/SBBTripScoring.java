package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.SumScoringFunction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SBBTripScoring implements SumScoringFunction.TripScoring {

    private final SBBScoringParameters params;
    private final Set<String> ptModes;
    private final HashSet<String> modesAlreadyConsideredForDailyConstants;

    public SBBTripScoring(SBBScoringParameters params, Set<String> ptModes) {
        this.params = params;
        this.ptModes = new HashSet<>(ptModes);
        this.ptModes.add(SBBModes.PT);
        this.modesAlreadyConsideredForDailyConstants = new HashSet<>();

    }

    @Override
    public void handleTrip(TripStructureUtils.Trip trip) {
        String routingMode = trip.getLegsOnly().get(0).getRoutingMode();
        double tripScore = 0.;

        if (ptModes.contains(routingMode)) {
            String mostSignificantPtMode = findMostSignificantPTSubMode(trip.getLegsOnly());
            tripScore += params.getMatsimScoringParameters().modeParams.get(mostSignificantPtMode).constant;
        } else {
            tripScore += params.getMatsimScoringParameters().modeParams.get(routingMode).constant;
        }
        for (Leg leg : trip.getLegsOnly()) {
            var modeParams = params.getMatsimScoringParameters().modeParams.get(leg.getMode());
            tripScore += (modeParams.marginalUtilityOfDistance_m + modeParams.monetaryDistanceCostRate * params.getMatsimScoringParameters().marginalUtilityOfMoney) * leg.getRoute().getDistance();
            tripScore += modeParams.marginalUtilityOfTraveling_s * (leg.getTravelTime().seconds());

            if (!modesAlreadyConsideredForDailyConstants.contains(leg.getMode())) {
                tripScore += modeParams.dailyMoneyConstant * params.getMatsimScoringParameters().marginalUtilityOfMoney + modeParams.dailyUtilityConstant;
                modesAlreadyConsideredForDailyConstants.add(leg.getMode());
            }
        }
    }

    private String findMostSignificantPTSubMode(List<Leg> legsOnly) {
        String mostSignificantMode = null;
        double longestDistance = -1.;
        for (Leg leg : legsOnly) {
            double distance = leg.getRoute().getDistance();
            if (distance > longestDistance) {
                longestDistance = distance;
                mostSignificantMode = leg.getMode();
            }
        }
        return mostSignificantMode;
    }

    @Override
    public void finish() {

    }

    @Override
    public double getScore() {
        return 0;
    }

    @Override
    public void explainScore(StringBuilder out) {
        SumScoringFunction.TripScoring.super.explainScore(out);
    }
}
