package ch.sbb.matsim.analysis.linkAnalysis;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.MaviHelper;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.collections.Tuple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ch.sbb.matsim.analysis.linkAnalysis.CarLinkAnalysis.*;

public class HourlyCarLinkAnalysis {

    public static void main(String[] args) {
        String runprefix = args[0];
        double scaleFactor = Double.parseDouble(args[1]);

        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(runprefix + "output_network.xml.gz");
        EventsManager manager = EventsUtils.createEventsManager();
        LinkAnalyzer linkAnalyzer = new LinkAnalyzer(network);
        manager.addHandler(linkAnalyzer);
        new MatsimEventsReader(manager).readFile(runprefix + "output_events.xml.gz");
        List<String> columns = new ArrayList<>();
        columns.add(LINK_NO);
        columns.add(FROMNODENO);
        columns.add(TONODENO);
        for (int i = 0; i < 30; i++) {
            columns.add("H_" + i);
        }
        try (CSVWriter writer = new CSVWriter(CarLinkAnalysis.HEADER, columns.toArray(new String[columns.size()]), runprefix + "hourly_link_volumes.att")) {
            for (var linkEntry : linkAnalyzer.getLinkStorage().entrySet()) {
                var link = network.getLinks().get(linkEntry.getKey());
                final String matsimToNode = link.getToNode().getId().toString().startsWith("C_") ? link.getToNode().getId().toString().substring(2) : link.getToNode().getId().toString();

                int[] volumes = linkEntry.getValue();
                if (Arrays.stream(volumes).sum() > 0) {
                    String[] visumLinkIds = link.getId().toString().split("-");
                    for (int i = 0; i < visumLinkIds.length; i++) {
                        String visumLinkId = visumLinkIds[i];
                        Tuple<Integer, Integer> visumLinkNodeIds = MaviHelper.extractVisumNodeAndLinkId(visumLinkId);
                        if (visumLinkNodeIds != null) {
                            final String visumFromNode = String.valueOf(visumLinkNodeIds.getFirst());
                            String visumNo = String.valueOf(visumLinkNodeIds.getSecond());
                            final String visumToNode = i < visumLinkIds.length - 1 ? String.valueOf(MaviHelper.extractVisumNodeAndLinkId(visumLinkIds[i + 1]).getFirst()) : matsimToNode;
                            writer.set(LINK_NO, visumNo);
                            writer.set(TONODENO, visumFromNode);
                            writer.set(FROMNODENO, visumToNode);
                            setVolumes(writer, volumes, scaleFactor);
                            writer.writeRow();
                        }

                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private static void setVolumes(CSVWriter writer, int[] volumes, double scaleFactor) {
        for (int i = 0; i < 30; i++) {
            writer.set("H_" + i, Integer.toString((int) (scaleFactor * volumes[i])));
        }
    }


    static class LinkAnalyzer implements LinkEnterEventHandler, VehicleEntersTrafficEventHandler {

        private final Map<Id<Link>, int[]> linkStorage;

        LinkAnalyzer(Network network) {
            linkStorage = network.getLinks().values().stream().collect(Collectors.toMap(link -> link.getId(), link -> new int[30]));

        }

        @Override
        public void handleEvent(LinkEnterEvent event) {
            handleLink(event.getLinkId(), event.getTime());

        }

        public Map<Id<Link>, int[]> getLinkStorage() {
            return linkStorage;
        }

        private void handleLink(Id<Link> linkId, double time) {
            if (!linkId.toString().endsWith("_pt")) {
                int hour = (int) (time / 3600);
                if (hour > 29) hour = 29;
                linkStorage.get(linkId)[hour]++;
            }
        }

        @Override
        public void handleEvent(VehicleEntersTrafficEvent event) {
            handleLink(event.getLinkId(), event.getTime());

        }
    }
}
