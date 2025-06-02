package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.pt.routes.TransitPassengerRoute;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SBBTripScoring implements SumScoringFunction.TripScoring {

    private final SBBScoringParameters params;
    private final Set<String> ptModes;
    private final HashSet<String> modesAlreadyConsideredForDailyConstants;
    private double score = 0.0;
    private final List<Double> scoresPerTrip = new ArrayList<>();

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
            String mostExpensivePTSubmode =
                    findMostExpensivePTSubMode(trip.getLegsOnly());
            if (mostExpensivePTSubmode == null) {
                mostExpensivePTSubmode = routingMode;
            }
            tripScore += params.getMatsimScoringParameters().modeParams.get(mostExpensivePTSubmode).constant;
        } else {
            tripScore += params.getMatsimScoringParameters().modeParams.get(routingMode).constant;
        }
        for (Leg leg : trip.getLegsOnly()) {
            var modeParams = params.getMatsimScoringParameters().modeParams.get(leg.getMode());
            tripScore += (modeParams.marginalUtilityOfDistance_m + modeParams.monetaryDistanceCostRate * params.getMatsimScoringParameters().marginalUtilityOfMoney) * leg.getRoute().getDistance();
            double travelTime = leg.getTravelTime().seconds();
            if (leg.getRoute() instanceof TransitPassengerRoute transitPassengerRoute) {
                double waitingTime = transitPassengerRoute.getBoardingTime().or(leg.getDepartureTime()).seconds() - leg.getDepartureTime().seconds();
                travelTime = -waitingTime;
                tripScore += params.getMatsimScoringParameters().marginalUtilityOfWaitingPt_s * waitingTime;
            }
            tripScore += modeParams.marginalUtilityOfTraveling_s * (travelTime);

            if (!modesAlreadyConsideredForDailyConstants.contains(leg.getMode())) {
                tripScore += modeParams.dailyMoneyConstant * params.getMatsimScoringParameters().marginalUtilityOfMoney + modeParams.dailyUtilityConstant;
                modesAlreadyConsideredForDailyConstants.add(leg.getMode());
            }
        }
        this.scoresPerTrip.add(tripScore);
        score += tripScore;

    }

    private String findMostExpensivePTSubMode(List<Leg> legsOnly) {
        String mostExpensivePTSubMode = null;
        double maxCost = Double.MAX_VALUE;
        for (Leg leg : legsOnly) {
            if (ptModes.contains(leg.getMode())) {
                if (params.getMatsimScoringParameters().modeParams.get(leg.getMode()).constant < maxCost) {
                    maxCost = params.getMatsimScoringParameters().modeParams.get(leg.getMode()).constant;
                    mostExpensivePTSubMode = leg.getMode();
                }
            }
        }
        return mostExpensivePTSubMode;
    }

    @Override
    public void finish() {

    }

    @Override
    public double getScore() {
        return score;
    }

    @Override
    public void explainScore(StringBuilder out) {
        out.append("Score per Trip: " + scoresPerTrip);
        SumScoringFunction.TripScoring.super.explainScore(out);
    }
}
