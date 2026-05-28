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

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.MaviHelper;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PtLinkVolumeAnalyzer {

    private static final String LINK_NO = "$LINK:NO";
    private static final String FROMNODENO = "FROMNODENO";
    private static final String LINK_ID_SIM = "LINK_ID_SIM";
    private static final String VOLUME = "VOLUME";
    private static final String[] VOLUMES_COLUMNS = new String[]{LINK_NO, FROMNODENO, LINK_ID_SIM, VOLUME};
    private static final String HEADER = "$VISION\n* Schweizerische Bundesbahnen SBB Personenverkehr Bern\n* 12/09/22\n* \n* Table: Version block\n* \n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n12.00;Att;ENG;KM\n\n* \n* Table: Links\n* \n";
    private final static Logger log = LogManager.getLogger(PtLinkVolumeAnalyzer.class);
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Set<Id<Link>> ptlinks;
    private final Network network;
    private final Population population;
    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    public PtLinkVolumeAnalyzer(RailTripsAnalyzer railTripsAnalyzer, Network network, Population population) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.network = network;
        this.population = population;
        this.ptlinks = network.getLinks().values()
                .stream()
                .filter(l -> l.getAllowedModes().stream().anyMatch(m -> SBBModes.PT_PASSENGER_MODES.contains(m)))
                .map(Identifiable::getId)
                .collect(Collectors.toSet());

    }

    private static <T extends Number> void writeFile(String outputfile, double scalefactor, Map<Id<Link>, T> ptVolumes, Network ptNetwork) {
        try (CSVWriter writer = new CSVWriter(HEADER, VOLUMES_COLUMNS, outputfile)) {
            for (Entry<Id<Link>, T> e : ptVolumes.entrySet()) {
                Id<Link> currentLinkId = e.getKey();
                int scaledVolume = (int) Math.round(e.getValue().doubleValue() * scalefactor);
                Link l = ptNetwork.getLinks().get(currentLinkId);
                if (l != null) {
                	// Set attribute for XML output
                    l.getAttributes().putAttribute(VOLUME, scaledVolume);

                    // Set standard CSV columns
                    writer.set(LINK_ID_SIM, currentLinkId.toString());
                    writer.set(VOLUME, Integer.toString(scaledVolume));

                    var visumNodeAndLink = MaviHelper.extractVisumNodeAndLinkId(currentLinkId);
                    
                    if (visumNodeAndLink != null) {
                        writer.set(FROMNODENO, Integer.toString(visumNodeAndLink.getFirst()));
                        writer.set(LINK_NO, Integer.toString(visumNodeAndLink.getSecond()));
                    } else {
                        // Handle links that don't match the Mavi/Visum naming convention (e.g. BES line)
                        // Fill these columns with 0 so the row is still written
                        writer.set(FROMNODENO, "0");
                        writer.set(LINK_NO, "0");
                    }
                    
                    // Always write the row
                    writer.writeRow();
                }
                else {
                    log.warn("Could not find link " + currentLinkId + ". Skipping.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        new NetworkWriter(ptNetwork).write(outputfile + "_network.xml.gz");
        NetworkFilterManager nfm2 = new NetworkFilterManager(ptNetwork, new NetworkConfigGroup());
        nfm2.addLinkFilter(link -> link.getAllowedModes().contains(PTSubModes.RAIL));
        Network railNet = nfm2.applyFilters();
        new NetworkWriter(railNet).write(outputfile + "_rail_network.xml.gz");
    }

    private static <T extends Number> void writeDetailedFile(String outputfile, double scalefactor, Map<Id<Link>, T> ptVolumes, Map<Id<Link>, Map<Id<TransitLine>, Long>> ptVolumesPerLine, Network ptNetwork) {
        try (CSVWriter writer = new CSVWriter(HEADER, VOLUMES_COLUMNS, outputfile)) {
            for (Entry<Id<Link>, T> e : ptVolumes.entrySet()) {
                Id<Link> currentLinkId = e.getKey();
                int scaledVolume = (int) Math.round(e.getValue().doubleValue() * scalefactor);
                Link l = ptNetwork.getLinks().get(currentLinkId);
                if (l != null) {
                    l.getAttributes().putAttribute(VOLUME, scaledVolume);

                    // Set per-line volume attributes
                    Map<Id<TransitLine>, Long> lineVolumes = ptVolumesPerLine.get(currentLinkId);
                    if (lineVolumes != null) {
                        for (Entry<Id<TransitLine>, Long> lineEntry : lineVolumes.entrySet()) {
                            int scaledLineVolume = (int) Math.round(lineEntry.getValue() * scalefactor);
                            l.getAttributes().putAttribute(VOLUME + "_" + lineEntry.getKey().toString(), scaledLineVolume);
                        }
                    }

                    writer.set(LINK_ID_SIM, currentLinkId.toString());
                    writer.set(VOLUME, Integer.toString(scaledVolume));

                    var visumNodeAndLink = MaviHelper.extractVisumNodeAndLinkId(currentLinkId);
                    if (visumNodeAndLink != null) {
                        writer.set(FROMNODENO, Integer.toString(visumNodeAndLink.getFirst()));
                        writer.set(LINK_NO, Integer.toString(visumNodeAndLink.getSecond()));
                    } else {
                        writer.set(FROMNODENO, "0");
                        writer.set(LINK_NO, "0");
                    }
                    writer.writeRow();
                } else {
                    log.warn("Could not find link " + currentLinkId + ". Skipping.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        new NetworkWriter(ptNetwork).write(outputfile + "_network.xml.gz");
        NetworkFilterManager nfm2 = new NetworkFilterManager(ptNetwork, new NetworkConfigGroup());
        nfm2.addLinkFilter(link -> link.getAllowedModes().contains(PTSubModes.RAIL));
        Network railNet = nfm2.applyFilters();
        new NetworkWriter(railNet).write(outputfile + "_rail_network.xml.gz");
    }

    public static void main(String[] args) {
        // Example usage
        String folderPrefix = args[0];
        double scaleFactor = Double.parseDouble(args[1]);
        String zonesFile = args[2];
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(ZonesLoader.loadZones("zones", zonesFile));

        String outputPlans = folderPrefix + "output_plans.xml.gz";
        String transitScheduleFile = folderPrefix + "output_transitSchedule.xml.gz";
        String networkFile = folderPrefix + "output_network.xml.gz";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new PopulationReader(scenario).readFile(outputPlans);
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);

        PtLinkVolumeAnalyzer analyzer = new PtLinkVolumeAnalyzer(railTripsAnalyzer, scenario.getNetwork(), scenario.getPopulation());
        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, scenario.getPopulation().getPersons().size());
        scenario.getPopulation()
                .getPersons()
                .values()
                .forEach(person -> experiencedPlans.put(person.getId(), person.getSelectedPlan()));
        analyzer.writePtLinkUsage(folderPrefix + "pt_volumes_rewritten.att", scaleFactor, experiencedPlans);
    }


    public Map<Id<Link>, Long> analysePtLinkUsage(Map<Id<Person>,Plan> experiencedPlans) {
        Map<Id<Link>, Long> ptUsage = experiencedPlans.values()
                .stream()
                .flatMap(p -> TripStructureUtils.getLegs(p).stream())
                .filter(l -> SBBModes.PT_PASSENGER_MODES.contains(l.getMode()))
                .map(l -> (TransitPassengerRoute) l.getRoute())
                .flatMap(r -> railTripsAnalyzer.getPtLinkIdsTraveledOn(r).stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return ptUsage;

    }

    public Map<Id<Link>, Map<Id<TransitLine>, Long>> analysePtLinkUsagePerLine(Map<Id<Person>, Plan> experiencedPlans) {
        Map<Id<Link>, Map<Id<TransitLine>, Long>> ptUsagePerLine = new HashMap<>();
        experiencedPlans.values()
                .stream()
                .flatMap(p -> TripStructureUtils.getLegs(p).stream())
                .filter(l -> SBBModes.PT_PASSENGER_MODES.contains(l.getMode()))
                .map(l -> (TransitPassengerRoute) l.getRoute())
                .forEach(route -> {
                    Id<TransitLine> lineId = route.getLineId();
                    for (Id<Link> linkId : railTripsAnalyzer.getPtLinkIdsTraveledOn(route)) {
                        ptUsagePerLine.computeIfAbsent(linkId, k -> new HashMap<>()).merge(lineId, 1L, Long::sum);
                    }
                });
        return ptUsagePerLine;
    }

    public Map<Id<Link>, Double> analysePtLinkUsageincludingDNWV(Map<Id<Person>, Plan> experiencedPlans) {
        IdMap<Person, LinkedList<Variables.MOBiTripAttributes>> additionalTripAttributes =  Variables.MOBiTripAttributes.extractTripAttributes(population);
        boolean containsDNWVValues = additionalTripAttributes.values().stream().flatMap(moBiTripAttributes -> moBiTripAttributes.stream())
                .anyMatch(mobiTripAttribute -> mobiTripAttribute.getDNWVFactor() != null);
        if (!containsDNWVValues) {
            return null;
        }

        Map<Id<Link>, Double> ptUsage = new HashMap<>();
        for (Plan plan : experiencedPlans.values()) {
            var trips = TripStructureUtils.getTrips(plan);
            var tripAttributes = additionalTripAttributes.get(plan.getPerson().getId());
            for (var trip : trips) {
                var tripAttribute = tripAttributes.poll();
                Double dnwvFactor = tripAttribute != null ? tripAttribute.getDNWVFactor() : null;
                if (dnwvFactor != null) {
                    for (var leg : trip.getLegsOnly()) {
                        if (SBBModes.PT_PASSENGER_MODES.contains(leg.getMode())) {
                            var route = (TransitPassengerRoute) leg.getRoute();
                            var ptLinkIds = railTripsAnalyzer.getPtLinkIdsTraveledOn(route);
                            for (var ptLinkId : ptLinkIds) {
                                ptUsage.merge(ptLinkId, dnwvFactor, Double::sum);
                            }
                        }
                    }
                }
            }


        }

        return ptUsage;

    }

    public void writePtLinkUsage(String outputfile, double scalefactor, Map <Id<Person>, Plan> experiencedPlans) {
        NetworkFilterManager nfm = new NetworkFilterManager(network, new NetworkConfigGroup());
        nfm.addLinkFilter(l -> this.ptlinks.contains(l.getId()));
        Network ptNetwork = nfm.applyFilters();
        var ptVolumes = analysePtLinkUsage(experiencedPlans);
        writeFile(outputfile, scalefactor, ptVolumes, ptNetwork);

        var ptVolumesPerLine = analysePtLinkUsagePerLine(experiencedPlans);
        NetworkFilterManager nfmDetailed = new NetworkFilterManager(network, new NetworkConfigGroup());
        nfmDetailed.addLinkFilter(l -> this.ptlinks.contains(l.getId()));
        Network ptNetworkDetailed = nfmDetailed.applyFilters();
        writeDetailedFile(outputfile + "_detailed", scalefactor, ptVolumes, ptVolumesPerLine, ptNetworkDetailed);

        var ptVolumesIncludingDNWV = analysePtLinkUsageincludingDNWV(experiencedPlans);
        if (ptVolumesIncludingDNWV != null) {
            writeFile(outputfile + "_dnwv", scalefactor, ptVolumesIncludingDNWV, ptNetwork);

            NetworkFilterManager nfmDnwvDetailed = new NetworkFilterManager(network, new NetworkConfigGroup());
            nfmDnwvDetailed.addLinkFilter(l -> this.ptlinks.contains(l.getId()));
            Network ptNetworkDnwvDetailed = nfmDnwvDetailed.applyFilters();
            writeDetailedFile(outputfile + "_dnwv_detailed", scalefactor, ptVolumesIncludingDNWV, ptVolumesPerLine, ptNetworkDnwvDetailed);
        }
    }

    public void writePtLinkUsage(String outputfile, double scalefactor) {
        writePtLinkUsage(outputfile, scalefactor, experiencedPlansService.getExperiencedPlans());
    }

}
