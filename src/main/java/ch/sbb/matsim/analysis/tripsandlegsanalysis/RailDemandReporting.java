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

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Singleton
public class RailDemandReporting {

    public static final String NONE = "none";
    final Logger logger = LogManager.getLogger(getClass());
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Map<String, MutableDouble> pkmSparte = new TreeMap<>();
    private final Map<String, MutableDouble> pkmLfpCat = new TreeMap<>();
    private final Map<String, MutableDouble> pkmAbgrenzung = new TreeMap<>();

    private final Map<Id<TransitLine>, String> lineSparte = new HashMap<>();
    private final Map<Id<TransitLine>, String> lineLfpCat = new HashMap<>();
    private final Map<Id<TransitLine>, String> lineAbgrenzung = new HashMap<>();
    private final Map<Id<TransitLine>, String> line2Mode = new HashMap<>();
    private final Map<String, MutableDouble> modeDistances = new HashMap<>();
    private final Map<String, MutableInt> modeBoardings = new HashMap<>();

    @Inject
    private ExperiencedPlansService experiencedPlansService;
    private double fqDistance = 0;
    private int fqTrips = 0;
    private double railDistance = 0.0;
    private int railtrips = 0;
    private int domesticFQTrips = 0;
    private double domesticFQDistance = 0;
    private static final String runIDName = "runId";
    private static final String categoryName = "category";
    private static final String subCategoryName = "subcategory";
    private static final String unitName = "unit";
    private static final String valueName = "value";
    private final String runId;

    @Inject
    public RailDemandReporting(RailTripsAnalyzer railTripsAnalyzer, TransitSchedule schedule, Config config) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        prepareCategories(schedule);
        runId = config.controller().getRunId() != null ? config.controller().getRunId() : "";
    }

    public static void main(String[] args) {

        String folderPrefix = args[0];
        double scaleFactor = Double.parseDouble(args[1]);
        String outputFile = args[2];
        String zonesFile = args[3];
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(ZonesLoader.loadZones("zones", zonesFile));

        String experiencedPlansFile = folderPrefix + "output_experienced_plans.xml.gz";
        String transitScheduleFile = folderPrefix + "output_transitSchedule.xml.gz";
        String networkFile = folderPrefix + "output_network.xml.gz";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new PopulationReader(scenario).readFile(experiencedPlansFile);
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);
        RailDemandReporting railDemandReporting = new RailDemandReporting(railTripsAnalyzer, scenario.getTransitSchedule(), scenario.getConfig());
        railDemandReporting.calcAndWriteDistanceReporting(outputFile, scenario.getPopulation().getPersons().values().stream().map(HasPlansAndId::getSelectedPlan).collect(Collectors.toSet()), scaleFactor);
    }

    private void reset() {
        pkmSparte.values().forEach(d -> d.setValue(0));
        pkmAbgrenzung.values().forEach(d -> d.setValue(0));
        pkmLfpCat.values().forEach(d -> d.setValue(0));
        modeDistances.values().forEach(d -> d.setValue(0));
        modeBoardings.values().forEach(d -> d.setValue(0));
        fqDistance = .0;
        fqTrips = 0;
        domesticFQDistance = 0;
        domesticFQTrips = 0;
        railtrips = 0;
        railDistance = 0.0;
    }

    private void calcAndWriteDistanceReporting(String outputfile, Collection<Plan> plans, double scaleFactor) {
        reset();
        aggregateRailDistances(plans);
        writeRailDistanceReporting(outputfile, scaleFactor);
    }

    public void calcAndwriteIterationDistanceReporting(String outputfile, double scaleFactor) {
        calcAndWriteDistanceReporting(outputfile, experiencedPlansService.getExperiencedPlans().values(), scaleFactor);
    }

    private void writeRailDistanceReporting(String outputFile, double scaleFactor) {

        String pf = "PF";
        String pkm = "PKM";
        String boardings = "Einstiege";
        String[] columns = new String[]{runIDName, categoryName, subCategoryName, unitName, valueName};
        try (CSVWriter writer = new CSVWriter(null, columns, outputFile)) {

            String category = "Gesamt Bahn";
            String subCategory = "all";
            writeRow(writer, category, subCategory, pf, railtrips * scaleFactor);
            writeRow(writer, category, subCategory, pkm, (int) Math.round(scaleFactor * railDistance / 1000.));
            subCategory = "Inland";
            double domesticRailDistance = pkmAbgrenzung.values().stream().mapToDouble(value -> value.doubleValue()).sum();
            writeRow(writer, category, subCategory, pkm, (int) Math.round(scaleFactor * domesticRailDistance / 1000.));

            subCategory = "Ausland";
            writeRow(writer, category, subCategory, pkm, (int) Math.round(scaleFactor * (railDistance - domesticRailDistance) / 1000.));


            category = "FQ-relevant";
            subCategory = "Inlandsfahrten";

            writeRow(writer, category, subCategory, pf, domesticFQTrips * scaleFactor);
            writeRow(writer, category, subCategory, pkm, (int) Math.round(scaleFactor * domesticFQDistance / 1000.));

            subCategory = "Inlandsanteil von Auslandsfahrten";
            writeRow(writer, category, subCategory, pf, (fqTrips - domesticFQTrips) * scaleFactor);
            writeRow(writer, category, subCategory, pkm, (int) Math.round(scaleFactor * (fqDistance - domesticFQDistance) / 1000.));

            subCategory = "nicht relevant (In- & Ausland)";
            writeRow(writer, category, subCategory, pf, (railtrips - fqTrips) * scaleFactor);
            writeRow(writer, category, subCategory, pkm, (int) Math.round(scaleFactor * (railDistance - fqDistance) / 1000.));


            category = "Sparte";
            for (Entry<String, MutableDouble> e : pkmSparte.entrySet()) {
                writeRow(writer, category, e.getKey(), pkm, (int) Math.round(scaleFactor * e.getValue().doubleValue() / 1000.));
            }

            category = "Abgrenzgrupe";
            for (Entry<String, MutableDouble> e : pkmAbgrenzung.entrySet()) {
                writeRow(writer, category, e.getKey(), pkm, (int) Math.round(scaleFactor * e.getValue().doubleValue() / 1000.));
            }

            category = "LFP-Betreiberkategorie";
            for (Entry<String, MutableDouble> e : pkmLfpCat.entrySet()) {
                writeRow(writer, category, e.getKey(), pkm, (int) Math.round(scaleFactor * e.getValue().doubleValue() / 1000.));
            }
            category = "oeV Insgesamt";
            for (var modalDemand : modeDistances.entrySet()) {
                writeRow(writer, category, modalDemand.getKey(), boardings, modeBoardings.get(modalDemand.getKey()).intValue() * scaleFactor);
                writeRow(writer, category, modalDemand.getKey(), pkm, (int) Math.round(scaleFactor * modalDemand.getValue().doubleValue() / 1000.));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void writeRow(CSVWriter writer, String category, String subCategory, String unit, double value) {
        writer.set(runIDName, runId);
        writer.set(categoryName, category);
        writer.set(subCategoryName, subCategory);
        writer.set(unitName, unit);
        writer.set(valueName, String.valueOf(value));
        writer.writeRow();
    }

    private void prepareCategories(TransitSchedule schedule) {
        Set<String> allModes = new HashSet<>();
        for (TransitLine line : schedule.getTransitLines().values()) {
            var route = line.getRoutes().values().stream().findFirst().get();
            var mode = route.getTransportMode();
            line2Mode.put(line.getId(), mode);
            allModes.add(mode);

            if (railTripsAnalyzer.isRailLine(line.getId())) {
                String sparte = (String) route.getAttributes().getAttribute(Variables.SPARTE);
                if (sparte == null) {
                    logger.warn("No " + Variables.SPARTE + " defined for transit line " + line.getId());
                    sparte = NONE;
                }
                lineSparte.put(line.getId(), sparte);
                pkmSparte.computeIfAbsent(sparte, (a) -> new MutableDouble(0));

                String abgrenzgruppe = (String) route.getAttributes().getAttribute(Variables.ABGRENZGRUPPE);
                if (abgrenzgruppe == null) {
                    logger.warn("No " + Variables.ABGRENZGRUPPE + " defined for transit line " + line.getId());
                    abgrenzgruppe = NONE;
                }
                lineAbgrenzung.put(line.getId(), abgrenzgruppe);
                pkmAbgrenzung.computeIfAbsent(abgrenzgruppe, (a) -> new MutableDouble(0));

                String lfpCat = (String) route.getAttributes().getAttribute(Variables.BETREIBERAGGRLFP);
                if (lfpCat == null) {
                    logger.warn("No " + Variables.BETREIBERAGGRLFP + " defined for transit line " + line.getId());
                    lfpCat = NONE;
                }
                lineLfpCat.put(line.getId(), lfpCat);
                pkmLfpCat.computeIfAbsent(lfpCat, (a) -> new MutableDouble(0));

            }
        }
        allModes.forEach(mode -> modeDistances.put(mode, new MutableDouble()));
        allModes.forEach(mode -> modeBoardings.put(mode, new MutableInt()));
    }

    private void aggregateRailDistances(Collection<Plan> plans) {
        Set<DefaultTransitPassengerRoute> allPtRoutes = plans.stream().flatMap(plan -> TripStructureUtils.getLegs(plan).stream()).filter(leg -> leg.getRoute().getRouteType().equals(DefaultTransitPassengerRoute.ROUTE_TYPE)).map(leg -> (DefaultTransitPassengerRoute) leg.getRoute()).collect(Collectors.toSet());
        Set<DefaultTransitPassengerRoute> allRailRoutes = allPtRoutes.stream().filter(defaultTransitPassengerRoute -> railTripsAnalyzer.isRailLine(defaultTransitPassengerRoute.getLineId())).collect(Collectors.toSet());
        for (var route : allRailRoutes) {
            double distance = railTripsAnalyzer.getDomesticRailDistance_m(route);
            if (distance > 0.0) {
                var transitLineId = route.getLineId();
                pkmLfpCat.get(lineLfpCat.get(transitLineId)).add(distance);
                pkmAbgrenzung.get(lineAbgrenzung.get(transitLineId)).add(distance);
                pkmSparte.get(lineSparte.get(transitLineId)).add(distance);
            }
        }
        aggregateFQValues(plans);
        for (var route : allPtRoutes) {
            String mode = line2Mode.get(route.getLineId());
            modeDistances.get(mode).add(route.getDistance());
            modeBoardings.get(mode).increment();

        }
    }

    private void aggregateFQValues(Collection<Plan> plans) {
        plans.stream().flatMap(plan -> TripStructureUtils.getTrips(plan).stream()).forEach(trip -> {
            double tripFQDistance = railTripsAnalyzer.getFQDistance(trip, true);
            if (tripFQDistance > 0) {
                this.fqDistance += tripFQDistance;
                this.fqTrips++;
            }
            double domesticFQDistance = railTripsAnalyzer.getFQDistance(trip, false);
            if (domesticFQDistance > 0) {
                this.domesticFQDistance += domesticFQDistance;
                this.domesticFQTrips++;
            }

            final double tripRailDistance = railTripsAnalyzer.calcRailDistance(trip);
            this.railDistance += tripRailDistance;
            if (tripRailDistance > 0) {
                this.railtrips++;
            }
        });
    }

}
