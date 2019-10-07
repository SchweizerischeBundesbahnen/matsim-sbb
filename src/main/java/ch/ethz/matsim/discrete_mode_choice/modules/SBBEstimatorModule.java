package ch.ethz.matsim.discrete_mode_choice.modules;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

import com.google.inject.Provides;

import ch.ethz.matsim.discrete_mode_choice.components.estimators.MATSimTripScoringEstimator;
import ch.ethz.matsim.discrete_mode_choice.estimators.SBBTourEstimator;
import ch.ethz.matsim.discrete_mode_choice.model.estimation.CachedTripEstimator;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import ch.sbb.matsim.config.ParkingCostConfigGroup;
import ch.sbb.matsim.zones.ZonesCollection;

public class SBBEstimatorModule extends AbstractDiscreteModeChoiceExtension {
	
	public static final String SBB_SCORING = "SBBScoring";

	@Override
	public void installExtension() {

		bindTourEstimator(SBB_SCORING).to(SBBTourEstimator.class);
	}

	@Provides
	public SBBTourEstimator provideSBBTourEstimator(MATSimTripScoringEstimator tripEstimator,
			ScoringParametersForPerson scoringParametersForPerson, DiscreteModeChoiceConfigGroup dmcConfig,
			ParkingCostConfigGroup parkCostConfig,ZonesCollection zones,Scenario scenario) {
		return new SBBTourEstimator(new CachedTripEstimator(tripEstimator, dmcConfig.getCachedModes()),
				scoringParametersForPerson, parkCostConfig, zones,scenario);
	}
}