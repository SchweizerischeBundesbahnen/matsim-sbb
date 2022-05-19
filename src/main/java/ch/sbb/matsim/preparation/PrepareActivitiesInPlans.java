package ch.sbb.matsim.preparation;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;

public class PrepareActivitiesInPlans {

	public static void main(String[] args) {
		String pathIn = args[0];
		String configIn = args[1];
		String pathOut = args[2];
		String configOut = args[3];

		Config config = RunSBB.buildConfig(configIn);
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(pathIn);

		overwriteActivitiesInPlans(scenario.getPopulation());
		ActivityParamsBuilder.buildActivityParams(config);

		new ConfigWriter(config).write(configOut);
		new PopulationWriter(scenario.getPopulation()).write(pathOut);
	}

	public static void overwriteActivitiesInPlans(Population population) {
		for (Person p : population.getPersons().values()) {
			if (PopulationUtils.getSubpopulation(p).equals(Variables.REGULAR)) {
				for (Plan plan : p.getPlans()) {
					List<Activity> activities = TripStructureUtils.getActivities(plan, StageActivityHandling.ExcludeStageActivities);
					Activity firstAct = activities.get(0);
					Activity lastAct = activities.get(activities.size() - 1);

					for (Activity act : activities) {
						if (act == firstAct) {
							continue;
						}
						if (act == lastAct) {
							processOvernightAct(firstAct, lastAct);
							break;
						}

						double endTime = act.getEndTime().orElse(36.0 * 3600);
						double startTime = act.getStartTime().orElse(0.0);

						double duration = endTime - startTime;

						if (act.getType().equals(SBBActivities.home)) {
							if (startTime >= (16.0 * 3600) && startTime < (19.0 * 3600)) {
								long ii = roundSecondsToMinInterval(duration, 60);

								double hours = startTime / 3600 * 1;
								double yy = ((int) (hours + 0.25)) / 1.0;
								String type = SBBActivities.home + "_" + ii + "_" + yy;
								act.setType(type);
							} else {
								long ii = roundSecondsToMinInterval(duration, 60);
								String type = SBBActivities.home + "_" + ii;
								act.setType(type);
							}
						} else if (act.getType().equals(SBBActivities.work)) {
							// process morning peak (between 6.5am and 8.5am)
							if (startTime >= (6.25 * 3600) && startTime <= (8.25 * 3600)) {
								long ii = roundSecondsToMinInterval(duration, 60);
								String type = SBBActivities.work + "_" + ii + "_mp";
								act.setType(type);
							}
							// process noon peak (between 12pm and 2pm)
							else if (startTime >= (12.5 * 3600) && startTime <= (13.75 * 3600)) {
								long ii = roundSecondsToMinInterval(duration, 60);
								String type = SBBActivities.work + "_" + ii + "_np";
								act.setType(type);
							}
							// process rest
							else {
								long ii = roundSecondsToMinInterval(duration, 60);
								String type = SBBActivities.work + "_" + ii;
								act.setType(type);
							}
						} else if (act.getType().equals(SBBActivities.education)) {
							// process morning peak (between 7am and 9am)
							if (startTime >= (6.75 * 3600) && startTime <= (8.5 * 3600)) {
								long ii = roundSecondsToMinInterval(duration, 60);
								String type = SBBActivities.education + "_" + ii + "_mp";
								act.setType(type);
							}
							// process noon peak (between 12.5pm and 2pm)
							else if (startTime >= (12.5 * 3600) && startTime <= (13.5 * 3600)) {
								long ii = roundSecondsToMinInterval(duration, 60);
								String type = SBBActivities.education + "_" + ii + "_np";
								act.setType(type);
							}
							// process rest
							else {
								long ii = roundSecondsToMinInterval(duration, 60);
								String type = SBBActivities.education + "_" + ii;
								act.setType(type);
							}
						} else if (act.getType().equals(SBBActivities.business)) {
							long ii = roundSecondsToMinInterval(duration, 60);
							String type = SBBActivities.business + "_" + ii;
							act.setType(type);
						} else if (act.getType().equals(SBBActivities.leisure)) {
							long ii = roundSecondsToMinInterval(duration, 30);
							String type = SBBActivities.leisure + "_" + ii;
							act.setType(type);
						} else if (act.getType().equals(SBBActivities.other)) {
							long ii = roundSecondsToMinInterval(duration, 30);
							String type = SBBActivities.other + "_" + ii;
							act.setType(type);
						} else if (act.getType().equals(SBBActivities.shopping)) {
							long ii = roundSecondsToMinInterval(duration, 30);
							String type = SBBActivities.shopping + "_" + ii;
							act.setType(type);
						} else if (act.getType().equals(SBBActivities.accompany)) {
							long ii = roundSecondsToMinInterval(duration, 30);
							String type = SBBActivities.accompany + "_" + ii;
							act.setType(type);
						}
					}
				}
			}
		}
	}

	private static void processOvernightAct(Activity firstAct, Activity lastAct) {
		if (firstAct.getType().equals(SBBActivities.home) && lastAct.getType().equals(SBBActivities.home)) {
			double lastActStartTime = lastAct.getStartTime().seconds();
			double totDuration = (firstAct.getEndTime().seconds() + 24 * 3600.0) - lastAct.getStartTime().seconds();
			if (totDuration <= 0) {
				totDuration = 1;
			}

			if (lastActStartTime >= (16.0 * 3600) && lastActStartTime < (19.0 * 3600)) {
				long ii = roundSecondsToMinInterval(totDuration, 60);

				double hours = lastActStartTime / 3600 * 1;
				double yy = ((int) hours) / 1.0 + 1.0;

				String type = SBBActivities.home + "_" + ii + "_" + yy;
				firstAct.setType(type);
				lastAct.setType(type);
			} else {
				long ii = roundSecondsToMinInterval(totDuration, 60);
				String type = SBBActivities.home + "_" + ii;
				firstAct.setType(type);
				lastAct.setType(type);
			}
		}
	}

	private static long roundSecondsToMinInterval(double seconds, int interval) {
		double minutes = seconds / 60;
		double toRound = minutes / interval;
		long rounded = (int) Math.ceil(toRound);
		return (rounded * interval);
	}
}
