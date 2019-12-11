package ch.sbb.matsim.intermodal.analysis;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.BoxAndWhiskerToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.util.chart.ChartSaveUtils;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.misc.Time;

import javax.inject.Inject;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jbischoff / SBB
 */
public class IntermodalTransferTimeAnalyser implements PersonArrivalEventHandler, PersonDepartureEventHandler, PersonEntersVehicleEventHandler, ActivityEndEventHandler {


    private final Set<String> monitoredAccessEgressModes;
    private final Set<String> monitoredPtModes;
    private final Set<String> monitoredModes;
    private final Map<String, Map<String, DescriptiveStatistics>> transferStats = new HashMap<>();
    private final boolean writePng;
    private Map<Id<Person>, IntermodalTransfer> openTransfers = new HashMap<>();

    @Inject
    public IntermodalTransferTimeAnalyser(Config config, EventsManager eventsManager) {
        eventsManager.addHandler(this);
        monitoredAccessEgressModes = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class)
                .getIntermodalAccessEgressParameterSets()
                .stream()
                .map(c -> c.getMode())
                .collect(Collectors.toSet());
        monitoredPtModes = config.transit().getTransitModes();
        writePng = config.controler().isCreateGraphs();

        monitoredModes = new HashSet<>(monitoredAccessEgressModes);
        monitoredModes.addAll(monitoredPtModes);
        initializeTransferStats();
    }

    public IntermodalTransferTimeAnalyser(Set<String> monitoredAccessEgressModes, Set<String> monitoredPtModes) {

        this.monitoredAccessEgressModes = monitoredAccessEgressModes;
        this.monitoredPtModes = monitoredPtModes;
        writePng = true;

        monitoredModes = new HashSet<>(monitoredAccessEgressModes);
        monitoredModes.addAll(monitoredPtModes);
        initializeTransferStats();


    }

    private void initializeTransferStats() {
        for (String mode : monitoredModes) {
            final Map<String, DescriptiveStatistics> modeMap = new HashMap<>();
            monitoredModes.stream().forEach(m -> modeMap.put(m, new DescriptiveStatistics()));
            transferStats.put(mode, modeMap);
        }

    }

    private boolean isIgnoredMode(String mode) {
        return mode.equals(SBBModes.NON_NETWORK_WALK) || mode.equals(SBBModes.PT_FALLBACK_MODE);

    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if (isIgnoredMode(event.getLegMode())) return;
        if (openTransfers.containsKey(event.getPersonId())) {
            finishTransfer(event.getPersonId());
            //this will happen if a person has departed by a non network mode;
        } else if (monitoredModes.contains(event.getLegMode())) {
            IntermodalTransfer transfer = new IntermodalTransfer(event.getPersonId(), event.getTime(), event.getLegMode());
            openTransfers.put(event.getPersonId(), transfer);
        }

    }

    private void finishTransfer(Id<Person> personId) {
        IntermodalTransfer transfer = openTransfers.remove(personId);
        double transferTime = (Time.isUndefinedTime(transfer.boardingTime) ? transfer.departureTime : transfer.boardingTime) - transfer.arrivalTime;
        if (monitoredModes.contains(transfer.departureMode) && monitoredModes.contains(transfer.arrivalMode)) {
            transferStats.get(transfer.arrivalMode).get(transfer.departureMode).addValue(transferTime);
        }
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if (isIgnoredMode(event.getLegMode())) return;
        if (openTransfers.containsKey(event.getPersonId())) {
            IntermodalTransfer transfer = openTransfers.get(event.getPersonId());
            transfer.departureMode = event.getLegMode();
            transfer.departureTime = event.getTime();

        }
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        if (openTransfers.containsKey(event.getPersonId())) {
            openTransfers.get(event.getPersonId()).boardingTime = event.getTime();
            finishTransfer(event.getPersonId());

        }
    }

    public void writeIterationStats(String iterationFilename) {

        final String fromModeD = "fromMode";
        final String toModeD = "toMode";
        final String minTT = "Minimum Transfer Time";
        final String meanTT = "Mean Transfer Time";
        final String medianTT = "Median Transfer Time";
        final String p95TT = "P95 Transfer Time";
        final String maxTT = "Max Transfer Time";
        final String n = "Count";
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();


        try (CSVWriter csvWriter = new CSVWriter(null, new String[]{fromModeD, toModeD, n, minTT, meanTT, medianTT, p95TT, maxTT}, iterationFilename + ".csv")) {

            for (Map.Entry<String, Map<String, DescriptiveStatistics>> fromStats : transferStats.entrySet()) {
                String fromMode = fromStats.getKey();
                for (Map.Entry<String, DescriptiveStatistics> entry : fromStats.getValue().entrySet()) {
                    String describer = fromMode + " -> " + entry.getKey();
                    List<Double> values = Arrays.stream(entry.getValue().getValues()).boxed().collect(Collectors.toList());
                    if (!values.isEmpty()) {
                        dataset.add(values, describer, "Intermodal Transfer");
                        csvWriter.set(fromModeD, fromMode);
                        csvWriter.set(toModeD, entry.getKey());
                        csvWriter.set(minTT, Double.toString(entry.getValue().getMin()));
                        csvWriter.set(meanTT, Double.toString(entry.getValue().getMean()));
                        csvWriter.set(medianTT, Double.toString(entry.getValue().getGeometricMean()));
                        csvWriter.set(p95TT, Double.toString(entry.getValue().getPercentile(95)));
                        csvWriter.set(maxTT, Double.toString(entry.getValue().getMax()));
                        csvWriter.set(n, Long.toString(entry.getValue().getN()));

                        csvWriter.writeRow();
                    }

                }
            }


        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (writePng) {

            final CategoryAxis xAxis = new CategoryAxis("Type");
            final Font font = new Font("SansSerif", Font.BOLD, 16);
            xAxis.setLabelFont(font);
            final NumberAxis yAxis = new NumberAxis("Value");
            yAxis.setLabelFont(font);
            yAxis.setAutoRangeIncludesZero(false);
            final BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer();
            renderer.setFillBox(true);
            renderer.setMeanVisible(false);
            renderer.setDefaultToolTipGenerator(new BoxAndWhiskerToolTipGenerator());
            final CategoryPlot plot = new CategoryPlot(dataset, xAxis, yAxis, renderer);

            final JFreeChart chart = new JFreeChart(
                    "Intermodal Transfer Times",
                    new Font("SansSerif", Font.BOLD, 24),
                    plot,
                    true
            );
            chart.getLegend().setItemFont(font);
            ChartSaveUtils.saveAsPNG(chart, iterationFilename, 2048, 1536);

        }
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
        if (openTransfers.containsKey(event.getPersonId())) {
            if (!event.getActType().endsWith("interaction")) {
                openTransfers.remove(event.getPersonId());
            }
        }
    }

    @Override
    public void reset(int iteration) {
        initializeTransferStats();
    }

    private static class IntermodalTransfer {
        private final Id<Person> personId;
        private final double arrivalTime;
        private final String arrivalMode;
        double departureTime = Time.getUndefinedTime();
        double boardingTime = Time.getUndefinedTime();
        String departureMode = null;

        public IntermodalTransfer(Id<Person> personId, double arrivalTime, String arrivalMode) {
            this.personId = personId;
            this.arrivalTime = arrivalTime;
            this.arrivalMode = arrivalMode;

        }

    }
}
