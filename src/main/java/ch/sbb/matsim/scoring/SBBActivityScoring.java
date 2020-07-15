package ch.sbb.matsim.scoring;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.pt.PtConstants;

/**
 * @author mrieser
 */
public class SBBActivityScoring implements SumScoringFunction.ActivityScoring {

	private final CharyparNagelActivityScoring delegate;

	public SBBActivityScoring(ScoringParameters params) {
		this.delegate = new CharyparNagelActivityScoring(params);
	}

	@Override
	public void handleFirstActivity(Activity act) {
		if (!PtConstants.TRANSIT_ACTIVITY_TYPE.equals(act.getType())) {
			this.delegate.handleFirstActivity(act);
		}
	}

	@Override
	public void handleActivity(Activity act) {
		if (!PtConstants.TRANSIT_ACTIVITY_TYPE.equals(act.getType())) {
			this.delegate.handleActivity(act);
		}
	}

	@Override
	public void handleLastActivity(Activity act) {
		if (!PtConstants.TRANSIT_ACTIVITY_TYPE.equals(act.getType())) {
			this.delegate.handleLastActivity(act);
		}
	}

	@Override
	public void finish() {
		this.delegate.finish();
	}

	@Override
	public double getScore() {
		return this.delegate.getScore();
	}
}
