package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;

import static org.matsim.core.config.ConfigUtils.createConfig;
import static org.matsim.core.scenario.ScenarioUtils.createScenario;

public class DemandAggregatorTest {

    @Test
    public void aggregateTripDemandTest() {
        Scenario scenario = createScenario(RunSBB.buildConfig("test/input/scenarios/mobi31test/config.xml"));
        ZonesCollection zones = new ZonesCollection();
        ZonesLoader.loadAllZones(scenario.getConfig(), zones);
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zones);
        DemandAggregator aggregator = new DemandAggregator(scenario, zones, ConfigUtils.addOrGetModule(scenario.getConfig(), PostProcessingConfigGroup.class), railTripsAnalyzer);
        String inputFolder = "test/input/ch/sbb/matsim/analysis/tripsandlegsanalysis/";
        String outputFolder = "test/output/";
        Scenario scenario2 = createScenario(createConfig());
        File outputFolderFile = new File(outputFolder);
        outputFolderFile.mkdirs();

        new PopulationReader(scenario2).readFile(inputFolder + "MOBI33IT.output_experienced_plans.xml.gz");
        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class);
        scenario2.getPopulation().getPersons().values().stream()
                .forEach(person -> experiencedPlans.put(person.getId(), person.getSelectedPlan()));
        aggregator.aggregateAndWriteMatrix(10, outputFolder + "matrix.csv", outputFolder + "railDemandStationToStation.csv.gz", outputFolder + "railDemandStationToStationFQ.csv.gz", outputFolder + "tripsPerMun.csv.gz", outputFolder + "tripsPerAMR.csv.gz", experiencedPlans);
        MatsimTestUtils.assertEqualFilesLineByLine(outputFolder + "railDemandStationToStation.csv.gz", inputFolder + "MOBI33IT.railDemandStationToStation.csv.gz");
    }
}