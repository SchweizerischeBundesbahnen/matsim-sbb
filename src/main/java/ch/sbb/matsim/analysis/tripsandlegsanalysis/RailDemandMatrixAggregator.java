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
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

@Singleton
public class RailDemandMatrixAggregator {

    public static final String OUTSIDE_ZONE = "outside";
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Map<Id<TransitStopFacility>, String> zoneStop = new HashMap<>();
    private final ArrayList<String> aggregateZones;
    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    public RailDemandMatrixAggregator(TransitSchedule schedule, ZonesCollection zonesCollection, final PostProcessingConfigGroup ppConfig, RailTripsAnalyzer railTripsAnalyzer) {
        Zones zones = zonesCollection.getZones(ppConfig.getZonesId());
        this.railTripsAnalyzer = railTripsAnalyzer;
        for (TransitStopFacility facility : schedule.getFacilities().values()) {
            Zone zone = zones.findZone(facility.getCoord());
            String aggregate = zone != null ? String.valueOf(zone.getAttribute(ppConfig.getRailMatrixAggregate())) : OUTSIDE_ZONE;
            if (aggregate.equals("-1")) {
                aggregate = OUTSIDE_ZONE;
            }
            zoneStop.put(facility.getId(), aggregate);
        }
        var sortedZones = new TreeSet<>(new StringNumberComparator());
        sortedZones.addAll(zoneStop.values());
        aggregateZones = new ArrayList<>(sortedZones);
        aggregateZones.remove("");

    }

    public void aggregateAndWriteMatrix(double scalefactor, String outputfile) {
        writeMatrix(aggregateRailDemand(scalefactor, experiencedPlansService.getExperiencedPlans().values()), outputfile);
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
                Tuple<Id<TransitStopFacility>, Id<TransitStopFacility>> od = railTripsAnalyzer.getOriginDestination(trip);
                if (od != null) {
                    String fromZone = zoneStop.get(od.getFirst());
                    String toZone = zoneStop.get(od.getSecond());
                    matrix[aggregateZones.indexOf(fromZone)][aggregateZones.indexOf(toZone)] += scaleFactor;

                }
            }
        }
        return matrix;
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
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());
        RailDemandMatrixAggregator railDemandMatrixAggregator = new RailDemandMatrixAggregator(scenario.getTransitSchedule(), zonesCollection, ppcg, railTripsAnalyzer);
        railDemandMatrixAggregator
                .writeMatrix(railDemandMatrixAggregator.aggregateRailDemand(scaleFactor, scenario.getPopulation().getPersons().values().stream().map(p -> p.getSelectedPlan()).collect(
                        Collectors.toSet())), outputFile);

    }

    private static class StringNumberComparator implements Comparator<String> {

        @Override
        public int compare(String s, String t1) {
            int s0 = Integer.MAX_VALUE;
            int s1 = Integer.MAX_VALUE;
            try {
                s0 = Integer.parseInt(s);
            } catch (NumberFormatException e) {
            }
            try {
                s1 = Integer.parseInt(t1);
            } catch (NumberFormatException e) {
            }
            if (s0 != s1) {
                return Integer.compare(s0, s1);
            } else {
                return s.compareTo(t1);
            }
        }
    }
}
