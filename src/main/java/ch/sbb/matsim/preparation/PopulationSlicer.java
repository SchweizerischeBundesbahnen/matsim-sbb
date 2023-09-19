package ch.sbb.matsim.preparation;

import ch.sbb.matsim.preparation.cutter.BetterPopulationReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

/**
 * @author jbischoff / SBB
 */
public class PopulationSlicer {

	final Random random = MatsimRandom.getRandom();

	/*
	Randomly slices a population in n parts
	 */
	public static void main(String[] args) throws IOException {
		String inputPopulation = args[0];
		String inputFacilities = args[1];
		int slices = Integer.parseInt(args[2]);
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		if (!"-".equals(inputFacilities)) {
			new MatsimFacilitiesReader(scenario).readFile(inputFacilities);
		}
		BetterPopulationReader.readSelectedPlansOnly(scenario, new File(inputPopulation));
		String p = 100 / slices + "pct";
		var outputDir = Paths.get(inputPopulation.replace("plans.xml.gz", p));
		Files.createDirectory(outputDir);

		String outputFolder = outputDir.toAbsolutePath().toString();
		new PopulationSlicer().run(scenario, outputFolder, slices);

	}

	public void run(Scenario scenario, String outputFolder, int slices) {
		Population population = scenario.getPopulation();
		List<Id<Person>> personIds = new ArrayList<>(population.getPersons().keySet());
		Collections.shuffle(personIds, random);
		int partitionsize = personIds.size() / slices;

		for (int i = 0; i < slices; i++) {
			StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
			streamingPopulationWriter.startStreaming(outputFolder + "/plans_" + i + ".xml.gz");
			Set<Id<ActivityFacility>> usedFacilities = new HashSet<>();
			for (int j = 0; j < partitionsize; j++) {
				int personNo = i * partitionsize + j;
				Person person = population.getPersons().get(personIds.get(personNo));
				streamingPopulationWriter.run(person);
				usedFacilities.addAll(TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities).stream()
						.map(Activity::getFacilityId)
						.filter(Objects::nonNull)
						.collect(Collectors.toSet()));

			}
			streamingPopulationWriter.closeStreaming();
			Scenario newfacilities = ScenarioUtils.createScenario(ConfigUtils.createConfig());
			for (var facId : usedFacilities) {
				newfacilities.getActivityFacilities().addActivityFacility(scenario.getActivityFacilities().getFacilities().get(facId));
			}
			if (newfacilities.getActivityFacilities().getFacilities().size() > 0) {
				new FacilitiesWriter(newfacilities.getActivityFacilities()).write(outputFolder + "/facilities_" + i + ".xml.gz");
			}
		}

	}

}
