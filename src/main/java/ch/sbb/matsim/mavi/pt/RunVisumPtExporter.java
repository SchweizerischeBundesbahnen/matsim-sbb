/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.config.variables.Filenames;
import ch.sbb.matsim.mavi.PolylinesCreator;
import ch.sbb.matsim.mavi.visum.Visum;
import ch.sbb.matsim.preparation.MobiTransitScheduleVerifiyer;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.MatsimVehicleWriter;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/***
 *
 * @author pmanser / SBB
 *
 * IMPORTANT:
 * Download the JACOB library version 1.18 and
 * set path to the library in the VM Options (e.g. -Djava.library.path="C:\Users\u225744\Downloads\jacob-1.18\jacob-1.18")
 *
 */

public class RunVisumPtExporter {

    private static final Logger log = Logger.getLogger(RunVisumPtExporter.class);

    private static final String TRANSITSCHEDULE_OUT = "transitSchedule.xml.gz";
    private static final String TRANSITVEHICLES_OUT = "transitVehicles.xml.gz";

    public static void main(String[] args) throws IOException {
        new RunVisumPtExporter().run(args[0]);
    }

    public void run(String configFile) throws IOException {
        Config config = ConfigUtils.loadConfig(configFile, new VisumPtExporterConfigGroup());
        VisumPtExporterConfigGroup exporterConfig = ConfigUtils.addOrGetModule(config, VisumPtExporterConfigGroup.class);

        // Start Visum and load version
        Visum visum = new Visum(18);
        visum.loadVersion(exporterConfig.getPathToVisum());

        if(exporterConfig.getAngebotName() != null) visum.filterForAngebot(exporterConfig.getAngebotName());

        // currently not supported: additional filter for time profiles if desired
        //if(exporterConfig.getTimeProfilFilterParams().size() != 0)
        //    visum.setTimeProfilFilter(exporterConfig.getTimeProfilFilterConditions());

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        // load stops into scenario
        VisumStopExporter stops = new VisumStopExporter(scenario);
        stops.loadStopPoints(visum, exporterConfig);

        // load minimal transfer times into scenario
        new MTTExporter(scenario).integrateMinTransferTimes(visum, stops.getStopAreasToStopPoints());

        // load vehicles into scenario
        new VehicleTypeExporter(scenario).createVehicleTypes(visum);

        // load transit lines
        TimeProfileExporter tpe = new TimeProfileExporter(scenario);
        tpe.createTransitLines(visum, exporterConfig);

        // reduce the size of the network and the schedule by taking necessary things only.
        cleanStops(scenario.getTransitSchedule());
        cleanNetwork(scenario.getTransitSchedule(), scenario.getNetwork());

        // write outputs
        createOutputPath(exporterConfig.getOutputPath());
        tpe.writeLinkSequence(exporterConfig.getOutputPath());
        writeFiles(scenario, exporterConfig.getOutputPath());
        MobiTransitScheduleVerifiyer.verifyTransitSchedule(scenario.getTransitSchedule());

        // write polyline file
        new PolylinesCreator().runPt(scenario.getNetwork(), visum, tpe.linkToVisumSequence, exporterConfig.getOutputPath());
    }

    private static void cleanStops(TransitSchedule schedule)   {
        Set<Id<TransitStopFacility>> stopsToKeep = new HashSet<>();
        for(TransitLine line: schedule.getTransitLines().values())   {
            for(TransitRoute route: line.getRoutes().values())  {
                for(TransitRouteStop stops: route.getStops()) {
                    stopsToKeep.add(stops.getStopFacility().getId());
                }
            }
        }
        Set<Id<TransitStopFacility>> stopsToRemove = schedule.getFacilities().keySet().stream().
                filter(stopId -> !stopsToKeep.contains(stopId)).
                collect(Collectors.toSet());
        stopsToRemove.forEach(stopId -> schedule.removeStopFacility(schedule.getFacilities().get(stopId)));
        log.info("removed " + stopsToRemove.size() + " unused stop facilities.");

        MinimalTransferTimes mtt =  schedule.getMinimalTransferTimes();
        MinimalTransferTimes.MinimalTransferTimesIterator itr = mtt.iterator();
        while(itr.hasNext()) {
            itr.next();
            if(!stopsToKeep.contains(itr.getFromStopId()) || !stopsToKeep.contains(itr.getToStopId()))
                mtt.remove(itr.getFromStopId(), itr.getToStopId());
        }
    }

    private static void cleanNetwork(TransitSchedule schedule, Network network) {
        Set<Id<Link>> linksToKeep = new HashSet<>();

        for(TransitLine line: schedule.getTransitLines().values())   {
            for(TransitRoute route: line.getRoutes().values())  {
                linksToKeep.add(route.getRoute().getStartLinkId());
                linksToKeep.add(route.getRoute().getEndLinkId());
                linksToKeep.addAll(route.getRoute().getLinkIds());
            }
        }
        Set<Id<Link>> linksToRemove = network.getLinks().keySet().stream().
                filter(linkId -> !linksToKeep.contains(linkId)).
                collect(Collectors.toSet());
        linksToRemove.forEach(network::removeLink);
        log.info("Removed " + linksToRemove.size() + " unused links.");

        Set<Id<Node>> nodesToRemove = network.getNodes().values().stream().
                filter(n -> n.getInLinks().size() == 0 && n.getOutLinks().size() == 0).
                map(Node::getId).
                collect(Collectors.toSet());
        nodesToRemove.forEach(network::removeNode);
        log.info("removed " + nodesToRemove.size() + " unused nodes.");
        for (Link l : network.getLinks().values()) {
            double beelineLength = CoordUtils.calcEuclideanDistance(l.getFromNode().getCoord(), l.getToNode().getCoord());
            if (l.getLength() < beelineLength) {
                if (beelineLength - l.getLength() > 1.0) {
                    log.warn(l.getId() + " has a length (" + l.getLength() + ") shorter than its beeline distance (" + beelineLength + "). Correcting this.");
                }
                l.setLength(beelineLength);
            }
            if (l.getLength() <= 0.0) {
                l.setLength(0.01);
            }
        }
    }

    private static void createOutputPath(String path)    {
        File outputPath = new File(path);
        if(!outputPath.exists())
            outputPath.mkdir();
    }

    private static void writeFiles(Scenario scenario, String outputPath)   {
        new NetworkWriter(scenario.getNetwork()).write(new File(outputPath, Filenames.PT_NETWORK).getPath());
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(new File(outputPath, TRANSITSCHEDULE_OUT).getPath());
        new MatsimVehicleWriter(scenario.getVehicles()).writeFile(new File(outputPath, TRANSITVEHICLES_OUT).getPath());
    }
}