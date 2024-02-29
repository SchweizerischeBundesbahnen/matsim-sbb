package ch.sbb.matsim.projects.synpop.roadExogeneous;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.jboss.logging.Logger;
import org.matsim.api.core.v01.BasicLocation;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SingleTripAgentCreator {

    private final Random random;
    private final PopulationFactory factory;
    private final Population population;
    private final WeightedRandomSelection<Integer> dailyDepartureDistribution;
    private final Map<Id<Zone>, WeightedRandomSelection<Coord>> zonalOriginDestinationDistribution;
    private final String originActivity;
    private final String destinationActivity;
    private final Map<String, Object> additionalPersonAttributes;

    public SingleTripAgentCreator(Population population, WeightedRandomSelection<Integer> dailyDepartureDistribution, Map<Id<Zone>, WeightedRandomSelection<Coord>> zonalOriginDestinationDistribution, String originActivity, String destinationActivity, Map<String, Object> additionalPersonAttributes, Random random) {
        this.population = population;
        this.factory = population.getFactory();
        this.dailyDepartureDistribution = dailyDepartureDistribution;
        this.zonalOriginDestinationDistribution = zonalOriginDestinationDistribution;
        this.originActivity = originActivity;
        this.destinationActivity = destinationActivity;
        this.random = random;
        this.additionalPersonAttributes = additionalPersonAttributes == null ? new HashMap<>() : additionalPersonAttributes;
    }

    public static String getAggregateZone(Zones zones, Id<Zone> fromZoneId) {
        Integer zoneIdNo = Integer.parseInt(fromZoneId.toString());
        if (zoneIdNo < 700101001) {
            return String.valueOf(zones.getZone(fromZoneId).getAttribute("amr_id"));
        } else if (zoneIdNo < 710101001) {
            return "Liechtenstein";
        } else {
            Zone zone = zones.getZone(fromZoneId);
            if (zone != null) {
                return String.valueOf(zone.getAttribute("mun_name"));
            } else return fromZoneId.toString();
        }
    }

    public static Map<Id<Zone>, WeightedRandomSelection<Coord>> createCoordinateSelector(Scenario scenario, Zones zones, Random random, String foreignConnectorsLocationFile) {
        var coordinateSelector = createZonalDistributionFromBasicLocation(scenario.getActivityFacilities().getFacilities().values(), zones, random);
        readAdditionalForeignConnectors(coordinateSelector, foreignConnectorsLocationFile, random);
        fillMissingZonesWithCentroids(coordinateSelector, zones, random);
        return coordinateSelector;
    }

    public static WeightedRandomSelection<Integer> readTimeDistribution(String timeDistributionFile, Random random) {
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

    public static Map<Id<Zone>, WeightedRandomSelection<Coord>> createZonalDistributionFromBasicLocation(Collection<? extends BasicLocation> facilitySet, Zones zones, Random r) {
        Map<Id<Zone>, WeightedRandomSelection<Coord>> distribution = new HashMap<>();
        facilitySet.forEach(basicLocation -> {
            Zone zone = zones.findZone(basicLocation.getCoord());
            if (zone != null) {
                distribution.computeIfAbsent(zone.getId(), a -> new WeightedRandomSelection<>(r)).add(basicLocation.getCoord(), 1.);
            }
        });
        return distribution;
    }

    public static Map<Id<Zone>, WeightedRandomSelection<Coord>> readAdditionalForeignConnectors(Map<Id<Zone>, WeightedRandomSelection<Coord>> distribution, String filename, Random r) {
        try (CSVReader reader = new CSVReader(filename, ";")) {
            var line = reader.readLine();
            while (line != null) {
                if (!line.isEmpty()) {
                    Id<Zone> zoneId = Id.create(line.get("zone_id"), Zone.class);
                    Coord coord = new Coord(Double.parseDouble(line.get("x")), Double.parseDouble(line.get("y")));
                    distribution.computeIfAbsent(zoneId, a -> new WeightedRandomSelection<>(r)).add(coord, 1.0);
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return distribution;

    }

    public static Map<Id<Zone>, WeightedRandomSelection<Coord>> fillMissingZonesWithCentroids(Map<Id<Zone>, WeightedRandomSelection<Coord>> distribution, Zones zones, Random r) {
        zones.getZones().stream()
                .filter(zone -> !distribution.containsKey(zone.getId()))
                .forEach(zone ->
                {
                    WeightedRandomSelection<Coord> wrs = new WeightedRandomSelection<>(r);
                    wrs.add(MGC.coordinate2Coord(zone.getEnvelope().centre()), 1.0);
                    distribution.put(zone.getId(), wrs);
                    Logger.getLogger(SingleTripAgentCreator.class).info("added centroid for zone " + zone.getId());
                });

        return distribution;
    }

    public void generateAgents(Map<Id<Zone>, Map<Id<Zone>, Double>> zoneToZoneDemand, String mode, String subpopulation, String additionalNamingPostfix) {
        for (var fromZone : zoneToZoneDemand.entrySet()) {
            for (var toZone : fromZone.getValue().entrySet()) {
                Double toZoneValue = toZone.getValue();
                for (double i = 0.0; i < toZoneValue; i++) {
                    double leftoverAgents = toZoneValue - i;
                    if (leftoverAgents < 1.0) {
                        if (random.nextDouble() > leftoverAgents) continue;
                    }
                    generateSingleTripAgent(fromZone.getKey(), toZone.getKey(), (int) i, mode, subpopulation, additionalNamingPostfix);
                }
            }

        }
    }

    private void generateSingleTripAgent(Id<Zone> fromZoneId, Id<Zone> toZoneId, int no, String mode, String subpopulation, String additionalNamingPostfix) {
        Coord startCoord = zonalOriginDestinationDistribution.get(fromZoneId).select();
        Coord destinationCoord = zonalOriginDestinationDistribution.get(toZoneId).select();
        int departureTime = dailyDepartureDistribution.select() * 3600 + random.nextInt(3600);
        var personId = Id.createPersonId(subpopulation + "_" + additionalNamingPostfix + "_" + fromZoneId.toString() + "_" + toZoneId.toString() + "_" + no);
        Person person = factory.createPerson(personId);
        population.addPerson(person);
        Plan plan = factory.createPlan();
        person.addPlan(plan);
        Activity start = factory.createActivityFromCoord(originActivity, startCoord);
        start.setEndTime(departureTime);
        plan.addActivity(start);
        plan.addLeg(factory.createLeg(mode));
        plan.addActivity(factory.createActivityFromCoord(destinationActivity, destinationCoord));
        PopulationUtils.putSubpopulation(person, subpopulation);
        additionalPersonAttributes.forEach((k, v) -> person.getAttributes().putAttribute(k, v));
    }


}
