/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.PtMergerConfigGroup;
import ch.sbb.matsim.csv.CSVReader;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkReaderMatsimV1;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleReaderV1;
import org.matsim.vehicles.VehicleWriterV1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class PTMerger {
    private final static Logger log =Logger.getLogger(PTMerger.class);

    private Scenario scenario;

    public static void main(final String[] args) {

        final Config config = ConfigUtils.loadConfig(args[0], new PtMergerConfigGroup());
        new PTMerger(config);
    }

    private PTMerger(Config config) {
        log.info("Running Public Transport Merger. Enjoy :)");

        this.scenario = ScenarioUtils.createScenario(config);
        new NetworkReaderMatsimV1(scenario.getNetwork()).readFile(config.network().getInputFile());
        new TransitScheduleReader(this.scenario).readFile(config.transit().getTransitScheduleFile());
        new VehicleReaderV1(this.scenario.getTransitVehicles()).readFile(config.transit().getVehiclesFile());


        final PtMergerConfigGroup mergerConfig = (PtMergerConfigGroup) config.getModule(PtMergerConfigGroup.GROUP_NAME);
        Config config2 = ConfigUtils.createConfig();
        Scenario scenario2 = ScenarioUtils.createScenario(config2);
        config2.transit().setTransitScheduleFile(mergerConfig.getScheduleFile());
        config2.transit().setVehiclesFile(mergerConfig.getVehiclesFile());

        new NetworkReaderMatsimV1(scenario2.getNetwork()).readFile(mergerConfig.getNetworkFile());
        new TransitScheduleReader(scenario2).readFile(config2.transit().getTransitScheduleFile());
        new VehicleReaderV1(scenario2.getTransitVehicles()).readFile(config2.transit().getVehiclesFile());

        removePt("pt", mergerConfig.getLineToDeleteFile());
        addPt(scenario2);
        write(mergerConfig.getOutput());
    }

     private void removeUnusedNodes(){
        HashSet<Id<Node>> usedNodesId = new HashSet<>();
        for(Link link: this.scenario.getNetwork().getLinks().values()){
            if(!usedNodesId.contains(link.getFromNode().getId())) usedNodesId.add(link.getFromNode().getId());
            if(!usedNodesId.contains(link.getToNode().getId())) usedNodesId.add(link.getToNode().getId());
        }

        HashSet<Id<Node>> toDelete = new HashSet<>();
        for(Node node: this.scenario.getNetwork().getNodes().values()){
            if(!usedNodesId.contains(node.getId())){
                toDelete.add(node.getId());
            }
        }

        for(Id<Node> nodeId: toDelete) {
            this.scenario.getNetwork().removeNode(nodeId);
        }
    }

    private void write(String outFolder){
        new NetworkWriter(this.scenario.getNetwork()).write(outFolder+"/network.xml.gz");
        new TransitScheduleWriter(this.scenario.getTransitSchedule()).writeFile(outFolder+"/transitSchedule.xml.gz");
        new VehicleWriterV1(this.scenario.getTransitVehicles()).writeFile(outFolder+"/transitVehicle.xml.gz");
    }

    private void removeUnusedStopFacilities(){
        HashSet<Id<TransitStopFacility>> usedStopId = new HashSet<>();
        for(TransitLine tl: this.scenario.getTransitSchedule().getTransitLines().values()){
            for(TransitRoute route: tl.getRoutes().values()){
                for(TransitRouteStop stop: route.getStops()){
                    if(!usedStopId.contains(stop.getStopFacility().getId())) usedStopId.add(stop.getStopFacility().getId());
                }
            }
        }
        HashSet<TransitStopFacility> toDelete = new HashSet<>();
        for(TransitStopFacility stop: this.scenario.getTransitSchedule().getFacilities().values()){
            if(!usedStopId.contains(stop.getId())) toDelete.add(stop);
        }

        for(TransitStopFacility stop: toDelete){
            this.scenario.getTransitSchedule().removeStopFacility(stop);
        }
    }

    private void removeUnusedTransitLinks(String networkMode){
        log.info("Network contains "+this.scenario.getNetwork().getLinks().size()+" links");

        HashSet<Id<Link>> usedLinkId = new HashSet<>();
        for(TransitLine tl: this.scenario.getTransitSchedule().getTransitLines().values()){
            for(TransitRoute route: tl.getRoutes().values()){
                for(Id<Link> linkId: route.getRoute().getLinkIds()){
                    if(!usedLinkId.contains(linkId)) usedLinkId.add(linkId);
                }
            }
        }

        for(TransitStopFacility stop: this.scenario.getTransitSchedule().getFacilities().values()){
            if(!usedLinkId.contains(stop.getLinkId())) usedLinkId.add(stop.getLinkId());
        }

        HashSet<Id<Link>> toDelete = new HashSet<>();
        for(Link link: this.scenario.getNetwork().getLinks().values()){
            if(!link.getAllowedModes().contains(networkMode)){
                continue;
            }

            if(!usedLinkId.contains(link.getId())){
                toDelete.add(link.getId());
            }
        }

        for(Id<Link> linkId: toDelete) {
            this.scenario.getNetwork().removeLink(linkId);
        }
        log.info("Network contains "+this.scenario.getNetwork().getLinks().size()+" links");
    }

    private void removePt(String networkMode, String csvFile){
        HashMap<Id<TransitLine>, String> toKeep = new HashMap<>();
        try (CSVReader lineReader = new CSVReader(new String[] {"line", "is_simba_perimeter"}, csvFile, ";")) {
            Map<String, String> row;
            while ((row = lineReader.readLine()) != null) {
                toKeep.put(Id.create(row.get("line"), TransitLine.class), row.get("is_simba_perimeter"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ArrayList<TransitLine> lineToDelete = new ArrayList<>();

        for(TransitLine tl: this.scenario.getTransitSchedule().getTransitLines().values()){
            Boolean remove = false;

            if(!toKeep.containsKey(tl.getId())){
                remove = false;
                log.info("Not in file but keeping " + tl.getId());
            }
            else if(toKeep.get(tl.getId()).equals("1")){
                remove = true;
                log.info("Removing " + tl.getId());
            }
            else{
                log.info("Keeping " + tl.getId());
            }

            if (remove) lineToDelete.add(tl);
        }

        for (TransitLine tl: lineToDelete) this.scenario.getTransitSchedule().removeTransitLine(tl);

        removeUnusedStopFacilities();
        removeUnusedTransitLinks(networkMode);
        removeUnusedNodes();
    }

    private void addLink(Network network, Link link){
         if(!network.getNodes().containsKey(link.getFromNode().getId())){
             Node node = link.getFromNode();
             HashSet<Id<Link>> inLinks = new HashSet<>(node.getInLinks().keySet());
             for(Id<Link> linkId: inLinks) {
                 node.removeInLink(linkId);
             }
             HashSet<Id<Link>> outLinks = new HashSet<>(node.getOutLinks().keySet());
             for(Id<Link> linkId: outLinks) {
                 node.removeOutLink(linkId);
             }
             network.addNode(node);
         }

        if(!network.getNodes().containsKey(link.getToNode().getId())) {
             Node node = link.getToNode();
             HashSet<Id<Link>> inLinks = new HashSet<>(node.getInLinks().keySet());
             for(Id<Link> linkId: inLinks) {
                 node.removeInLink(linkId);
             }
             HashSet<Id<Link>> outLinks = new HashSet<>(node.getOutLinks().keySet());
             for(Id<Link> linkId: outLinks) {
                 node.removeOutLink(linkId);
             }
             network.addNode(node);
        }

        network.addLink(link);
    }


    private void addPt(Scenario scenario2){
        Network network = this.scenario.getNetwork();
        Network network2 = scenario2.getNetwork();

        for(TransitLine line: scenario2.getTransitSchedule().getTransitLines().values()){

            if(line.getId().equals(Id.create("012-D-15253_RW-SG-SA-RW_Bas60l_[H]" , TransitLine.class))){
                log.info("");
            }

            for(TransitRoute route: line.getRoutes().values()){
                for(Id<Link> linkId: route.getRoute().getLinkIds()){
                    if(linkId.equals(Id.create("3004_UZ-2013_KAB_[471]", Link.class))){
                        log.info("");
                    }

                    if(!network.getLinks().containsKey(linkId)) {
                        Link link = network2.getLinks().get(linkId);
                        addLink(network, link);
                    }
                }

                for(Departure departure: route.getDepartures().values()){
                    Vehicle vehicle = scenario2.getTransitVehicles().getVehicles().get(departure.getVehicleId());
                    if(!this.scenario.getTransitVehicles().getVehicles().containsKey(vehicle.getId())){
                        if(!this.scenario.getTransitVehicles().getVehicleTypes().containsKey(vehicle.getType().getId())){
                            this.scenario.getTransitVehicles().addVehicleType(vehicle.getType());
                        }
                        this.scenario.getTransitVehicles().addVehicle(vehicle);
                    }

                }
                for(TransitRouteStop stop: route.getStops()){
                    if(!this.scenario.getTransitSchedule().getFacilities().containsKey(stop.getStopFacility().getId())){
                        if(!network.getLinks().containsKey(stop.getStopFacility().getLinkId())) {
                            Link link = network2.getLinks().get(stop.getStopFacility().getLinkId());
                            addLink(network, link);
                        }
                        this.scenario.getTransitSchedule().addStopFacility(stop.getStopFacility());
                    }
                }
            }
            this.scenario.getTransitSchedule().addTransitLine(line);
        }
    }


}
