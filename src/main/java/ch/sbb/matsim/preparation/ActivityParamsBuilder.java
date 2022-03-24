package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;

public class ActivityParamsBuilder {

	public static void buildActivityParams(Config config) {
		buildStageActivityModeParams(config);

		//exogeneous activities
		{
			String type = SBBActivities.exogeneous;
			final ActivityParams params = new ActivityParams(type);
			params.setMinimalDuration(10 * 60.0);
			params.setTypicalDuration(40 * 60.0);
			params.setScoringThisActivityAtAll(false);
			config.planCalcScore().addActivityParams(params);
		}
		{
			String type = SBBActivities.freight;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(12 * 3600.0);
			params.setScoringThisActivityAtAll(false);
			config.planCalcScore().addActivityParams(params);
		}

		{
			String type = SBBActivities.cbHome;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(12 * 3660.0);
			params.setScoringThisActivityAtAll(false);
			config.planCalcScore().addActivityParams(params);
		}

		// home
		for (long ii = 30; ii <= 1440; ii += 30) {
			String type = SBBActivities.home + "_" + ii;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		for (long ii = 30; ii <= 1440; ii += 30) {
			for (double yy = 16; yy <= 20; yy += 0.5) {
				String type = SBBActivities.home + "_" + ii + "_" + yy;
				final ActivityParams params = new ActivityParams(type);
				params.setTypicalDuration(ii * 60.0);
				params.setLatestStartTime(yy * 3600.0);
				params.setScoringThisActivityAtAll(true);
				config.planCalcScore().addActivityParams(params);
			}
		}

		// work
		for (long ii = 30; ii <= 960; ii += 30) {
			String type = SBBActivities.work + "_" + ii;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(4 * 3600.0);
			params.setClosingTime(23 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		for (long ii = 30; ii <= 960; ii += 30) {
			// morning peak is assumed to be between 7am and 8am
			String type = SBBActivities.work + "_" + ii + "_mp";
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(6.45 * 3600.0);
			params.setLatestStartTime(7.45 * 3600.0);
			params.setClosingTime(23 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		for (long ii = 30; ii <= 960; ii += 30) {
			// noon peak is assumed to be between 12pm and 2pm
			String type = SBBActivities.work + "_" + ii + "_np";
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(12.5 * 3600.0);
			params.setLatestStartTime(13.75 * 3600.0);
			params.setClosingTime(23 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		// education
		for (long ii = 30; ii <= 960; ii += 30) {
			String type = SBBActivities.education + "_" + ii;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(6 * 3600.0);
			params.setClosingTime(21 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		for (long ii = 30; ii <= 960; ii += 30) {
			// morning peak is assumed to be between 7am and 9am
			String type = SBBActivities.education + "_" + ii + "_mp";
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(7.5 * 3600.0);
			params.setLatestStartTime(8.25 * 3600.0);
			params.setClosingTime(21 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		for (long ii = 30; ii <= 960; ii += 30) {
			// noon peak is assumed to be between 12.5pm and 2pm
			String type = SBBActivities.education + "_" + ii + "_np";
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(12.75 * 3600.0);
			params.setLatestStartTime(13.25 * 3600.0);
			params.setClosingTime(21 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		// business
		for (long ii = 30; ii <= 540; ii += 30) {
			String type = SBBActivities.business + "_" + ii;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(4 * 3600.0);
			params.setClosingTime(23 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		// leisure
		for (long ii = 15; ii <= 480; ii += 15) {
			String type = SBBActivities.leisure + "_" + ii;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(4 * 3600.0);
			params.setClosingTime(24 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		// shopping
		for (long ii = 15; ii <= 330; ii += 15) {
			String type = SBBActivities.shopping + "_" + ii;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setOpeningTime(5 * 3600.0);
			params.setClosingTime(22 * 3600.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		// accompany
		for (long ii = 10; ii <= 120; ii += 10) {
			String type = SBBActivities.accompany + "_" + ii;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}

		// other
		for (long ii = 30; ii <= 600; ii += 30) {
			String type = SBBActivities.other + "_" + ii;
			final ActivityParams params = new ActivityParams(type);
			params.setTypicalDuration(ii * 60.0);
			params.setScoringThisActivityAtAll(true);
			config.planCalcScore().addActivityParams(params);
		}
	}

	public static void buildStageActivityModeParams(Config config) {
		for (String stageActivityType : SBBActivities.stageActivityTypeList) {
			final ActivityParams params = new ActivityParams(stageActivityType);
			params.setScoringThisActivityAtAll(false);
			config.planCalcScore().addActivityParams(params);
		}
	}
}
