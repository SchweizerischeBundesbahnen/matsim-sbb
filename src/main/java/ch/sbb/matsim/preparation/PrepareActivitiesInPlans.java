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
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PrepareActivitiesInPlans {

	static void main(String[] args) {
		String pathIn = args[0];
		String configIn = args[1];
		String pathOut = args[2];
		String configOut = args[3];

		Config config = RunSBB.buildConfig(configIn);
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(pathIn);

		overwriteActivitiesInPlans(scenario.getPopulation(), config);
		ActivityParamsBuilder.buildActivityParams(config);

		new ConfigWriter(config).write(configOut);
		new PopulationWriter(scenario.getPopulation()).write(pathOut);
	}

	public static void overwriteActivitiesInPlans(Population population, Config config) {
		Set<String> allActivityTypes = new HashSet<>();
		for (Person p : population.getPersons().values()) {
			if (PopulationUtils.getSubpopulation(p).equals(Variables.REGULAR)) {
				for (Plan plan : p.getPlans()) {
					List<Activity> activities = TripStructureUtils.getActivities(plan, StageActivityHandling.ExcludeStageActivities);
					Activity firstAct = activities.getFirst();
					Activity lastAct = activities.getLast();

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

						switch (act.getType()) {
							case SBBActivities.home,
								 SBBActivities.work,
								 SBBActivities.education,
								 SBBActivities.business,
								 SBBActivities.leisure,
								 SBBActivities.other,
								 SBBActivities.shopping,
								 SBBActivities.accompany ->
									act.getAttributes().putAttribute(
											ScoringConfigGroup.ActivityParams.TYPICAL_DURATION,
											duration);
						}
					}
					TripStructureUtils.getActivities(plan, StageActivityHandling.ExcludeStageActivities)
							.stream().map(Activity::getType).forEach(allActivityTypes::add);
				}
			}
		}

		var allTypesInConfig = config.scoring().getActivityTypes();
		Set<String> notFoundActivityTypes = allActivityTypes.stream()
				.filter(type -> !allTypesInConfig.contains(type))
				.collect(Collectors.toSet());
		if (!notFoundActivityTypes.isEmpty()) {
			throw new RuntimeException("Activity types are not in scoring: " + notFoundActivityTypes);
		}
	}

	private static void processOvernightAct(Activity firstAct, Activity lastAct) {
		if (firstAct.getType().equals(SBBActivities.home) && lastAct.getType().equals(SBBActivities.home)) {
			double totDuration = (firstAct.getEndTime().seconds() + 24 * 3600.0) - lastAct.getStartTime().seconds();
			if (totDuration <= 0) {
				totDuration = 1;
			}
			firstAct.getAttributes().putAttribute(ScoringConfigGroup.ActivityParams.TYPICAL_DURATION, totDuration);
			lastAct.getAttributes().putAttribute(ScoringConfigGroup.ActivityParams.TYPICAL_DURATION, totDuration);
		}
	}

}
