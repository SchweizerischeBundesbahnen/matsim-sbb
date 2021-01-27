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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

@Singleton
public class RailDemandMatrixAggregator {

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
            String aggregate = zone != null ? String.valueOf(zone.getAttribute(ppConfig.getRailMatrixAggregate())) : "outside";
            zoneStop.put(facility.getId(), aggregate);
        }
        aggregateZones = new ArrayList<>(new TreeSet(zoneStop.values()));

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

}
