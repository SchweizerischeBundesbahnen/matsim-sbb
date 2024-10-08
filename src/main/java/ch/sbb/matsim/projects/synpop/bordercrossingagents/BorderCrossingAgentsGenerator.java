package ch.sbb.matsim.projects.synpop.bordercrossingagents;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;

public class BorderCrossingAgentsGenerator {

    private final Random random;
    private final WeightedRandomSelection<Integer> ageDistribution;
    final int initialPersonId = 16_000_000;
    Map<Id<Zone>, WeightedRandomSelection<Long>> zonalBuildingSelector;
    private final Map<String, Integer> commutersPerNuts3 = new HashMap<>();
    private Population population;
    private Map<String, WeightedRandomSelection<Id<Zone>>> nuts3ToZoneSelector;
    private BorderCrossingAgentsOSMBuildingParser borderCrossingAgentsOSMBuildingParser;

    public BorderCrossingAgentsGenerator(Random random) {
        this.random = random;
        ageDistribution = new WeightedRandomSelection<>(random);
    }

    public static void main(String[] args) {
        String osmFile = args[0];
        String zonesFile = args[1];
        String nuts3Demand = args[2];
        String ageDistributionFile = args[3];
        String outputPath = args[4];
        Random random = MatsimRandom.getRandom();
        BorderCrossingAgentsGenerator borderCrossingAgentsGenerator = new BorderCrossingAgentsGenerator(random);
        borderCrossingAgentsGenerator.readNuts3Demand(nuts3Demand);
        borderCrossingAgentsGenerator.readAgeDistribution(ageDistributionFile);
        borderCrossingAgentsGenerator.prepareOSMData(osmFile, zonesFile);
        borderCrossingAgentsGenerator.manuallyAdjustDistribution();
        borderCrossingAgentsGenerator.generatePersons();
        new PopulationWriter(borderCrossingAgentsGenerator.getPopulation()).write(outputPath + "/cb_commuter_plans.xml.gz");
        PopulationToSynpopexporter exporter = new PopulationToSynpopexporter(borderCrossingAgentsGenerator.getPopulation());
        exporter.prepareHouseholds();
        exporter.exportPopulation(outputPath + "/persons.csv");
        exporter.exportHouseholds(outputPath + "/households.csv");
        System.out.println("done");

    }

    /**
     * Adjusts some home locations to be closer to the Swiss border
     */
    private void manuallyAdjustDistribution() {


        //Bolzano, all other zones are "far off behind the mountains"
        WeightedRandomSelection<Id<Zone>> bolzano = new WeightedRandomSelection(random);
        bolzano.add(Id.create(842901006, Zone.class), 1.0);
        bolzano.add(Id.create(842901009, Zone.class), 1.0);
        bolzano.add(Id.create(842901005, Zone.class), 1.0);
        this.nuts3ToZoneSelector.put("ITH10", bolzano);

        //Domodossola, all other zones are too far away
        WeightedRandomSelection<Id<Zone>> domodossola = new WeightedRandomSelection(random);
        domodossola.add(Id.create(841104011, Zone.class), 1.0);
        this.nuts3ToZoneSelector.put("ITC14", domodossola);


        //tirolerOberland, all other zones are "far off behind the mountains"
        WeightedRandomSelection<Id<Zone>> tirolerOberland = new WeightedRandomSelection(random);
        tirolerOberland.add(Id.create(813304001, Zone.class), 1.0);
        this.nuts3ToZoneSelector.put("AT334", tirolerOberland);

    }

    private void readAgeDistribution(String ageDistributionFile) {
        try (CSVReader reader = new CSVReader(ageDistributionFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                int age = Integer.parseInt(line.get("age"));
                double probability = Double.parseDouble(line.get("probability"));
                ageDistribution.add(age, probability);
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public Population getPopulation() {
        return population;
    }

    private void generatePersons() {
        this.population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        var fac = population.getFactory();
        int personIdNo = initialPersonId;
        for (var commuters : commutersPerNuts3.entrySet()) {
            String nuts3 = commuters.getKey();
            WeightedRandomSelection<Id<Zone>> selection = this.nuts3ToZoneSelector.get(nuts3);
            if (selection == null) {
                LogManager.getLogger(getClass()).error("Not buildings in NUTS " + nuts3);
                continue;
            }
            for (int i = 0; i < commuters.getValue(); i++) {
                Id<Person> personId = Id.createPersonId(personIdNo);
                int age = ageDistribution.select();
                Id<Zone> zoneId = selection.select();
                long buildingOSMWayId = this.zonalBuildingSelector.get(zoneId).select();
                Coord homeCoord = borderCrossingAgentsOSMBuildingParser.getBuildingData().get(buildingOSMWayId).centroid();
                Person person = fac.createPerson(personId);
                String language = getLanguage(nuts3);
                person.getAttributes().putAttribute("NUTS3", nuts3);
                person.getAttributes().putAttribute("residentialOSMWay", buildingOSMWayId);
                person.getAttributes().putAttribute("age", age);
                person.getAttributes().putAttribute("current_job_rank", "employee");
                person.getAttributes().putAttribute("is_swiss", "0");
                person.getAttributes().putAttribute("current_edu", "null");
                person.getAttributes().putAttribute("level_of_employment", 80 + random.nextInt(5) * 5);
                person.getAttributes().putAttribute("language", language);
                person.getAttributes().putAttribute("zone_id", zoneId.toString());
                person.getAttributes().putAttribute("is_employed", "True");
                person.getAttributes().putAttribute(Variables.ANALYSIS_SUBPOPULATION, Variables.CB_COMMUTER);
                person.getAttributes().putAttribute(Variables.HIGHEST_EDUCATION, "2");
                this.population.addPerson(person);
                Plan plan = fac.createPlan();
                person.addPlan(plan);

                Activity home = fac.createActivityFromCoord("home", homeCoord);
                home.setEndTime(8 * 3600);
                plan.addActivity(home);


                personIdNo++;
            }
        }
    }

    private String getLanguage(String nuts3) {
        return switch (nuts3.substring(0, 2)) {
            case "DE", "AT" -> "german";
            case "FR" -> "french";
            case "IT" -> "italian";
            default -> "unknown";
        };

    }

    private void readNuts3Demand(String nuts3Demand) {
        try (CSVReader reader = new CSVReader(nuts3Demand, ";")) {

            var line = reader.readLine();
            while (line != null) {
                String nuts3 = line.get("NUTS3");
                double commuters = Double.parseDouble(line.get("Pendler"));
                this.commutersPerNuts3.put(nuts3, (int) commuters);
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void prepareOSMData(String osmFile, String zonesFile) {

        Zones zones = ZonesLoader.loadZones("ID_Zone", zonesFile, "ID_Zone");
        borderCrossingAgentsOSMBuildingParser = new BorderCrossingAgentsOSMBuildingParser(TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus), Executors.newWorkStealingPool(), zones);
        borderCrossingAgentsOSMBuildingParser.parse(Paths.get(osmFile));
        borderCrossingAgentsOSMBuildingParser.assignZones(random);
        nuts3ToZoneSelector = borderCrossingAgentsOSMBuildingParser.prepareRandomDistributor(true, random);
        zonalBuildingSelector = borderCrossingAgentsOSMBuildingParser.getWeightedBuildingsPerZone();
    }
}
