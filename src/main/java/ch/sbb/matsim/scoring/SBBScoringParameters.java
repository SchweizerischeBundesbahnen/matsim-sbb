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
	private final double transferUtilityBase;
	private final double transferUtilityPerTravelTime_utilsPerHour;
	private final double transferUtilityMinimum;
	private final double transferUtilityMaximum;

	private SBBScoringParameters(ScoringParameters matsimScoringParameters, double marginalUtilityOfParkingPrice,
			double transferUtlityBase, double transferUtilityPerTravelTime_utilsPerHour,
			double transferUtilityMinimum, double transferUtilityMaximum) {
		this.matsimScoringParameters = matsimScoringParameters;
		this.marginalUtilityOfParkingPrice = marginalUtilityOfParkingPrice;
		this.transferUtilityBase = transferUtlityBase;
		this.transferUtilityPerTravelTime_utilsPerHour = transferUtilityPerTravelTime_utilsPerHour;
		this.transferUtilityMinimum = transferUtilityMinimum;
		this.transferUtilityMaximum = transferUtilityMaximum;
	}

	public ScoringParameters getMatsimScoringParameters() {
		return this.matsimScoringParameters;
	}

	public double getMarginalUtilityOfParkingPrice() {
		return this.marginalUtilityOfParkingPrice;
	}

	public double getBaseTransferUtility() {
		return this.transferUtilityBase;
	}

	public double getTransferUtilityPerTravelTime_utilsPerHour() {
		return this.transferUtilityPerTravelTime_utilsPerHour;
	}

	public double getMinimumTransferUtility() {
		return this.transferUtilityMinimum;
	}

	public double getMaximumTransferUtility() {
		return this.transferUtilityMaximum;
	}

	public static final class Builder {

        private final ScoringParameters.Builder matsimBuilder;
        private double marginalUtilityOfParkingPrice;
        private double transferUtilityPerTravelTime;
        private double transferUtilityBase;
        private final double transferUtilityMinimum;
        private final double transferUtilityMaximum;

        public Builder(final PlanCalcScoreConfigGroup configGroup,
                final PlanCalcScoreConfigGroup.ScoringParameterSet scoringParameterSet,
                final ScenarioConfigGroup scenarioConfig,
                final SBBBehaviorGroupsConfigGroup sbbConfig) {
            this.matsimBuilder = new ScoringParameters.Builder(configGroup, scoringParameterSet, scenarioConfig);
            this.marginalUtilityOfParkingPrice = sbbConfig.getMarginalUtilityOfParkingPrice();
            this.transferUtilityPerTravelTime = sbbConfig.getTransferUtilityPerTravelTime_utils_hr();
            this.transferUtilityBase = sbbConfig.getBaseTransferUtility();
            this.transferUtilityMinimum = Math.min(sbbConfig.getMinimumTransferUtility(), sbbConfig.getMaximumTransferUtility());
			this.transferUtilityMaximum = Math.max(sbbConfig.getMinimumTransferUtility(), sbbConfig.getMaximumTransferUtility());
		}

		public void setMarginalUtilityOfParkingPrice(double marginalUtilityOfParkingPrice) {
			this.marginalUtilityOfParkingPrice = marginalUtilityOfParkingPrice;
		}

		public void setTransferUtilityPerTravelTime(double transferUtilityPerTravelTime) {
			this.transferUtilityPerTravelTime = transferUtilityPerTravelTime;
		}

		public void setTransferUtilityBase(double transferUtilityBase) {
			this.transferUtilityBase = transferUtilityBase;
		}

		public ScoringParameters.Builder getMatsimScoringParametersBuilder() {
			return this.matsimBuilder;
		}

		public SBBScoringParameters build() {
			return new SBBScoringParameters(
					this.matsimBuilder.build(),
					this.marginalUtilityOfParkingPrice,
					this.transferUtilityBase,
					this.transferUtilityPerTravelTime,
					this.transferUtilityMinimum,
					this.transferUtilityMaximum
			);
		}
	}
}
