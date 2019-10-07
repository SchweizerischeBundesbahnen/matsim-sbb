package ch.ethz.matsim.discrete_mode_choice.modules;

import org.matsim.core.scoring.functions.ScoringParametersForPerson;

import com.google.inject.Provides;

import ch.ethz.matsim.discrete_mode_choice.components.estimators.MATSimTripScoringEstimator;
import ch.ethz.matsim.discrete_mode_choice.estimators.SBBTourEstimator;
import ch.ethz.matsim.discrete_mode_choice.model.estimation.CachedTripEstimator;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;

public class SBBEstimatorModule extends AbstractDiscreteModeChoiceExtension {
	
	public static final String SBB_SCORING = "SBBScoring";

	@Override
	public void installExtension() {

		bindTourEstimator(SBB_SCORING).to(SBBTourEstimator.class);
	}

	@Provides
	public SBBTourEstimator provideSBBTourEstimator(MATSimTripScoringEstimator tripEstimator,
			ScoringParametersForPerson scoringParametersForPerson, DiscreteModeChoiceConfigGroup dmcConfig) {
		return new SBBTourEstimator(new CachedTripEstimator(tripEstimator, dmcConfig.getCachedModes()),
				scoringParametersForPerson);
	}
}