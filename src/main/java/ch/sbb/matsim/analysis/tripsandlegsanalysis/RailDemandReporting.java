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
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.BufferedWriter;
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
    @Inject
    private ExperiencedPlansService experiencedPlansService;
    private double fqDistance = 0;
    private int fqTrips = 0;
    private double railDistance = 0.0;
    private int railtrips = 0;
    private int domesticFQTrips = 0;
    private double domesticFQDistance = 0;

    @Inject
    public RailDemandReporting(RailTripsAnalyzer railTripsAnalyzer, TransitSchedule schedule) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        prepareCategories(schedule);
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
        RailDemandReporting railDemandReporting = new RailDemandReporting(railTripsAnalyzer, scenario.getTransitSchedule());
        railDemandReporting
                .calcAndWriteDistanceReporting(outputFile, scenario.getPopulation().getPersons().values().stream().map(HasPlansAndId::getSelectedPlan).collect(Collectors.toSet()), scaleFactor);
    }

    private void reset() {
        pkmSparte.values().forEach(d -> d.setValue(0));
        pkmAbgrenzung.values().forEach(d -> d.setValue(0));
        pkmLfpCat.values().forEach(d -> d.setValue(0));
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

    private void writeRailDistanceReporting(String outputfile, double scaleFactor) {
        try (BufferedWriter bw = IOUtils.getBufferedWriter(outputfile)) {
            bw.write("SIMBA MOBi Rail Reporting");
            bw.newLine();
            bw.newLine();
            bw.write("Gesamt Bahn");
            bw.newLine();
            bw.write("PF;" + railtrips * scaleFactor);
            bw.newLine();
            bw.write("PKM;" + (int) Math.round(scaleFactor * railDistance / 1000.));
            bw.newLine();
            bw.newLine();
            bw.write("FQ-relevante Werte");
            bw.newLine();
            bw.write("PF;" + fqTrips * scaleFactor);
            bw.newLine();
            bw.write("PKM;" + (int) Math.round(scaleFactor * fqDistance / 1000.));
            bw.newLine();
            bw.newLine();
            bw.write("FQ-relevante Werte (Nur Inlandsreisen)");
            bw.newLine();
            bw.write("PF;" + domesticFQTrips * scaleFactor);
            bw.newLine();
            bw.write("PKM;" + (int) Math.round(scaleFactor * domesticFQDistance / 1000.));
            bw.newLine();
            bw.newLine();
            bw.write("PKM je Sparte");
            for (Entry<String, MutableDouble> e : pkmSparte.entrySet()) {
                bw.newLine();
                bw.write(e.getKey() + ";" + Math.round(scaleFactor * e.getValue().doubleValue()) / 1000);
            }
            bw.newLine();
            bw.newLine();
            bw.write("PKM je Abgrenzgrupe");
            for (Entry<String, MutableDouble> e : pkmAbgrenzung.entrySet()) {
                bw.newLine();
                bw.write(e.getKey() + ";" + Math.round(scaleFactor * e.getValue().doubleValue()) / 1000);
            }

            bw.newLine();
            bw.newLine();
            bw.write("PKM je LFP-Betreiberkategorie");
            for (Entry<String, MutableDouble> e : pkmLfpCat.entrySet()) {
                bw.newLine();
                bw.write(e.getKey() + ";" + Math.round(scaleFactor * e.getValue().doubleValue()) / 1000);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void prepareCategories(TransitSchedule schedule) {

        for (TransitLine line : schedule.getTransitLines().values()) {
            if (railTripsAnalyzer.isRailLine(line.getId())) {
                var route = line.getRoutes().values().stream().findFirst().get();
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
    }

    private void aggregateRailDistances(Collection<Plan> plans) {
        Set<DefaultTransitPassengerRoute> allRailRoutes = plans
                .stream()
                .flatMap(plan -> TripStructureUtils.getLegs(plan).stream())
                .filter(leg -> leg.getRoute().getRouteType().equals(DefaultTransitPassengerRoute.ROUTE_TYPE))
                .map(leg -> (DefaultTransitPassengerRoute) leg.getRoute())
                .filter(defaultTransitPassengerRoute -> railTripsAnalyzer.isRailLine(defaultTransitPassengerRoute.getLineId()))
                .collect(Collectors.toSet());

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

    }

    private void aggregateFQValues(Collection<Plan> plans) {
            plans.stream()
                .flatMap(plan -> TripStructureUtils.getTrips(plan).stream())
                .forEach(trip -> {
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
