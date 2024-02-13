package ch.sbb.matsim.projects.synpop.airports;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesImpl;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.IOException;
import java.util.*;

import static ch.sbb.matsim.utils.AssignExogeneousTripPurposes.*;

public class GenerateAirportDemand {

    private final Scenario scenario;
    private final Zones zones;
    private final WeightedRandomSelection<Integer> departureTimeSelector;
    private final WeightedRandomSelection<Integer> arrivalTimeSelector;
    private final SwissRailRaptorData data;
    private final Random random;
    private final Map<String, List<Id<ActivityFacility>>> facilitiesPerZoneId = new HashMap<>();
    private final Map<String, WeightedRandomSelection<String>> zonePerAmr = new HashMap<>();

    public GenerateAirportDemand(Scenario scenario, Zones zones, WeightedRandomSelection<Integer> departureTimeSelector, WeightedRandomSelection<Integer> arrivalTimeSelector, Random random) {
        this.random = random;
        this.scenario = scenario;
        this.zones = zones;
        this.departureTimeSelector = departureTimeSelector;
        this.arrivalTimeSelector = arrivalTimeSelector;
        this.data = SwissRailRaptorData.create(scenario.getTransitSchedule(), null, RaptorUtils.createStaticConfig(scenario.getConfig()), scenario.getNetwork(), null);

        prepareFacilities();

    }

    public static void main(String[] args) {

        Random random = MatsimRandom.getRandom();
        String network = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2020\\pt\\NPVM2020\\output\\transitNetwork.xml.gz";
        String transitScheduleFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2020\\pt\\NPVM2020\\output\\transitSchedule.xml.gz";

        String zonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\mobi-zones.shp";
        String facilitiesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\facilities.xml.gz";

        String outputDemand = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\Flughafenverkehr\\output\\airport-demand.xml.gz";

        String departureTimeDistribution = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\Flughafenverkehr\\Tagesgang_GVA_dep.csv";
        String arrivalTimeDistribution = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\Flughafenverkehr\\Tagesgang_GVA_arr.csv";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(network);
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimFacilitiesReader(scenario).readFile(facilitiesFile);
        Zones zones = ZonesLoader.loadZones("zones", zonesFile);
        WeightedRandomSelection<Integer> departureTimeSelector = readTimeDistribution(departureTimeDistribution, random);
        WeightedRandomSelection<Integer> arrivalTimeSelector = readTimeDistribution(arrivalTimeDistribution, random);
        GenerateAirportDemand generateAirportDemand = new GenerateAirportDemand(scenario, zones, departureTimeSelector, arrivalTimeSelector, random);

        generateAirportDemand.generateForAirport("bsl", new Coord(2607176.8206491300, 1272138.9751427100), "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\Flughafenverkehr\\input\\bsl.csv");
        generateAirportDemand.generateForAirport("zrh", new Coord(2684750, 1256341), "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\Flughafenverkehr\\input\\zrh.csv");
        generateAirportDemand.generateForAirport("mxp", new Coord(2699355, 1053646), "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\Flughafenverkehr\\input\\mxp.csv");
        generateAirportDemand.generateForAirport("gva", new Coord(2497430, 1120813), "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\Flughafenverkehr\\input\\gva.csv");
        new PopulationWriter(scenario.getPopulation()).write(outputDemand);
    }

    private static WeightedRandomSelection<Integer> readTimeDistribution(String timeDistributionFile, Random random) {
        WeightedRandomSelection<Integer> selection = new WeightedRandomSelection<>(random);
        try (CSVReader reader = new CSVReader(new String[]{"time", "weight"}, timeDistributionFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                try {
                    Integer time = Integer.valueOf(line.get("time"));
                    Double weight = Double.valueOf(line.get("weight"));
                    System.out.println(time + " " + weight);
                    if (weight > 0) {
                        selection.add(time, weight);
                    }
                } catch (Exception e) {

                }

                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return selection;
    }

    private void prepareFacilities() {
        for (var fac : scenario.getActivityFacilities().getFacilities().values()) {
            var zone = zones.findZone(fac.getCoord());
            if (zone != null) {
                String zoneId = zone.getId().toString();
                facilitiesPerZoneId.computeIfAbsent(zoneId, z -> new ArrayList<>()).add(fac.getId());
            }
        }
        for (Zone zone : ((ZonesImpl) zones).getZones()) {
            String amr = zone.getAttribute("amr_id").toString();
            Integer popTotal = (Integer) zone.getAttribute("pop_total");
            zonePerAmr.computeIfAbsent(amr, a -> new WeightedRandomSelection<>(random)).add(zone.getId().toString(), popTotal);

        }
    }

    private void generateForAirport(String prefix, Coord destinationCoord, String demandFile) {
        LogManager.getLogger(getClass()).info("Handling Airport " + prefix);
        List<ZonalDemand> zonalDemand = readZonalDemand(demandFile);
        int count = 0;
        for (ZonalDemand demand : zonalDemand) {
            for (int i = 0; i < demand.ptDemand(); i++) {
                generateAgentToAirport(prefix, count, destinationCoord, selectFacilityCandidates(demand), SBBModes.PT);
                count++;
                generateAgentFromAirport(prefix, count, destinationCoord, selectFacilityCandidates(demand), SBBModes.PT);
                count++;

            }
            for (int i = 0; i < demand.carDemand(); i++) {
                generateAgentToAirport(prefix, count, destinationCoord, selectFacilityCandidates(demand), SBBModes.CAR);
                count++;
                generateAgentFromAirport(prefix, count, destinationCoord, selectFacilityCandidates(demand), SBBModes.CAR);
                count++;
            }
        }

    }

    private List<Id<ActivityFacility>> selectFacilityCandidates(ZonalDemand demand) {
        List<Id<ActivityFacility>> facilities = null;
        while (facilities == null) {
            String zoneId = demand.aggregate.equals("amr_id") ? zonePerAmr.get(demand.zonalId).select() : demand.zonalId;
            facilities = facilitiesPerZoneId.get(zoneId);
        }
        return facilities;
    }

    private void generateAgentFromAirport(String prefix, int count, Coord airportCoord, List<Id<ActivityFacility>> facilities, String mode) {
        Id<Person> personId = Id.createPersonId(prefix + "_" + count);
        var factory = scenario.getPopulation().getFactory();
        ActivityFacility facility = null;
        facility = drawActivityFacility(facilities, mode, facility);
        Person person = factory.createPerson(personId);
        scenario.getPopulation().addPerson(person);
        Plan plan = factory.createPlan();
        person.addPlan(plan);
        Activity activity = factory.createActivityFromCoord("airport", airportCoord);
        drawAndAssignPurpose(activity);
        activity.setEndTime(departureTimeSelector.select() * 3600 + random.nextInt(3600));
        plan.addActivity(activity);
        plan.addLeg(factory.createLeg(mode));
        plan.addActivity(factory.createActivityFromCoord("airportDestination", facility.getCoord()));
        setSubpopulation(person, mode);

    }

    private void setSubpopulation(Person person, String mode) {
        String subpopulation = mode.equals(SBBModes.PT) ? "airport_rail" : "airport_car";
        PopulationUtils.putSubpopulation(person, subpopulation);
    }

    private void generateAgentToAirport(String prefix, int count, Coord airportCoord, List<Id<ActivityFacility>> facilities, String mode) {
        Id<Person> personId = Id.createPersonId(prefix + "_" + count);
        var factory = scenario.getPopulation().getFactory();
        ActivityFacility facility = null;
        facility = drawActivityFacility(facilities, mode, facility);
        Person person = factory.createPerson(personId);
        scenario.getPopulation().addPerson(person);
        Plan plan = factory.createPlan();
        person.addPlan(plan);
        Activity activity = factory.createActivityFromCoord("airportDestination", facility.getCoord());
        drawAndAssignPurpose(activity);
        int desiredArrivalTime = arrivalTimeSelector.select() * 3600 + random.nextInt(3600);
        int travelTimeEstimate = (int) (1800 + CoordUtils.calcEuclideanDistance(airportCoord, facility.getCoord()) / (17));
        int time = Math.max(desiredArrivalTime - travelTimeEstimate, 0);
        activity.setEndTime(time);
        plan.addActivity(activity);
        plan.addLeg(factory.createLeg(mode));
        plan.addActivity(factory.createActivityFromCoord("airport", airportCoord));
        setSubpopulation(person, mode);
    }

    private void drawAndAssignPurpose(Activity activity) {
        double r = random.nextDouble();
        String purpose;
        //data source: ZRH airport, 2019
        if (r <= 0.26) {
            purpose = BUSINESS;
        } else if (r <= 0.36) {
            purpose = COMMUTE;
        } else {
            purpose = LEISURE;
        }
        activity.getAttributes().putAttribute(Variables.MOBiTripAttributes.PURPOSE, purpose);

    }

    private ActivityFacility drawActivityFacility(List<Id<ActivityFacility>> facilities, String mode, ActivityFacility facility) {
        if (mode.equals(SBBModes.CAR)) {
            int r = random.nextInt(facilities.size());
            facility = scenario.getActivityFacilities().getFacilities().get(facilities.get(r));
        } else if (mode.equals(SBBModes.PT)) {
            int attempts = 0;
            while (facility == null) {
                int r = random.nextInt(facilities.size());
                facility = scenario.getActivityFacilities().getFacilities().get(facilities.get(r));
                if (data.findNearbyStops(facility.getCoord().getX(), facility.getCoord().getY(), 1000 + attempts * 200).isEmpty()) {
                    if (attempts > 10) break;
                    facility = null;
                    attempts++;
                }
            }

        }
        return facility;
    }

    private List<ZonalDemand> readZonalDemand(String demandFile) {
        List<ZonalDemand> demand = new ArrayList<>();
        String carMobi = "car_mobi";
        String ptMobi = "pt_mobi";
        String id = "id";
        String aggregate = "aggregate";
        try (CSVReader reader = new CSVReader(demandFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                ZonalDemand zonalDemand = new ZonalDemand(line.get(aggregate), line.get(id), (int) Double.parseDouble(line.get(carMobi)), (int) Double.parseDouble(line.get(ptMobi)));
                demand.add(zonalDemand);
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return demand;
    }

    public record ZonalDemand(String aggregate, String zonalId, int carDemand, int ptDemand) {
    }
}
