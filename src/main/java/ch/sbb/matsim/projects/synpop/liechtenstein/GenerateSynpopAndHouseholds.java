package ch.sbb.matsim.projects.synpop.liechtenstein;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.projects.synpop.bordercrossingagents.BorderCrossingAgentsOSMBuildingParser;
import ch.sbb.matsim.projects.synpop.bordercrossingagents.OSMRetailParser;
import ch.sbb.matsim.projects.synpop.bordercrossingagents.PopulationToSynpopexporter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.contrib.osm.networkReader.PbfParser;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.households.Household;
import org.matsim.households.HouseholdImpl;
import org.matsim.households.Households;
import org.matsim.households.HouseholdsImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class GenerateSynpopAndHouseholds {


    public static final String FACILITY_ID = "facilityId";
    public static final String APPRENTICE = "apprentice";
    final ExecutorService executor = Executors.newWorkStealingPool();
    private final Zones zones;
    private final Random random;
    private final Population population;
    private final Households households;
    private final ActivityFacilities facilities;
    private final double employmentProbability = (18103. + 670.) / 25185;
    private final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus);
    final Logger log = LogManager.getLogger(GenerateSynpopAndHouseholds.class);
    final Map<Id<Zone>, WeightedRandomSelection<Id<ActivityFacility>>> facilitySelector = new HashMap<>();
    private final Map<Id<Zone>, Map<String, Integer>> householdSizesPerZone = new HashMap<>();
    private final Map<Id<Zone>, WeightedRandomSelection<Integer>> ageDistributionPerZone = new HashMap<>();
    private int personNo = 11_000_000;
    private Map<Long, Set<Long>> buildingDataNodeStorage;
    private Map<Long, Coord> nodeStorage;
    private Map<Long, GenerateBusinessesPerZone.OSDMBuildingData> buildingData;


    public GenerateSynpopAndHouseholds(String osmInputFile, String householdFile, String agedistributionFile, Zones zones, Random random) {
        this.zones = zones;
        this.random = random;
        population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        households = new HouseholdsImpl();
        facilities = FacilitiesUtils.createActivityFacilities();
        parseHouseholdSizes(householdFile);
        parseAgeDistribution(agedistributionFile);
        parseOSMFile(Paths.get((osmInputFile)));
        generateHouses();
        generateHouseholdsAndPopulation();

    }

    public static void main(String[] args) {
        String osmInputFile = args[0];
        String householdFile = args[1];
        String agedistributionFile = args[2];
        String zonesFile = args[3];
        String outputPopulation = args[4];
        String outputHouseholds = args[5];
        var zones = ZonesLoader.loadZones("zones", zonesFile, "ID_Zone");
        GenerateSynpopAndHouseholds synpopAndHouseholds = new GenerateSynpopAndHouseholds(osmInputFile, householdFile, agedistributionFile, zones, MatsimRandom.getRandom());
        synpopAndHouseholds.exportHouseholds(outputHouseholds);
        synpopAndHouseholds.exportPopulation(outputPopulation);
    }

    void parseOSMFile(Path inputFile) {
        buildingData = new ConcurrentHashMap<>();
        buildingDataNodeStorage = new ConcurrentHashMap<>();
        nodeStorage = new ConcurrentHashMap<>();
        // make sure we have empty collections

        log.info("Starting to read ways ");
        new PbfParser.Builder()
                .setWaysHandler(this::handleWay)
                .setExecutor(executor)
                .build()
                .parse(inputFile);

        log.info("Finished reading ways");

        log.info("Starting to read nodes");

        new PbfParser.Builder()
                .setNodeHandler(this::handleNode)
                .setExecutor(executor)
                .build()
                .parse(inputFile);

        log.info("finished reading nodes");
    }

    private void generateHouseholdsAndPopulation() {
        int householdNo = 9_000_000;
        for (var entry : this.householdSizesPerZone.entrySet()) {
            var zoneId = entry.getKey();
            WeightedRandomSelection<Integer> ageDistribution = this.ageDistributionPerZone.get(zoneId);
            for (var householdComposition : entry.getValue().entrySet()) {
                int adults = Integer.parseInt(householdComposition.getKey().substring(0, 1));
                int children = Integer.parseInt(householdComposition.getKey().substring(2));
                log.info(householdComposition.getKey());
                for (int i = 0; i < householdComposition.getValue(); i++) {
                    var facilityId = facilitySelector.get(zoneId).select();
                    List<Id<Person>> personsInHousehold = new ArrayList<>();
                    Id<Household> householdId = Id.create(householdNo, Household.class);
                    HouseholdImpl household = new HouseholdImpl(householdId);
                    household.getAttributes().putAttribute(PopulationToSynpopexporter.ZONE_ID, zoneId);
                    this.households.getHouseholds().put(householdId, household);
                    List<Integer> childAges = selectAges(children, ageDistribution, 0, 20, 8);
                    for (int age : childAges) {
                        Person p = generatePerson(age, householdId, zoneId);
                        personsInHousehold.add(p.getId());
                    }
                    int maxChildAge = childAges.stream().mapToInt(value -> value).max().orElse(0);
                    List<Integer> adultAges = selectAges(adults, ageDistribution, 20 + maxChildAge, children > 0 ? 65 : 102, 40);
                    for (int age : adultAges) {
                        Person p = generatePerson(age, householdId, zoneId);
                        personsInHousehold.add(p.getId());
                    }
                    household.setMemberIds(personsInHousehold);
                    household.getAttributes().putAttribute(FACILITY_ID, facilityId);
                    householdNo++;
                }
            }
        }
    }

    private Person generatePerson(int age, Id<Household> householdId, Id<Zone> zoneId) {
//       household_id	is_swiss	zone_id	residentialOSMWay	language	current_job_rank	level_of_employment	NUTS3	age

        Person person = population.getFactory().createPerson(Id.createPersonId(personNo));
        population.addPerson(person);
        person.getAttributes().putAttribute(PopulationToSynpopexporter.HOUSEHOLD_ID, householdId);
        person.getAttributes().putAttribute(PopulationToSynpopexporter.ZONE_ID, zoneId);
        person.getAttributes().putAttribute("age", age);
        boolean isEmployed = false;
        int levelOfEmployment = 0;
        String currentEdu = "null";
        if (age > 4 && age < 7) currentEdu = "kindergarten";
        else if (age < 12) currentEdu = "pupil_primary";
        else if (age < 16) currentEdu = "pupil_secondary";
        else if (age < 20) currentEdu = random.nextDouble() < 0.3 ? APPRENTICE : "pupil_secondary";
        String curent_job_rank = "null";

        if (age > 17 && age < 65) {
            isEmployed = random.nextDouble() < this.employmentProbability;
        }
        if (isEmployed) {
            curent_job_rank = "employee";

            if (random.nextDouble() < 0.34) {
                levelOfEmployment = 20 + random.nextInt(70);
            } else {
                levelOfEmployment = 90 + random.nextInt(11);
            }
        }
        if (currentEdu.equals(APPRENTICE)) {
            isEmployed = true;
            levelOfEmployment = 100;
            curent_job_rank = APPRENTICE;
        }
        person.getAttributes().putAttribute("is_employed", isEmployed);
        person.getAttributes().putAttribute("current_edu", currentEdu);
        person.getAttributes().putAttribute("current_job_rank", curent_job_rank);
        person.getAttributes().putAttribute("level_of_employment", levelOfEmployment);
        person.getAttributes().putAttribute("language", "german");
        person.getAttributes().putAttribute("is_swiss", false);
        personNo++;
        return person;
    }

    private List<Integer> selectAges(int i, WeightedRandomSelection<Integer> ageDistribution, int minAge, int maxAge, int maxSpread) {
        if (i == 0) return Collections.emptyList();
        List<Integer> ages = new ArrayList<>();
        int firstAge = ageDistribution.select();
        while (firstAge > maxAge && firstAge < minAge) {
            firstAge = ageDistribution.select();
        }
        ages.add(firstAge);
        for (int j = 1; j < i; j++) {
            int age = ageDistribution.select();
            while (age > maxAge && age < minAge && Math.abs(firstAge - age) > maxSpread) {
                age = ageDistribution.select();
            }
            ages.add(age);
        }
        return ages;
    }

    private void parseAgeDistribution(String agedistributionFile) {
        try (CSVReader reader = new CSVReader(agedistributionFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                Id<Zone> zoneId = Id.create(line.get("zoneId"), Zone.class);
                WeightedRandomSelection<Integer> ageDistribution = new WeightedRandomSelection(random);
                for (int i = 0; i < 102; i++) {
                    ageDistribution.add(i, Double.parseDouble(line.get(Integer.toString(i))));
                }
                this.ageDistributionPerZone.put(zoneId, ageDistribution);
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseHouseholdSizes(String householdSizeFile) {
        try (CSVReader reader = new CSVReader(householdSizeFile, ";")) {
            List<String> columns = new ArrayList<>(Arrays.stream(reader.getColumns()).toList());
            columns.remove("zoneId");
            var line = reader.readLine();
            while (line != null) {
                Id<Zone> zoneId = Id.create(line.get("zoneId"), Zone.class);
                Map<String, Integer> houseHoldsizesInZone = new HashMap<>();
                for (String s : columns) {
                    houseHoldsizesInZone.put(s, Integer.valueOf(line.get(s)));
                }
                householdSizesPerZone.put(zoneId, houseHoldsizesInZone);
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    void handleNode(OsmNode osmNode) {
        if (buildingDataNodeStorage.containsKey(osmNode.getId())) {
            nodeStorage.put(osmNode.getId(), transformation.transform(new Coord(osmNode.getLongitude(), osmNode.getLatitude())));
        }

    }

    void handleWay(OsmWay osmWay) {
        Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);
        if (tags.containsKey("building")) {
            List<Long> nodeIds = new ArrayList<>();
            OsmModelUtil.nodesAsList(osmWay).forEach(t -> nodeIds.add(t));
            String type = tags.get("building");
            if (BorderCrossingAgentsOSMBuildingParser.RESIDENTIAL_TAGS.contains(type)) {
                String levelString = tags.get("building:levels");
                int levels = -1;
                if (levelString != null) {
                    try {
                        levels = Integer.parseInt(levelString);
                    } catch (NumberFormatException e) {
                    }
                }
                buildingData.put(osmWay.getId(), new GenerateBusinessesPerZone.OSDMBuildingData(osmWay.getId(), nodeIds, levels, type));
                nodeIds.forEach(nodeId -> buildingDataNodeStorage.computeIfAbsent(nodeId, a -> new HashSet<>()).add(osmWay.getId()));

            }
        }


    }

    private void generateHouses() {
        log.info("Generating houses.");
        GeometryFactory fac = new GeometryFactory();

        for (GenerateBusinessesPerZone.OSDMBuildingData data : this.buildingData.values()) {
            List<Long> nodeIds = data.nodeIds();
            var polygon = OSMRetailParser.getPolygon(fac, nodeIds, nodeStorage);
            Coord center = MGC.point2Coord(polygon.getCentroid());
            int floors = Math.max(1, data.floors());
            double area = polygon.getArea() * floors;
            double weight = area;
            Zone zone = this.zones.findZone(center);
            if (zone != null) {
                var zoneId = zone.getId();
                ActivityFacility activityFacility = facilities.getFactory().createActivityFacility(Id.create(data.wayId(), ActivityFacility.class), center);
                this.facilities.addActivityFacility(activityFacility);
                facilitySelector.computeIfAbsent(zoneId, zoneId1 -> new WeightedRandomSelection<>(random)).add(activityFacility.getId(), weight);
            }


        }

    }

    private void exportHouseholds(String outputHouseholdCSVFile) {
        String[] columns = new String[]{PopulationToSynpopexporter.HOUSEHOLD_ID, PopulationToSynpopexporter.ZONE_ID, "xcoord", "ycoord"};
        try (CSVWriter writer = new CSVWriter(null, columns, outputHouseholdCSVFile)) {

            for (Household household : this.households.getHouseholds().values()) {
                writer.set(PopulationToSynpopexporter.HOUSEHOLD_ID, household.getId().toString());
                writer.set(PopulationToSynpopexporter.ZONE_ID, household.getAttributes().getAttribute(PopulationToSynpopexporter.ZONE_ID).toString());
                Id<Facility> facilityId = (Id<Facility>) household.getAttributes().getAttribute(FACILITY_ID);
                ActivityFacility facility = facilities.getFacilities().get(facilityId);
                writer.set("xcoord", String.valueOf(facility.getCoord().getX()));
                writer.set("ycoord", String.valueOf(facility.getCoord().getY()));
                writer.writeRow();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void exportPopulation(String outputPopulationCSVFile) {
        Set<String> populationAttributes = population.getPersons().values().stream().flatMap(person -> person.getAttributes().getAsMap().keySet().stream()).collect(Collectors.toSet());
        List<String> header = new ArrayList<>();
        header.add(PopulationToSynpopexporter.PERSON_ID);
        header.addAll(populationAttributes);
        String[] columns = new String[header.size()];
        header.toArray(columns);
        try (CSVWriter writer = new CSVWriter(null, columns, outputPopulationCSVFile)) {
            for (Person p : this.population.getPersons().values()) {
                writer.set(PopulationToSynpopexporter.PERSON_ID, p.getId().toString());
                for (String attribute : populationAttributes) {
                    Object value = p.getAttributes().getAttribute(attribute);
                    if (value != null) {
                        String outString = value.toString();
                        if (value instanceof Boolean) {
                            outString = outString.toUpperCase();
                        }
                        writer.set(attribute, outString);

                    }
                }
                writer.writeRow();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
