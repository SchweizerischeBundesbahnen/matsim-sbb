package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ScoringConfigGroup.ActivityParams;

public class ActivityParamsBuilder {

	public static void buildActivityParams(Config config) {
		buildStageActivityModeParams(config);

		// exogeneous
		{
			final ActivityParams params = new ActivityParams(SBBActivities.exogeneous);
			params.setMinimalDuration(10 * 60.0);
			params.setTypicalDuration(40 * 60.0);
			params.setScoringThisActivityAtAll(false);
			config.scoring().addActivityParams(params);
		}
		// freight
		{
			final ActivityParams params = new ActivityParams(SBBActivities.freight);
			params.setTypicalDuration(12 * 3600.0);
			params.setScoringThisActivityAtAll(false);
			config.scoring().addActivityParams(params);
		}
		// cbHome
		{
			final ActivityParams params = new ActivityParams(SBBActivities.cbHome);
			params.setTypicalDuration(12 * 3660.0);
			params.setScoringThisActivityAtAll(false);
			config.scoring().addActivityParams(params);
		}
		// home
		{
			final ActivityParams params = new ActivityParams(SBBActivities.home);
			params.setTypicalDuration(8 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.scoring().addActivityParams(params);
		}
		// work
		{
			final ActivityParams params = new ActivityParams(SBBActivities.work);
			params.setTypicalDuration(8 * 3600.0);
			params.setOpeningTime(4 * 3600.0);
			params.setClosingTime(23 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.scoring().addActivityParams(params);
		}
		// education
		{
			final ActivityParams params = new ActivityParams(SBBActivities.education);
			params.setTypicalDuration(6 * 3600.0);
			params.setOpeningTime(6 * 3600.0);
			params.setClosingTime(21 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.scoring().addActivityParams(params);
		}
		// business
		{
			final ActivityParams params = new ActivityParams(SBBActivities.business);
			params.setTypicalDuration(4 * 3600.0);
			params.setOpeningTime(4 * 3600.0);
			params.setClosingTime(23 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.scoring().addActivityParams(params);
		}
		// leisure
		{
			final ActivityParams params = new ActivityParams(SBBActivities.leisure);
			params.setTypicalDuration(2 * 3600.0);
			params.setOpeningTime(4 * 3600.0);
			params.setClosingTime(24 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.scoring().addActivityParams(params);
		}
		// shopping
		{
			final ActivityParams params = new ActivityParams(SBBActivities.shopping);
			params.setTypicalDuration(1 * 3600.0);
			params.setOpeningTime(5 * 3600.0);
			params.setClosingTime(22 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.scoring().addActivityParams(params);
		}
		// accompany
		{
			final ActivityParams params = new ActivityParams(SBBActivities.accompany);
			params.setTypicalDuration(30 * 60.0);
			params.setScoringThisActivityAtAll(true);
			config.scoring().addActivityParams(params);
		}
		// other
		{
			final ActivityParams params = new ActivityParams(SBBActivities.other);
			params.setTypicalDuration(1 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.scoring().addActivityParams(params);
		}
	}


	public static void buildStageActivityModeParams(Config config) {
		for (String stageActivityType : SBBActivities.stageActivityTypeList) {
			final ActivityParams params = new ActivityParams(stageActivityType);
			params.setScoringThisActivityAtAll(false);
			config.scoring().addActivityParams(params);
		}
	}
}
