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
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import jakarta.inject.Inject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import static ch.sbb.matsim.routing.access.AccessEgressModule.IS_CH;

public class TripsAndDistanceStats {

    private final Network network;
    private final Population population;
    private final TransitSchedule transitSchedule;
    private final String runId;
    private final double sampleSize;
    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    public TripsAndDistanceStats(Scenario scenario) {
        this.network = scenario.getNetwork();
        this.population = scenario.getPopulation();
        this.transitSchedule = scenario.getTransitSchedule();
        this.runId = scenario.getConfig().controler().getRunId();
        this.sampleSize = ConfigUtils.addOrGetModule(scenario.getConfig(), PostProcessingConfigGroup.class).getSimulationSampleSize();

    }

    public static void main(String[] args) {
        String experiencedPlansFile = args[0];
        String networkFile = args[1];
        String transitScheduleFile = args[2];
        String zonesFile = args[3];
        String runId = args[4];
        String plansFile = args[5];
        double sampleSize = Double.parseDouble(args[6]);
        String outputFile = args[7];
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, "zone_id");
        final Config config = ConfigUtils.createConfig();
        config.controler().setRunId(runId);
        ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).setSimulationSampleSize(sampleSize);
        Scenario scenario = ScenarioUtils.createScenario(config);
        Scenario scenario2 = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        scenario.getNetwork().getLinks().values().forEach(l ->
                {
                    Zone zone = zones.findZone(l.getCoord());
                    boolean isInCH = zone != null && Integer.parseInt(zone.getId().toString()) < 700000000;
                    l.getAttributes().putAttribute(IS_CH, isInCH);
                }
        );

        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new PopulationReader(scenario).readFile(plansFile);
        new PopulationReader(scenario2).readFile(experiencedPlansFile);

        TripsAndDistanceStats tripsAndDistanceStats = new TripsAndDistanceStats(scenario);
        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, scenario2.getPopulation().getPersons().size());
        scenario2.getPopulation().getPersons().values().forEach(p -> experiencedPlans.put(p.getId(), p.getSelectedPlan()));
        tripsAndDistanceStats.analyzeAndWriteStats(outputFile + "_full.csv", outputFile + "_condensed.csv", experiencedPlans);
    }

    //trips in CH
    //trips nach & von CH
    // trips total
    // km in ch
    // km total
    // je subpopulation
    public void analyzeAndWriteStats(String fullFileName, String condensedFilename) {
        analyzeAndWriteStats(fullFileName, condensedFilename, experiencedPlansService.getExperiencedPlans());
    }

    public void analyzeAndWriteStats(String fullStatsFileName, String condensedStatsFilename, IdMap<Person, Plan> experiencedPlans) {
        Map<String, SubpopulationStats> stats = new TreeMap<>();
        analyze(stats, experiencedPlans);
        writeFile(fullStatsFileName, stats);
        addArtificialAllPaxSubpopulation(stats);
        for (String s : Variables.EXOGENEOUS_DEMAND) {
            stats.remove(s);
        }
        writeFile(condensedStatsFilename, stats);
    }

    private void writeFile(String filename, Map<String, SubpopulationStats> stats) {
        try (BufferedWriter bw = IOUtils.getBufferedWriter(filename)) {
            bw.write("Trips and Distance Stats per Subpopulation");
            bw.newLine();
            bw.write(runId);
            for (var stat : stats.values()) {
                bw.newLine();
                bw.newLine();
                bw.write("Subpopulation;" + stat.subpopulation);
                bw.newLine();
                bw.write("Domestic Trips");
                bw.newLine();
                bw.write("Mode;Trips;Share");
                double domesticTripsSum = stat.domesticTrips.values().stream().mapToInt(MutableInt::intValue).sum();
                for (Entry<String, MutableInt> e : stat.domesticTrips.entrySet()) {
                    bw.newLine();
                    bw.write(e.getKey() + ";" + e.getValue().doubleValue() / sampleSize + ";" + e.getValue().doubleValue() / domesticTripsSum);
                }
                bw.newLine();
                bw.write("All Trips");
                bw.newLine();
                bw.write("Mode;Trips;Share");
                double tripsSum = stat.tripsTotal.values().stream().mapToInt(MutableInt::intValue).sum();
                for (Entry<String, MutableInt> e : stat.tripsTotal.entrySet()) {
                    bw.newLine();
                    bw.write(e.getKey() + ";" + e.getValue().doubleValue() / sampleSize + ";" + e.getValue().doubleValue() / tripsSum);
                }
                bw.newLine();

                bw.write("Domestic Distances [km]");
                bw.newLine();
                bw.write("Mode;Distance;Share");
                double domesticDistanceSum = stat.domesticDistance.values().stream().mapToDouble(MutableDouble::doubleValue).sum();
                for (Entry<String, MutableDouble> e : stat.domesticDistance.entrySet()) {
                    bw.newLine();
                    bw.write(e.getKey() + ";" + (int) ((e.getValue().doubleValue() / 1000) / sampleSize) + ";" + e.getValue().doubleValue() / domesticDistanceSum);
                }
                bw.newLine();
                bw.write("Overall Distances");
                bw.newLine();
                bw.write("Mode;Distance;Share");
                double distanceSum = stat.overallDistance.values().stream().mapToDouble(MutableDouble::doubleValue).sum();
                for (Entry<String, MutableDouble> e : stat.overallDistance.entrySet()) {
                    bw.newLine();
                    bw.write(e.getKey() + ";" + (int) ((e.getValue().doubleValue() / 1000) / sampleSize) + ";" + e.getValue().doubleValue() / distanceSum);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addArtificialAllPaxSubpopulation(Map<String, SubpopulationStats> stats) {
        SubpopulationStats allPaxTraffic = new SubpopulationStats("All Passenger Traffic (ARE Style)");
        for (SubpopulationStats s : stats.values()) {
            if (s.subpopulation.equals("freight_road")) {
                continue;
            }
            for (Entry<String, MutableInt> e : s.tripsTotal.entrySet()) {
                allPaxTraffic.tripsTotal.computeIfAbsent(e.getKey(), (a) -> new MutableInt()).add(e.getValue());
            }

            for (Entry<String, MutableDouble> e : s.overallDistance.entrySet()) {
                allPaxTraffic.overallDistance.computeIfAbsent(e.getKey(), (a) -> new MutableDouble()).add(e.getValue());
            }
            // In the "All Passenger traffic" category, all kilometers traveled by the Regular subpopulation is defined to be domestic,
            // even if there is transit through foreign territory.
            if (s.subpopulation.equals(Variables.REGULAR)) {
                for (Entry<String, MutableInt> e : s.tripsTotal.entrySet()) {
                    allPaxTraffic.domesticTrips.computeIfAbsent(e.getKey(), (a) -> new MutableInt()).add(e.getValue());
                }
                for (Entry<String, MutableDouble> e : s.overallDistance.entrySet()) {
                    allPaxTraffic.domesticDistance.computeIfAbsent(e.getKey(), (a) -> new MutableDouble()).add(e.getValue());
                }
            } else {
                for (Entry<String, MutableInt> e : s.domesticTrips.entrySet()) {
                    allPaxTraffic.domesticTrips.computeIfAbsent(e.getKey(), (a) -> new MutableInt()).add(e.getValue());
                }
                for (Entry<String, MutableDouble> e : s.domesticDistance.entrySet()) {
                    allPaxTraffic.domesticDistance.computeIfAbsent(e.getKey(), (a) -> new MutableDouble()).add(e.getValue());
                }
            }

        }
        stats.put("xx", allPaxTraffic);
    }

    private void analyze(Map<String, SubpopulationStats> statsPerSubpopulation, IdMap<Person, Plan> experiencedPlans) {
        for (Entry<Id<Person>, Plan> e : experiencedPlans.entrySet()) {
            String subpopulation = PopulationUtils.getSubpopulation(population.getPersons().get(e.getKey()));
            if (subpopulation == null) {
                subpopulation = "none";
            }
            String finalSubpopulation = subpopulation;
            SubpopulationStats stats = statsPerSubpopulation.computeIfAbsent(subpopulation, (a) -> new SubpopulationStats(finalSubpopulation));
            for (Trip trip : TripStructureUtils.getTrips(e.getValue())) {
                Link startLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
                Link endLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
                boolean domestic = (isSwiss(startLink) && isSwiss(endLink));
                String mainmode = findMainMode(trip.getLegsOnly());
                if (domestic) {
                    stats.domesticTrips.computeIfAbsent(mainmode, (a) -> new MutableInt()).increment();
                }
                stats.tripsTotal.computeIfAbsent(mainmode, (a) -> new MutableInt()).increment();
                for (Leg leg : trip.getLegsOnly()) {
                    Link routeStartLink = network.getLinks().get(leg.getRoute().getStartLinkId());
                    Link routeEndLink = network.getLinks().get(leg.getRoute().getEndLinkId());
                    if (leg.getRoute() instanceof NetworkRoute) {
                        NetworkRoute nr = (NetworkRoute) leg.getRoute();
                        Tuple<Double, Double> distances = calcNetworkRouteDistances(nr, routeStartLink, routeEndLink);
                        stats.domesticDistance.computeIfAbsent(mainmode, (a) -> new MutableDouble()).add(distances.getFirst());
                        stats.overallDistance.computeIfAbsent(mainmode, (a) -> new MutableDouble()).add(distances.getSecond());
                    } else if (leg.getRoute() instanceof TransitPassengerRoute) {
                        TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                        TransitRoute transitRoute = transitSchedule.getTransitLines().get(route.getLineId()).getRoutes().get(route.getRouteId());
                        Tuple<Double, Double> distances = calcTransitDistances(transitRoute, transitSchedule.getFacilities().get(route.getAccessStopId()),
                                transitSchedule.getFacilities().get(route.getEgressStopId()), network);
                        stats.domesticDistance.computeIfAbsent(mainmode, (a) -> new MutableDouble()).add(distances.getFirst());
                        stats.overallDistance.computeIfAbsent(mainmode, (a) -> new MutableDouble()).add(distances.getSecond());
                    } else {
                        //generic or teleported route
                        double domesticDistance;
                        double totalDistance;
                        if (isSwiss(routeStartLink) && isSwiss(routeEndLink)) {
                            domesticDistance = totalDistance = leg.getRoute().getDistance();

                        } else {
                            totalDistance = leg.getRoute().getDistance();
                            domesticDistance = totalDistance / 2.0;
                        }

                        stats.domesticDistance.computeIfAbsent(mainmode, (a) -> new MutableDouble()).add(domesticDistance);
                        stats.overallDistance.computeIfAbsent(mainmode, (a) -> new MutableDouble()).add(totalDistance);
                    }

                }
            }
        }
    }

    private Tuple<Double, Double> calcNetworkRouteDistances(NetworkRoute nr, Link routeStartLink, Link routeEndLink) {
        double domesticDistance = 0;
        double totalDistance = 0;
        if (isSwiss(routeStartLink)) {
            domesticDistance += routeStartLink.getLength();
        }
        if (isSwiss(routeEndLink)) {
            domesticDistance += routeEndLink.getLength();
        }
        totalDistance += routeEndLink.getLength();
        totalDistance += routeStartLink.getLength();
        for (Link l : NetworkUtils.getLinks(network, nr.getLinkIds())) {
            if (isSwiss(l)) {
                domesticDistance += routeStartLink.getLength();
            }
            totalDistance += l.getLength();
        }

        return new Tuple<>(domesticDistance, totalDistance);
    }

    private Tuple<Double, Double> calcTransitDistances(TransitRoute tr, TransitStopFacility accessFacility, TransitStopFacility egressFacility, Network network) {
        Id<Link> enterLinkId = accessFacility.getLinkId();
        Id<Link> exitLinkId = egressFacility.getLinkId();

        NetworkRoute nr = tr.getRoute();
        double distDomestic = 0;
        double dist = 0;
        boolean count = enterLinkId.equals(nr.getStartLinkId());
        for (Id<Link> linkId : nr.getLinkIds()) {
            if (count) {
                Link l = network.getLinks().get(linkId);
                if (l != null) {
                    if (isSwiss(l)) {
                        distDomestic += l.getLength();
                    }
                    dist += l.getLength();
                }
            }
            if (enterLinkId.equals(linkId)) {
                count = true;
            }
            if (exitLinkId.equals(linkId)) {
                count = false;
                break;
            }
        }
        if (count) {
            Link l = network.getLinks().get(nr.getEndLinkId());
            if (l != null){
                if (isSwiss(l)) {
                    distDomestic += l.getLength();
                }
                dist += l.getLength();
            }
        }
        return new Tuple<>(distDomestic, dist);
    }

    private String findMainMode(List<Leg> legsOnly) {
        if (legsOnly.stream().anyMatch(leg -> leg.getMode().equals(SBBModes.PT))) {
            return SBBModes.PT;
        }
        if (legsOnly.stream().anyMatch(leg -> leg.getMode().equals(SBBModes.CAR))) {
            return SBBModes.CAR;
        }
        if (legsOnly.stream().anyMatch(leg -> leg.getMode().equals(SBBModes.AVTAXI))) {
            return SBBModes.AVTAXI;
        }
        if (legsOnly.stream().anyMatch(leg -> leg.getMode().equals(SBBModes.DRT))) {
            return SBBModes.DRT;
        }
        if (legsOnly.stream().anyMatch(leg -> leg.getMode().equals(SBBModes.RIDE))) {
            return SBBModes.RIDE;
        }
        if (legsOnly.stream().anyMatch(leg -> leg.getMode().equals(SBBModes.BIKE))) {
            return SBBModes.BIKE;
        }
        return SBBModes.WALK_FOR_ANALYSIS;
    }

    private boolean isSwiss(Link link) {
        try {
            return (boolean) link.getAttributes().getAttribute(IS_CH);
        } catch (RuntimeException a) {
            a.printStackTrace();

            return false;
        }
    }

    private static class SubpopulationStats {

        final String subpopulation;
        final Map<String, MutableDouble> domesticDistance = new TreeMap<>();
        final Map<String, MutableDouble> overallDistance = new TreeMap<>();
        final Map<String, MutableInt> domesticTrips = new TreeMap<>();
        final Map<String, MutableInt> tripsTotal = new TreeMap<>();

        public SubpopulationStats(String subpopulation) {
            this.subpopulation = subpopulation;
        }
    }

}
