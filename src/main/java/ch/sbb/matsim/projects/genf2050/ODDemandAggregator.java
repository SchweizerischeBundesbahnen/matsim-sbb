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

package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesImpl;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class ODDemandAggregator {

    public static final String ZZZ_OUTSIDE = "zzz_outside";
    public static final String ZONEAGG = "ZONEAGG";
    private final ArrayList<String> aggregateZones;
    private final String aggregateString;
    private final Network network;
    private final ZonesImpl zones;
    Map<String, float[][]> matrixPerMode;
    Map<String, float[][]> pkmMatrixPerMode;
    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    public ODDemandAggregator(ZonesCollection zonesCollection, Network network, final PostProcessingConfigGroup ppConfig) {
        this.zones = (ZonesImpl) zonesCollection.getZones(ppConfig.getZonesId());
        var sortedZones = new TreeSet<String>();
        this.aggregateString = ppConfig.getRailMatrixAggregate();
        zones.getZones().forEach(z -> sortedZones.add(String.valueOf(z.getAttribute(ppConfig.getRailMatrixAggregate()))));
        sortedZones.add(ZZZ_OUTSIDE);
        aggregateZones = new ArrayList<>(sortedZones);
        aggregateZones.remove("");
        this.network = network;
        network.getLinks().values().parallelStream().forEach(link -> {
            Zone z = zones.findZone(link.getCoord());
            String zoneAgg = z != null ? String.valueOf(z.getAttribute(aggregateString)) : ZZZ_OUTSIDE;
            link.getAttributes().putAttribute(ZONEAGG, zoneAgg);
        });

    }

    public static void main(String[] args) {
        String experiencedPlansFile = args[0];
        String zonesShapeFile = args[1];
        String aggregationId = args[2];
        double scaleFactor = Double.parseDouble(args[3]);
        String networkFile = args[4];
        String outputFile = args[5];
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(ZonesLoader.loadZones("zones", zonesShapeFile, "zone_id"));
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new PopulationReader(scenario).readFile(experiencedPlansFile);
        PostProcessingConfigGroup ppcg = new PostProcessingConfigGroup();
        ppcg.setRailMatrixAggregate(aggregationId);
        ppcg.setZonesId("zones");


        ODDemandAggregator odDemandAggregator = new ODDemandAggregator(zonesCollection, scenario.getNetwork(), ppcg);
        odDemandAggregator.aggregateDemand(scaleFactor, scenario.getPopulation().getPersons().values().stream().map(HasPlansAndId::getSelectedPlan).collect(Collectors.toSet()));
        for (Map.Entry<String, float[][]> entry : odDemandAggregator.getMatrixPerMode().entrySet()) {
            String outfile = outputFile + "_" + entry.getKey() + ".csv";
            odDemandAggregator.writeMatrix(entry.getValue(), outfile);
        }
        for (Map.Entry<String, float[][]> entry : odDemandAggregator.getPkmMatrixPerMode().entrySet()) {
            String outfile = outputFile + "_pkm_" + entry.getKey() + ".csv";
            odDemandAggregator.writeMatrix(entry.getValue(), outfile);
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

    private void aggregateDemand(double scaleFactor, Collection<Plan> plans) {
        SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
        this.matrixPerMode = new HashMap<>();
        this.pkmMatrixPerMode = new HashMap<>();
        List<String> modes = List.of(SBBModes.PT, SBBModes.CAR, SBBModes.AVTAXI, SBBModes.BIKE, SBBModes.RIDE, SBBModes.WALK_FOR_ANALYSIS, "sum", "miv_total");
        List<String> carmodes = List.of(SBBModes.CAR, SBBModes.AVTAXI, SBBModes.RIDE);
        for (String mode : modes) {
            float[][] matrix = new float[aggregateZones.size()][aggregateZones.size()];
            float[][] matrix2 = new float[aggregateZones.size()][aggregateZones.size()];
            matrixPerMode.put(mode, matrix);
            pkmMatrixPerMode.put(mode, matrix2);
        }

        for (Plan plan : plans) {

            for (Trip trip : TripStructureUtils.getTrips(plan)) {
                if (trip.getOriginActivity().getType().contains("freight")) continue;
                String mode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (mode == SBBModes.WALK_MAIN_MAINMODE) {
                    mode = SBBModes.WALK_FOR_ANALYSIS;
                }
                String startZoneId = network.getLinks().get(trip.getOriginActivity().getLinkId()).getAttributes().getAttribute(ZONEAGG).toString();
                String endZoneId = network.getLinks().get(trip.getDestinationActivity().getLinkId()).getAttributes().getAttribute(ZONEAGG).toString();
                double distance = trip.getLegsOnly().stream().mapToDouble(value -> value.getRoute().getDistance()).sum() / 1000.0;
                matrixPerMode.computeIfAbsent(mode, m -> new float[aggregateZones.size()][aggregateZones.size()])[aggregateZones.indexOf(startZoneId)][aggregateZones.indexOf(endZoneId)] += scaleFactor;
                pkmMatrixPerMode.computeIfAbsent(mode, m -> new float[aggregateZones.size()][aggregateZones.size()])[aggregateZones.indexOf(startZoneId)][aggregateZones.indexOf(endZoneId)] += (scaleFactor * distance);
                matrixPerMode.get("sum")[aggregateZones.indexOf(startZoneId)][aggregateZones.indexOf(endZoneId)] += scaleFactor;
                pkmMatrixPerMode.get("sum")[aggregateZones.indexOf(startZoneId)][aggregateZones.indexOf(endZoneId)] += (scaleFactor * distance);
                if (carmodes.contains(mode)) {
                    matrixPerMode.get("miv_total")[aggregateZones.indexOf(startZoneId)][aggregateZones.indexOf(endZoneId)] += scaleFactor;
                    pkmMatrixPerMode.get("miv_total")[aggregateZones.indexOf(startZoneId)][aggregateZones.indexOf(endZoneId)] += (scaleFactor * distance);
                }
            }
        }

    }

    public Map<String, float[][]> getMatrixPerMode() {
        return matrixPerMode;
    }

    public Map<String, float[][]> getPkmMatrixPerMode() {
        return pkmMatrixPerMode;
    }
}
