package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.ScenarioConfigGroup;
import org.matsim.core.scoring.functions.ScoringParameters;

/**
 * @author mrieser
 */
public class SBBScoringParameters {

    private final ScoringParameters matsimScoringParameters;

    private final double marginalUtilityOfParkingPrice;
    private final double transferUtilityPerTravelTime_utilsPerHour;

    private SBBScoringParameters(ScoringParameters matsimScoringParameters, double marginalUtilityOfParkingPrice, double transferUtilityPerTravelTime_utilsPerHour) {
        this.matsimScoringParameters = matsimScoringParameters;
        this.marginalUtilityOfParkingPrice = marginalUtilityOfParkingPrice;
        this.transferUtilityPerTravelTime_utilsPerHour = transferUtilityPerTravelTime_utilsPerHour;
    }

    public static final class Builder {
        private ScoringParameters.Builder matsimBuilder;
        private double marginalUtilityOfParkingPrice;
        private double transferUtilityPerTravelTime;

        public Builder(final PlanCalcScoreConfigGroup configGroup,
                       final PlanCalcScoreConfigGroup.ScoringParameterSet scoringParameterSet,
                       final ScenarioConfigGroup scenarioConfig,
                       final SBBBehaviorGroupsConfigGroup sbbConfig) {
            this.matsimBuilder = new ScoringParameters.Builder(configGroup, scoringParameterSet, scenarioConfig);
            this.marginalUtilityOfParkingPrice = sbbConfig.getMarginalUtilityOfParkingPrice();
            this.transferUtilityPerTravelTime = sbbConfig.getTransferUtilityPerTravelTime_utils_hr();
        }

        public void setMarginalUtilityOfParkingPrice(double marginalUtilityOfParkingPrice) {
            this.marginalUtilityOfParkingPrice = marginalUtilityOfParkingPrice;
        }

        public void setTransferUtilityPerTravelTime(double transferUtilityPerTravelTime) {
            this.transferUtilityPerTravelTime = transferUtilityPerTravelTime;
        }

        public ScoringParameters.Builder getMatsimScoringParametersBuilder() {
            return this.matsimBuilder;
        }

        public SBBScoringParameters build() {
            return new SBBScoringParameters(
              this.matsimBuilder.build(),
              this.marginalUtilityOfParkingPrice,
              this.transferUtilityPerTravelTime
            );
        }
    }

    public ScoringParameters getMatsimScoringParameters() {
        return this.matsimScoringParameters;
    }

    public double getMarginalUtilityOfParkingPrice() {
        return this.marginalUtilityOfParkingPrice;
    }

    public double getTransferUtilityPerTravelTime_utilsPerHour() {
        return this.transferUtilityPerTravelTime_utilsPerHour;
    }
}
