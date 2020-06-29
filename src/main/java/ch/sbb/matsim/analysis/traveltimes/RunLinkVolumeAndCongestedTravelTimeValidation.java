package ch.sbb.matsim.analysis.traveltimes;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.geometry.transformations.CH1903LV03PlustoWGS84;
import org.matsim.counts.Count;
import org.matsim.counts.CountSimComparison;
import org.matsim.counts.CountSimComparisonImpl;
import org.matsim.counts.Counts;
import org.matsim.counts.MatsimCountsReader;
import org.matsim.counts.algorithms.CountSimComparisonKMLWriter;

/**
 * @author jbischoff / SBB
 */
public class RunLinkVolumeAndCongestedTravelTimeValidation {


    public static final String LINKID = "LINKID";
    public static final String COUNTNAME = "COUNTNAME";
    public static final String COORDX = "COORDX";
    public static final String COORDY = "COORDY";
    public static final String COUNTVALUE = "COUNTVALUE";
    public static final String SIMVALUE = "SIMVALUE";
    public static final String HOUR = "HOUR";

    public static void main(String[] args) throws IOException {
        String simFolder = args[0];
        String runId = args[1];
        String countsFile = args[2];
        String outputFolder = args[3];
        double scaleFactor = Double.parseDouble(args[4]);
        String travelTimeRelations = args[5];
        double startTime = Double.parseDouble(args[6]);
        new RunLinkVolumeAndCongestedTravelTimeValidation().run(simFolder, runId, countsFile, outputFolder, scaleFactor, startTime, travelTimeRelations);

    }

    public void run(String simFolder, String runId, String countsFile, String outputFolder, double scalefactor, double startTime, String travelTimeRelationsFile) throws IOException {
        Files.createDirectories(Paths.get(outputFolder));
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(simFolder + "/" + runId + ".output_network.xml.gz");
        Config config = ConfigUtils.loadConfig(simFolder + "/" + runId + ".output_config.xml");
        TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(network);
        builder.configure(config.travelTimeCalculator());
        TravelTimeCalculator ttc = builder.build();

        Counts<Link> counts = new Counts<>();
        new MatsimCountsReader(counts).readFile(countsFile);
        final Map<Id<Link>, int[]> countsLinks = counts.getCounts().keySet().stream().collect(Collectors.toMap(p -> p, p -> new int[24]));
        EventsManager eventsManager = EventsUtils.createEventsManager(ConfigUtils.createConfig());
        eventsManager.addHandler(ttc);
        eventsManager.addHandler(new LinkEnterEventHandler() {
            @Override
            public void handleEvent(LinkEnterEvent event) {
                if (countsLinks.containsKey(event.getLinkId())) {
                    int hour = (int) (event.getTime() / 3600.0);
                    if (hour > 23) {
                        hour = hour - 24;
                    }
                    countsLinks.get(event.getLinkId())[hour]++;
                }
            }
        });
        new MatsimEventsReader(eventsManager).readFile(simFolder + "/" + runId + ".output_events.xml.gz");
        writeCountsComparisons(outputFolder, scalefactor, network, counts, countsLinks);
        TransportModeNetworkFilter networkFilter = new TransportModeNetworkFilter(network);
        Network carNet = NetworkUtils.createNetwork();
        networkFilter.filter(carNet, Collections.singleton(SBBModes.CAR));
        new RunTravelTimeValidation(carNet, ttc.getLinkTravelTimes(), startTime).run(travelTimeRelationsFile, outputFolder + "/congestedTravelTimeComparison_" + startTime + ".csv");

    }

    public void writeCountsComparisons(String outputFolder, double scalefactor, Network network, Counts<Link> counts, Map<Id<Link>, int[]> countsLinks) {
        List<CountSimComparison> comparisons = new ArrayList<>();
        try (CSVWriter writer = new CSVWriter(null, new String[]{LINKID, COUNTNAME, COORDX, COORDY, HOUR, COUNTVALUE, SIMVALUE}, outputFolder + "/countcomparisons.csv")) {
            for (Count count : counts.getCounts().values()) {
                int[] simvalues = countsLinks.get(count.getId());
                Link link = network.getLinks().get(Id.createLinkId(count.getId()));
                if (link != null) {
                    int cap = (int) link.getCapacity();
                    String title = count.getCsLabel() + "\n Link ID" + link.getId() + " Category: " + NetworkUtils.getType(link);
                    SBBCountsLoadCurveGraph countsLoadCurveGraph = new SBBCountsLoadCurveGraph(cap, title);
                    for (int i = 0; i < 24; i++) {
                        double simvalue = simvalues[i] * scalefactor;
                        double countvalue = count.getVolume(i + 1).getValue();
                        writer.set(LINKID, count.getId().toString());
                        writer.set(COUNTNAME, count.getCsLabel());
                        writer.set(COORDX, Double.toString(count.getCoord().getX()));
                        writer.set(COORDY, Double.toString(count.getCoord().getY()));
                        writer.set(COUNTVALUE, Double.toString(countvalue));
                        writer.set(HOUR, Integer.toString(i + 1));
                        writer.set(SIMVALUE, Double.toString(simvalue));
                        writer.writeRow();
                        CountSimComparison<Link> countSimComparison = new CountSimComparisonImpl(count.getId(), count.getCsLabel(), i + 1, countvalue, simvalue);
                        comparisons.add(countSimComparison);

                        countsLoadCurveGraph.add2LoadCurveDataSets(countSimComparison);

                    }
                    JFreeChart chart = countsLoadCurveGraph.createChart();
                    String chartfilename = link.getId().toString() + "_" + count.getCsLabel().replace(",", "").replace("\\", "-").replace("/", "-").toLowerCase();
                    ChartUtils.writeChartAsPNG(Files.newOutputStream(Paths.get(outputFolder + "/" + chartfilename + ".png")), chart, 1200, 750);
                } else {
                    Logger.getLogger(getClass()).warn(count.getId() + " , " + count.getCsLabel() + " was not found in network, but is in counts. Skipping.");
                }

            }
        } catch (IOException e) {

        }

        new CountSimComparisonKMLWriter<>(comparisons, counts, new CH1903LV03PlustoWGS84(), "Sim-Counts-Comparison").writeFile(outputFolder + "/counts.kmz");
    }

}
