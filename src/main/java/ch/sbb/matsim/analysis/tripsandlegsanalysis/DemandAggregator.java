/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import ch.sbb.matsim.zones.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class DemandAggregator {

    public static final String OUTSIDE_ZONE = Variables.DEFAULT_OUTSIDE_ZONE;
    public static final Id<Zone> OUTSIDE_ZONE_ID = Id.create(OUTSIDE_ZONE, Zone.class);
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Map<Id<TransitStopFacility>, String> aggregateZoneStop = new HashMap<>();
    private final Map<Id<TransitStopFacility>, String> zoneStop = new HashMap<>();
    private final Map<Id<TransitStopFacility>, Map<Id<TransitStopFacility>, RailODTravelInfo>> odRailDemandMatrix = new TreeMap<>();
    private final ArrayList<String> aggregateZones;
    private final Map<Id<Zone>, Map<Id<Zone>, ODTravelInfo>> allModesOdDemand = new ConcurrentHashMap<>();
    private final Scenario scenario;
    private final Zones zones;
    private final MainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
    private final Logger LOG = LogManager.getLogger(getClass());
    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    public DemandAggregator(Scenario scenario, ZonesCollection zonesCollection, final PostProcessingConfigGroup ppConfig, RailTripsAnalyzer railTripsAnalyzer) {
        this.scenario = scenario;
        Zones zones = zonesCollection.getZones(ppConfig.getZonesId());
        this.zones = zones;
        this.railTripsAnalyzer = railTripsAnalyzer;
        for (TransitStopFacility facility : scenario.getTransitSchedule().getFacilities().values()) {
            Zone zone = zones.findZone(facility.getCoord());
            String aggregate = zone != null ? String.valueOf(zone.getAttribute(ppConfig.getRailMatrixAggregate())) : OUTSIDE_ZONE;
            if (aggregate.equals("-1")) {
                aggregate = OUTSIDE_ZONE;
            }
            aggregateZoneStop.put(facility.getId(), aggregate);
            zoneStop.put(facility.getId(), zone != null ? zone.getId().toString() : OUTSIDE_ZONE);
        }
        var sortedZones = new TreeSet<>(new StringNumberComparator());
        sortedZones.addAll(aggregateZoneStop.values());
        aggregateZones = new ArrayList<>(sortedZones);
        aggregateZones.remove("");

    }

    public static void main(String[] args) {
        String experiencedPlansFile = args[0];
        String transitScheduleFile = args[1];
        String zonesShapeFile = args[2];
        String networkFile = args[3];
        String aggregationId = args[4];
        double scaleFactor = Double.parseDouble(args[5]);
        String outputFile = args[6];

        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(ZonesLoader.loadZones("zones", zonesShapeFile, "zone_id"));
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new PopulationReader(scenario).readFile(experiencedPlansFile);
        PostProcessingConfigGroup ppcg = new PostProcessingConfigGroup();
        ppcg.setRailMatrixAggregate(aggregationId);
        ppcg.setZonesId("zones");
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);
        DemandAggregator demandAggregator = new DemandAggregator(scenario, zonesCollection, ppcg, railTripsAnalyzer);
        demandAggregator
                .writeMatrix(demandAggregator.aggregateRailDemand(scaleFactor, scenario.getPopulation().getPersons().values().stream().map(HasPlansAndId::getSelectedPlan).collect(
                        Collectors.toSet())), outputFile);

    }

    public void aggregateAndWriteMatrix(double scalefactor, String outputMatrixFile, String stationToStationFile, String tripsPerMunFile, String tripsPerMSRFile) {
        Collection<Plan> experiencedPlans = experiencedPlansService.getExperiencedPlans().values();
        aggregateAndWriteMatrix(scalefactor, outputMatrixFile, stationToStationFile, tripsPerMunFile, tripsPerMSRFile, experiencedPlans);

    }

    void aggregateAndWriteMatrix(double scalefactor, String outputMatrixFile, String stationToStationFile, String tripsPerMunFile, String tripsPerMSRFile, Collection<Plan> experiencedPlans) {
        LOG.info("aggregating Rail Demand");
        float[][] matrix = aggregateRailDemand(scalefactor, experiencedPlans);
        LOG.info("aggregating Trip Demand");
        aggregateTripDemand(scalefactor, experiencedPlans);
        LOG.info("Writing Trip Demand aggregate files.");
        writeMatrix(matrix, outputMatrixFile);
        writeStationToStationDemand(stationToStationFile);
        writeTripDemand("mun_id", "mun_name", tripsPerMunFile);
        writeTripDemand("msr_id", "msr_name", tripsPerMSRFile);
        LOG.info("Done.");
        odRailDemandMatrix.clear();
        allModesOdDemand.clear();
    }

    private void writeTripDemand(String aggregationString, String aggregationStringName, String outputfile) {
        ZonesImpl zonesImpl = (ZonesImpl) zones;
        Map<Id<Zone>, String> zoneAggregate = zonesImpl.getZones().stream().collect(Collectors.toMap(zone -> zone.getId(), zone -> String.valueOf(zone.getAttribute(aggregationString))));
        Map<String, String> zoneAggregateNameString = zonesImpl.getZones().stream().collect(Collectors.toMap(zone -> String.valueOf(zone.getAttribute(aggregationString)), zone -> String.valueOf(zone.getAttribute(aggregationStringName)), (a, b) -> a));
        zoneAggregateNameString.put(OUTSIDE_ZONE, "Outside");
        zoneAggregate.put(OUTSIDE_ZONE_ID, OUTSIDE_ZONE);
        Map<String, Map<String, ODTravelInfo>> aggregatedAllModesOdDemand = new HashMap<>();
        Set<String> allModes = new HashSet<>();
        allModesOdDemand.forEach((zoneId, toFlow) -> {
            String fromAggregate = zoneAggregate.get(zoneId);
            toFlow.forEach((toZoneId, flow) -> {
                String toAggregate = zoneAggregate.get(toZoneId);
                ODTravelInfo aggregatedFlow = aggregatedAllModesOdDemand
                        .computeIfAbsent(fromAggregate, a -> new HashMap<>())
                        .computeIfAbsent(toAggregate, b -> new ODTravelInfo(new HashMap<>(), new HashMap<>(), new HashMap<>()));
                flow.travelDistancePerMode().forEach((mode, distance) -> {
                    allModes.add(mode);
                    double travelTime = flow.travelTimePerMode().get(mode).doubleValue();
                    double demand = flow.demandPerMode().get(mode).doubleValue();
                    aggregatedFlow.addTravelInfo(mode, demand, travelTime, distance.doubleValue());
                });

            });
        });
        List<String> sortedModes = new ArrayList<>(allModes);
        Collections.sort(sortedModes);
        String[] header = new String[4 + sortedModes.size() * 3];
        header[0] = "from_" + aggregationString;
        header[1] = "from_" + aggregationStringName;
        header[2] = "to_" + aggregationString;
        header[3] = "to_" + aggregationStringName;
        int i = 4;
        for (String mode : sortedModes) {
            header[i] = mode + "_demand";
            header[i + 1] = mode + "_travelDistance_km";
            header[i + 2] = mode + "_average_travelTime";
            i = i + 3;
        }
        try (CSVWriter writer = new CSVWriter(null, header, outputfile)) {
            for (var fromZoneFlows : aggregatedAllModesOdDemand.entrySet()) {
                for (var toZoneFlows : fromZoneFlows.getValue().entrySet()) {
                    writer.set(header[0], fromZoneFlows.getKey());
                    writer.set(header[1], zoneAggregateNameString.get(fromZoneFlows.getKey()));
                    writer.set(header[2], toZoneFlows.getKey());
                    writer.set(header[3], zoneAggregateNameString.get(toZoneFlows.getKey()));
                    var info = toZoneFlows.getValue();
                    for (String mode : sortedModes) {
                        double demand = info.getDemand(mode);
                        double distance = info.getTravelDistane(mode);
                        double travelTime = info.getTravelTime(mode);
                        String averageTravelTime = demand > 0.0 ? String.valueOf((int) Math.round(travelTime / demand)) : "";
                        writer.set(mode + "_demand", String.valueOf(demand));
                        writer.set(mode + "_travelDistance_km", String.valueOf((int) Math.round(distance * 0.001)));
                        writer.set(mode + "_average_travelTime", averageTravelTime);
                    }
                    writer.writeRow();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    void aggregateTripDemand(double scalefactor, Collection<Plan> experiencedPlans) {
        //for (Plan plan : experiencedPlans)

        experiencedPlans.parallelStream().forEach(plan ->
        {
            for (Trip trip : TripStructureUtils.getTrips(plan)) {
                Coord startCoord = SBBTripsExtension.getCoordFromActivity(trip.getOriginActivity(), scenario);
                Zone startZone = zones.findZone(startCoord);
                Id<Zone> startZoneId = startZone != null ? startZone.getId() : OUTSIDE_ZONE_ID;

                Coord endCoord = SBBTripsExtension.getCoordFromActivity(trip.getDestinationActivity(), scenario);
                Zone endZone = zones.findZone(endCoord);
                Id<Zone> endZoneId = endZone != null ? endZone.getId() : OUTSIDE_ZONE_ID;

                double travelTime = trip.getLegsOnly().stream().mapToDouble(leg -> leg.getRoute().getTravelTime().orElse(0)).sum();
                double travelDistance = trip.getLegsOnly().stream().mapToDouble(leg -> leg.getRoute().getDistance()).sum();
                String mainMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                allModesOdDemand.computeIfAbsent(startZoneId, a -> new ConcurrentHashMap<>())
                        .computeIfAbsent(endZoneId, a -> new ODTravelInfo(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>()))
                        .addTravelInfo(mainMode, scalefactor, travelTime * scalefactor, travelDistance * scalefactor);


            }
        });
    }

    public void writeStationToStationDemand(String outputFile) {
        String from = "from_station";
        String fromName = "from_station_name";
        String fromZone = "from_station_zone";

        String to = "to_station";
        String toName = "to_station_name";
        String toZone = "to_station_zone";


        String trips = "rail_trips";
        String pkm = "rail_pkm";
        String travel_time = "average_travel_time";
        String number_of_transfers = "number_of_rail_transfers";
        try (CSVWriter writer = new CSVWriter(null, new String[]{from, fromName, fromZone, to, toName, toZone, trips, pkm, travel_time, number_of_transfers}, outputFile)) {
            for (var entry : this.odRailDemandMatrix.entrySet()) {
                String fromZoneId = this.zoneStop.get(entry.getKey()).toString();
                String fromStationName = String.valueOf(scenario.getTransitSchedule().getFacilities().get(entry.getKey()).getName());
                for (var stopEntry : entry.getValue().entrySet()) {
                    String toZoneId = this.zoneStop.get(stopEntry.getKey()).toString();
                    String toStationName = String.valueOf(scenario.getTransitSchedule().getFacilities().get(stopEntry.getKey()).getName());

                    writer.set(from, entry.getKey().toString());
                    writer.set(fromZone, fromZoneId);
                    writer.set(fromName, fromStationName);
                    writer.set(toName, toStationName);
                    writer.set(toZone, toZoneId);
                    writer.set(to, stopEntry.getKey().toString());

                    var travelInfo = stopEntry.getValue();
                    writer.set(pkm, String.valueOf((int) Math.round(travelInfo.travelDistance().doubleValue())));
                    writer.set(trips, travelInfo.demand().toString());
                    writer.set(travel_time, String.valueOf((int) Math.round(travelInfo.travelTime().doubleValue() / travelInfo.demand().doubleValue())));
                    writer.set(number_of_transfers, String.valueOf((int) Math.round(travelInfo.numberOfRailTransfers().doubleValue() / travelInfo.demand().doubleValue())));
                    writer.writeRow();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeMatrix(float[][] matrix, String outputfile) {
        List<String> header = new ArrayList<>();
        header.add("from");
        header.addAll(aggregateZones);
        try (CSVWriter writer = new CSVWriter(null, header.toArray(new String[header.size()]), outputfile)) {
            for (int from = 0; from < aggregateZones.size(); from++) {
                String currentFrom = aggregateZones.get(from);
                writer.set("from", currentFrom);
                for (int to = 0; to < aggregateZones.size(); to++) {
                    writer.set(aggregateZones.get(to), String.valueOf(matrix[from][to]));
                }
                writer.writeRow();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private float[][] aggregateRailDemand(double scaleFactor, Collection<Plan> plans) {
        float[][] matrix = new float[aggregateZones.size()][aggregateZones.size()];
        for (Plan plan : plans) {
            for (Trip trip : TripStructureUtils.getTrips(plan)) {
                RailTripsAnalyzer.RailTravelInfo od = railTripsAnalyzer.getRailTravelInfo(trip);
                if (od != null) {
                    String fromZone = aggregateZoneStop.get(od.fromStation());
                    String toZone = aggregateZoneStop.get(od.toStation());
                    matrix[aggregateZones.indexOf(fromZone)][aggregateZones.indexOf(toZone)] += scaleFactor;

                    RailODTravelInfo travelInfo = this.odRailDemandMatrix.computeIfAbsent(od.fromStation(), a -> new TreeMap<>()).computeIfAbsent(od.toStation(), b -> new RailODTravelInfo(new MutableDouble(), new MutableDouble(), new MutableDouble(), new MutableDouble()));
                    travelInfo.demand.add(scaleFactor);
                    travelInfo.travelDistance().add(scaleFactor * od.distance() * 0.001);
                    travelInfo.travelTime().add(scaleFactor * od.railTravelTime());
                    travelInfo.numberOfRailTransfers().add(scaleFactor * od.numberOfTransfers());
                }
            }
        }
        return matrix;
    }

    record RailODTravelInfo(MutableDouble demand, MutableDouble travelTime, MutableDouble travelDistance, MutableDouble numberOfRailTransfers) {
    }

    record ODTravelInfo(Map<String, MutableDouble> demandPerMode, Map<String, MutableDouble> travelTimePerMode,
                        Map<String, MutableDouble> travelDistancePerMode) {
        void addTravelInfo(String mode, double demand, double travelTime, double travelDistance) {
            demandPerMode.computeIfAbsent(mode, m -> new MutableDouble()).add(demand);
            travelTimePerMode.computeIfAbsent(mode, m -> new MutableDouble()).add(travelTime);
            travelDistancePerMode.computeIfAbsent(mode, m -> new MutableDouble()).add(travelDistance);

        }

        double getDemand(String mode) {
            return demandPerMode.getOrDefault(mode, new MutableDouble()).doubleValue();
        }

        double getTravelDistane(String mode) {
            return travelDistancePerMode().getOrDefault(mode, new MutableDouble()).doubleValue();
        }

        double getTravelTime(String mode) {
            return travelTimePerMode().getOrDefault(mode, new MutableDouble()).doubleValue();
        }
    }

    private static class StringNumberComparator implements Comparator<String> {

        @Override
        public int compare(String s, String t1) {
            int s0 = Integer.MAX_VALUE;
            int s1 = Integer.MAX_VALUE;
            try {
                s0 = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
            try {
                s1 = Integer.parseInt(t1);
            } catch (NumberFormatException ignored) {
            }
            if (s0 != s1) {
                return Integer.compare(s0, s1);
            } else {
                return s.compareTo(t1);
            }
        }
    }
}
