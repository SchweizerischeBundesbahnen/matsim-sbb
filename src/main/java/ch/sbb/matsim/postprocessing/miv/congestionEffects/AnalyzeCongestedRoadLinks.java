package ch.sbb.matsim.postprocessing.miv.congestionEffects;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.vehicles.Vehicle;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class AnalyzeCongestedRoadLinks {


    private final String inputEvents;
    private final String outputFile;
    private final Network network;

    public AnalyzeCongestedRoadLinks(String inputNetwork, String inputEvents, String outputFile) {
        this.network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(inputNetwork);
        this.inputEvents = inputEvents;
        this.outputFile = outputFile;
    }

    public static void main(String[] args) {
        String inputNetwork = args[0];
        String inputEvents = args[1];
        String outputFile = args[2];

        AnalyzeCongestedRoadLinks analyzeCongestedRoadLinks = new AnalyzeCongestedRoadLinks(inputNetwork, inputEvents, outputFile);
        analyzeCongestedRoadLinks.analyze();
    }

    private void analyze() {
        Map<Id<Link>, Double> relevantLinkLoss = network.getLinks()
                .values().stream()
                .filter(link -> link.getAllowedModes().contains(SBBModes.CAR))
                .filter(link -> link.getCapacity() > 1500 && link.getFreespeed() > 50 / 3.6)
                .collect(Collectors.toMap(l -> l.getId(), l -> 0.0));

        EventsManager eventsManager = EventsUtils.createEventsManager();
        CongestionLinkHandler congestionLinkHandler = new CongestionLinkHandler(relevantLinkLoss, network);
        eventsManager.addHandler(congestionLinkHandler);
//        new MatsimEventsReader(eventsManager).readFile(inputEvents);
        writeData(outputFile, relevantLinkLoss, congestionLinkHandler.getVehiclesPerLink());


    }

    private void writeData(String outputFile, Map<Id<Link>, Double> relevantLinkLoss, Map<Id<Link>, Double> vehiclesPerLink) {
        final String LINK = "LINK";
        final String volume = "VOLUME";
        final String ABSOLUTE_LOSS = "ABSOLUTE_LOSS";
        final String AVERAGE_LOSS = "AVERAGE_LOSS";
        final String LOSS_PER_KM = "LOSS_PER_KM";
        String[] header = {LINK, LOSS_PER_KM, ABSOLUTE_LOSS, AVERAGE_LOSS, volume};
        try (CSVWriter csvWriter = new CSVWriter(null, header, outputFile)) {
            for (Map.Entry<Id<Link>, Double> e : relevantLinkLoss.entrySet()) {
                double linkVolume = vehiclesPerLink.get(e.getKey());
                csvWriter.set(LINK, e.getKey().toString());
                csvWriter.set(volume, Integer.toString((int) linkVolume));
                csvWriter.set(ABSOLUTE_LOSS, Integer.toString(e.getValue().intValue()));
                double averageLoss = e.getValue() / linkVolume;
                csvWriter.set(AVERAGE_LOSS, Double.toString(averageLoss));
                csvWriter.set(LOSS_PER_KM, Double.toString(e.getValue() / (0.001 * network.getLinks().get(e.getKey()).getLength())));
                csvWriter.writeRow();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class CongestionLinkHandler implements LinkEnterEventHandler, LinkLeaveEventHandler, VehicleLeavesTrafficEventHandler {

        private final Map<Id<Link>, Double> relevantLinkLoss;
        private final Map<Id<Link>, Double> vehiclesPerLink;
        private final Map<Id<Vehicle>, Double> lastEnterTime = new HashMap<>();
        private final Network network;

        CongestionLinkHandler(Map<Id<Link>, Double> relevantLinkLoss, Network network) {
            this.relevantLinkLoss = relevantLinkLoss;
            this.vehiclesPerLink = new HashMap<>();
            this.network = network;
            vehiclesPerLink.putAll(relevantLinkLoss);
        }

        public Map<Id<Link>, Double> getVehiclesPerLink() {
            return vehiclesPerLink;
        }

        @Override
        public void handleEvent(LinkEnterEvent event) {
            if (relevantLinkLoss.containsKey(event.getLinkId())) {
                lastEnterTime.put(event.getVehicleId(), event.getTime());
            }
        }

        @Override
        public void handleEvent(LinkLeaveEvent event) {
            Double enterTime = lastEnterTime.remove(event.getVehicleId());
            if (enterTime != null) {
                double travelTime = event.getTime() - enterTime;
                Link l = network.getLinks().get(event.getLinkId());
                double freeSpeedTravelTime = l.getLength() / l.getFreespeed();
                double timeLoss = travelTime - freeSpeedTravelTime;
                double newLoss = this.relevantLinkLoss.get(l.getId()) + timeLoss;
                this.relevantLinkLoss.put(l.getId(), newLoss);
                double vol = this.vehiclesPerLink.get(l.getId()) + 1;
                this.vehiclesPerLink.put(l.getId(), vol);
            }


        }

        @Override
        public void handleEvent(VehicleLeavesTrafficEvent event) {
            lastEnterTime.remove(event.getVehicleId());
        }
    }
}
