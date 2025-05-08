package ch.sbb.matsim.projects.synpop.tourismRail;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.projects.synpop.SingleTripAgentToCSVConverter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import omx.OmxFile;
import omx.OmxLookup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CompareRailDemandFQ {

    private static final Logger logger = LogManager.getLogger(CompareRailDemandFQ.class);
    final Map<String, Integer> zonalLookup = new HashMap<>();
    private final String identifier;
    private final Random random = MatsimRandom.getRandom();
    private final Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
    private final Map<Id<TransitStopFacility>, List<Id<ActivityFacility>>> facilitiesAroundStops = new ConcurrentHashMap<>();
    private final String aggregationAttribute;
    private double[][] fqDemandDWV;
    private Map<Id<TransitStopFacility>, List<Integer>> stopIdToZonesMap;
    private final Set<Id<TransitStopFacility>> stopsFilter = new HashSet<>();
    private final Set<Id<TransitStopFacility>> stopsWithDestinationScalingOnly = new HashSet<>();
    private final Map<Id<TransitStopFacility>, Map<Id<TransitStopFacility>, Double>> mobiDemand = new HashMap<>();
    private final Map<String, Map<String, Double>> aggregatedMobiDemand = new HashMap<>();
    private final Map<String, Map<String, Double>> aggregatedPaxflow = new HashMap<>();
    private final Map<Integer, double[][]> fqDemandPerTimeSlice = new HashMap<>();
    private final double threshold = 1500.0;
    private int additionalPerson = 0;

    public CompareRailDemandFQ(String identifier, String aggregationAttribute) {
        this.identifier = identifier;
        this.aggregationAttribute = aggregationAttribute;

    }


    public static void main(String[] args) throws IOException {
        String matrixFilePath = args[0];
        String zoneToStopIdFilePath = args[1];
        String whitelistStopFilePath = args[2];
        String mobiDemandFilePath = args[3];
        String transitScheduleFilePath = args[4];
        String facilitiesFilePath = args[5];
        String identifier = args[6];
        String zonesFile = args[7];
        String aggregationAttribute = args[8].equals("-") ? null : args[8];
        String outputPopulationFilePath = args[9];

        CompareRailDemandFQ compareRailDemandFQ = new CompareRailDemandFQ(identifier, aggregationAttribute);
        compareRailDemandFQ.prepareScenario(transitScheduleFilePath, facilitiesFilePath, zonesFile);
        compareRailDemandFQ.readMatrix(matrixFilePath);
        compareRailDemandFQ.readZoneToStopIdMap(zoneToStopIdFilePath);
        compareRailDemandFQ.readWhitelistStopFile(whitelistStopFilePath);
        compareRailDemandFQ.readMobiDemandFile(mobiDemandFilePath);
        compareRailDemandFQ.compareDemand();
        new PopulationWriter(compareRailDemandFQ.getScenario().getPopulation()).write(outputPopulationFilePath);
        SingleTripAgentToCSVConverter.writeSingleTripsAsCSV(outputPopulationFilePath.replace(".xml.gz", ".csv"), compareRailDemandFQ.getScenario().getPopulation(), identifier);
    }


    public Scenario getScenario() {
        return scenario;
    }

    private void prepareScenario(String transitScheduleFile, String facilitiesFile, String zonesFile) {
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimFacilitiesReader(scenario).readFile(facilitiesFile);
        if (aggregationAttribute != null) {
            Zones zones = ZonesLoader.loadZones("zones", zonesFile);
            scenario.getTransitSchedule().getFacilities().values().forEach(transitStopFacility -> {
                Zone zone = zones.findZone(transitStopFacility.getCoord());
                String zoneAttribute = Variables.DEFAULT_OUTSIDE_ZONE;
                if (zone != null) {
                    zoneAttribute = zone.getAttribute(aggregationAttribute).toString();
                }
                transitStopFacility.getAttributes().putAttribute(aggregationAttribute, zoneAttribute);
            });
        }
    }

    private void readMobiDemandFile(String filename) {
        try (CSVReader csvReader = new CSVReader(filename, ";")) {
            Map<String, String> row;
            while ((row = csvReader.readLine()) != null) {
                Id<TransitStopFacility> fromStopId = Id.create(row.get("from_station"), TransitStopFacility.class);

                Id<TransitStopFacility> toStopId = Id.create(row.get("to_station"), TransitStopFacility.class);
                if (stopsFilter.contains(fromStopId) && stopsFilter.contains(toStopId)) {
                    double demand = Double.parseDouble(row.get("rail_trips"));
                    mobiDemand.computeIfAbsent(fromStopId, k -> new HashMap<>()).put(toStopId, demand);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading CSV file: {}", e.getMessage());
        }
    }

    private void readWhitelistStopFile(String fileName) {
        Set<Id<TransitStopFacility>> invalidStops = new HashSet<>();
        try (CSVReader csvReader = new CSVReader(fileName, ";")) {
            Map<String, String> row;
            while ((row = csvReader.readLine()) != null) {
                Id<TransitStopFacility> stopId = Id.create(row.get("stopId"), TransitStopFacility.class);
                int destinationScaleOnly = Integer.parseInt(row.get("destination_scale_only"));
                if (scenario.getTransitSchedule().getFacilities().containsKey(stopId)) {
                    stopsFilter.add(stopId);

                    if (destinationScaleOnly == 1) {
                        stopsWithDestinationScalingOnly.add(stopId);
                    }
                } else {
                    invalidStops.add(stopId);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading CSV file: {}", e.getMessage());
        }

        if (!invalidStops.isEmpty()) {
            logger.warn("The following stops are not present in the transit schedule: " + invalidStops);
        }

    }


    public void readMatrix(String filePath) {
        OmxFile omxFile = new OmxFile(filePath);
        omxFile.openReadOnly();

        fqDemandDWV = (double[][]) omxFile.getMatrix("206").getData();
        for (int i = 1; i < 144; i++) {
            logger.info(i);
            fqDemandPerTimeSlice.put(i, (double[][]) omxFile.getMatrix(Integer.toString(i)).getData());
        }
        OmxLookup.OmxIntLookup lookup = (OmxLookup.OmxIntLookup) omxFile.getLookup("NO");
        int[] lookupValues = lookup.getLookup();
        for (int i = 0; i < lookupValues.length; i++) {
            zonalLookup.put(String.valueOf(lookupValues[i]), i);
        }
    }

    public void readZoneToStopIdMap(String csvFilePath) {
        stopIdToZonesMap = new HashMap<>();
        try (CSVReader csvReader = new CSVReader(csvFilePath, ";")) {
            Map<String, String> row;
            while ((row = csvReader.readLine()) != null) {
                var stopId = Id.create(Integer.parseInt(row.get("stopId").trim()), TransitStopFacility.class);
                var zoneIds = Arrays.stream(row.get("zoneIds").split(",")).map(Integer::parseInt).collect(Collectors.toList());
                stopIdToZonesMap.put(stopId, zoneIds);

            }
        } catch (IOException e) {
            logger.error("Error reading CSV file: {}", e.getMessage());
        }
    }

    private void compareDemand() {
        Map<Id<TransitStopFacility>, Map<Id<TransitStopFacility>, Double>> missingStopToStopDemand = new HashMap<>();
        for (var fromStopId : this.stopsFilter) {
            HashMap<Id<TransitStopFacility>, Double> missingDemand = new HashMap<>();
            TransitStopFacility fromStopFacility = this.scenario.getTransitSchedule().getFacilities().get(fromStopId);
            missingStopToStopDemand.put(fromStopId, missingDemand);
            for (var toStopId : this.stopsFilter) {
                if (stopsWithDestinationScalingOnly.contains(fromStopId) && stopsWithDestinationScalingOnly.contains(toStopId)) {
                    continue;
                }
                double mobiDemand = this.mobiDemand.getOrDefault(fromStopId, new HashMap<>()).getOrDefault(toStopId, 0.0);
                var fromStopZones = this.stopIdToZonesMap.get(fromStopId);
                var toStopZones = this.stopIdToZonesMap.get(toStopId);
                double paxFlowDemand = getMultipleZonesDemand(fromStopZones, toStopZones, this.fqDemandDWV);
                double difference = paxFlowDemand - mobiDemand;
                TransitStopFacility toStopFacility = this.scenario.getTransitSchedule().getFacilities().get(toStopId);
                missingDemand.put(toStopId, difference);
                if (aggregationAttribute != null) {
                    String fromZone = fromStopFacility.getAttributes().getAttribute(aggregationAttribute).toString();
                    String toZone = toStopFacility.getAttributes().getAttribute(aggregationAttribute).toString();
                    aggregatedMobiDemand.computeIfAbsent(fromZone, k -> new HashMap<>()).put(toZone, mobiDemand);
                    aggregatedPaxflow.computeIfAbsent(fromZone, k -> new HashMap<>()).put(toZone, paxFlowDemand);

                } else if (difference > 1.0) {
                    addAdditionalDemand(fromStopId, toStopId, difference);
                }
            }
        }
        for (String fromZone : aggregatedMobiDemand.keySet()) {
            for (String toZone : aggregatedMobiDemand.get(fromZone).keySet()) {
                double mobiDemand = aggregatedMobiDemand.get(fromZone).get(toZone);
                double paxFlowDemand = aggregatedPaxflow.get(fromZone).get(toZone);
                double difference = paxFlowDemand - mobiDemand;
                if (difference > 1.0) {
                    var aggregatedStopTopStopDemandMissing = missingStopToStopDemand.entrySet()
                            .stream().filter(entry -> scenario.getTransitSchedule().getFacilities().get(entry.getKey()).getAttributes().getAttribute(aggregationAttribute).equals(fromZone))
                            .flatMap(idMapEntry -> idMapEntry.getValue().entrySet().stream())
                            .filter(entry -> scenario.getTransitSchedule().getFacilities().get(entry.getKey()).getAttributes().getAttribute(aggregationAttribute).equals(toZone))
                            .mapToDouble(entry -> entry.getValue())
                            .filter(value -> value > 0.)
                            .sum();
                    double scaleFactor = difference / aggregatedStopTopStopDemandMissing;
                    logger.info("from zone: " + fromZone + " to zone: " + toZone + " scale factor: " + scaleFactor);
                    for (var fromStopId : this.stopsFilter) {
                        if (!fromZone.equals(this.scenario.getTransitSchedule().getFacilities().get(fromStopId).getAttributes().getAttribute(aggregationAttribute).toString())) {
                            continue;
                        }
                        for (var toStopId : this.stopsFilter) {
                            if (stopsWithDestinationScalingOnly.contains(fromStopId) && stopsWithDestinationScalingOnly.contains(toStopId)) {
                                continue;
                            }
                            if (!toZone.equals(this.scenario.getTransitSchedule().getFacilities().get(toStopId).getAttributes().getAttribute(aggregationAttribute).toString())) {
                                continue;
                            }
                            double demand = missingStopToStopDemand.get(fromStopId).get(toStopId);
                            if (demand > 0.0) {
                                addAdditionalDemand(fromStopId, toStopId, demand * scaleFactor);
                            }
                        }
                    }
                }
            }
        }
    }

    private void addAdditionalDemand(Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId, double difference) {
        WeightedRandomSelection<Integer> timeSliceSelector = generateTimeSliceSelector(fromStopId, toStopId);
        int timeSlice = timeSliceSelector.select();
        while (difference > 0.0) {
            if (difference > 1.0 || random.nextDouble() < difference) {
                generateSingleTripAgent(fromStopId, toStopId, timeSlice);
                difference--;
            }

        }
    }

    private Coord drawCoordFromFacilityAroundStop(Id<TransitStopFacility> stopId) {
        var stop = scenario.getTransitSchedule().getFacilities().get(stopId);
        List<Id<ActivityFacility>> facilitiesAroundStop = this.facilitiesAroundStops.computeIfAbsent(stopId, transitStopFacilityId -> {
            Coord stopCoord = stop.getCoord();
            return scenario.getActivityFacilities().getFacilities().values().stream().filter(facility -> CoordUtils.calcEuclideanDistance(stopCoord, facility.getCoord()) < threshold).map(ActivityFacility::getId).collect(Collectors.toList());
        });
        if (facilitiesAroundStop != null && !facilitiesAroundStop.isEmpty()) {
            var facilityId = facilitiesAroundStop.get(random.nextInt(facilitiesAroundStop.size()));
            return this.scenario.getActivityFacilities().getFacilities().get(facilityId).getCoord();
        } else {
            return stop.getCoord();
        }
    }

    private void generateSingleTripAgent(Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId, int timeSlice) {
        var factory = scenario.getPopulation().getFactory();
        var person = factory.createPerson(Id.createPersonId(identifier + "_add_" + additionalPerson + "_" + fromStopId + "_" + toStopId));
        additionalPerson++;

        var plan = factory.createPlan();
        person.addPlan(plan);
        Coord fromActivityCoord = drawCoordFromFacilityAroundStop(fromStopId);
        if (fromActivityCoord == null) return;
        var fromActivity = factory.createActivityFromCoord("Exogenous", fromActivityCoord);
        fromActivity.getAttributes().putAttribute(Variables.MOBiTripAttributes.PURPOSE, "Fr");
        fromActivity.setEndTime(Math.max(0, timeSlice * 600 - random.nextInt(1500)));

        Coord toActivityCoord = drawCoordFromFacilityAroundStop(toStopId);
        if (toActivityCoord == null) return;
        plan.addActivity(fromActivity);
        plan.addLeg(factory.createLeg(SBBModes.PT));
        var toActivity = factory.createActivityFromCoord("Exogenous", toActivityCoord);

        plan.addActivity(toActivity);
        scenario.getPopulation().addPerson(person);
    }

    private WeightedRandomSelection<Integer> generateTimeSliceSelector(Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId) {
        WeightedRandomSelection<Integer> selector = new WeightedRandomSelection<>(random);
        var fromStopZones = this.stopIdToZonesMap.get(fromStopId);
        var toStopZones = this.stopIdToZonesMap.get(toStopId);
        for (var e : fqDemandPerTimeSlice.entrySet()) {
            var timeSlice = e.getKey();
            var fqDemand = e.getValue();
            double demand = getMultipleZonesDemand(fromStopZones, toStopZones, fqDemand);
            selector.add(timeSlice, demand);
        }
        return selector;
    }

    private double getMultipleZonesDemand(List<Integer> fromStopZones, List<Integer> toStopZones, double[][] fqDemand) {
        double totalDemand = 0.0;
        for (var fromZone : fromStopZones) {
            int fromZoneIndex = zonalLookup.get(String.valueOf(fromZone));
            for (var toZone : toStopZones) {
                int toZoneIndex = zonalLookup.get(String.valueOf(toZone));
                totalDemand += fqDemand[fromZoneIndex][toZoneIndex];
            }
        }
        return totalDemand;
    }
}
