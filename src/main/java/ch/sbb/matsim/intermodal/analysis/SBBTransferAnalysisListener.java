package ch.sbb.matsim.intermodal.analysis;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.csv.CSVWriter;
import java.awt.Font;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import org.apache.commons.math3.stat.Frequency;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.logging.log4j.LogManager;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.BoxAndWhiskerToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.EventsToLegs;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class SBBTransferAnalysisListener implements IterationEndsListener {

    private final Set<String> monitoredPtModes;
    private final boolean writePng;
    private final HashSet<String> monitoredModes;
    private final Map<String, Map<String, DescriptiveStatistics>> transferStats = new ConcurrentHashMap<>();
    private final Set<Id<TransitRoute>> railLines;
    private final Frequency railTransfers;
    private final Frequency ptTransfers;
    private final Frequency ptOnlyTransfers;
    private final double[] pt_pkm = new double[4];

    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    private MatsimServices services;

    @Inject
    public SBBTransferAnalysisListener(Scenario scenario) {
        Config config = scenario.getConfig();
        Set<String> monitoredAccessEgressModes = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class)
                .getIntermodalAccessEgressParameterSets()
                .stream()
                .map(IntermodalAccessEgressParameterSet::getMode)
                .collect(Collectors.toSet());
        monitoredAccessEgressModes.remove(SBBModes.ACCESS_EGRESS_WALK);
        monitoredPtModes = config.transit().getTransitModes();
        writePng = config.controler().isCreateGraphs();
        railLines = scenario.getTransitSchedule().getTransitLines().values()
                .stream()
                .flatMap(l -> l.getRoutes().values().stream())
                .filter(transitRoute -> transitRoute.getTransportMode().equals(PTSubModes.RAIL))
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
        monitoredModes = new HashSet<>(monitoredAccessEgressModes);
        monitoredModes.addAll(monitoredPtModes);
        railTransfers = new Frequency();
        ptTransfers = new Frequency();
        ptOnlyTransfers = new Frequency();
        initializeStats();
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent iterationEndsEvent) {
        initializeStats();
        analyseTransfers(experiencedPlansService.getExperiencedPlans().values(), services.getControlerIO().getIterationFilename(iterationEndsEvent.getIteration(), "ptTransferStats"));
    }

    private void initializeStats() {
        for (String mode : monitoredModes) {
            final Map<String, DescriptiveStatistics> modeMap = new HashMap<>();
            monitoredModes.forEach(m -> modeMap.put(m, new DescriptiveStatistics()));
            transferStats.put(mode, modeMap);
        }
        ptTransfers.clear();
        ptOnlyTransfers.clear();
        railTransfers.clear();
        pt_pkm[0] = 0.;
        pt_pkm[1] = 0.;
        pt_pkm[2] = 0.;
        pt_pkm[3] = 0.;

    }

    public void analyseTransfers(Collection<Plan> experiencedPlans, String iterationFilename) {
        experiencedPlans.stream()
                .flatMap(a -> TripStructureUtils.getTrips(a).stream())
                .filter(trip -> trip.getLegsOnly().stream().anyMatch(leg -> monitoredPtModes.contains(leg.getMode())))
                .forEach(trip -> {
                    pt_pkm[3]++;
                    int ptLegs = 0;
                    int raillegs = 0;
                    double lastRelevantArrival = Double.NaN;
                    String lastMode = null;
                    for (Leg leg : trip.getLegsOnly()) {
                        pt_pkm[2] += leg.getRoute().getDistance();
                        if (monitoredModes.contains(leg.getMode())) {
                            if (lastMode != null) {

                                Double boardingTime = (Double) leg.getAttributes().getAttribute(EventsToLegs.ENTER_VEHICLE_TIME_ATTRIBUTE_NAME);
                                double transfertime = (boardingTime != null ? boardingTime : leg.getDepartureTime().seconds()) - lastRelevantArrival;
                                this.transferStats.get(lastMode).get(leg.getMode()).addValue(transfertime);

                            }
                            lastMode = leg.getMode();
                            lastRelevantArrival = leg.getDepartureTime().seconds() + leg.getTravelTime().orElse(0);
                            if (leg.getMode().equals(SBBModes.PT)) {
                                ptLegs++;
                                TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                                if (this.railLines.contains(route.getRouteId())) {
                                    raillegs++;
                                    pt_pkm[0] += route.getDistance() / 1000.;
                                } else {
                                    pt_pkm[1] += route.getDistance() / 1000.;

                                }
                            }
                        }
                    }
                    if (raillegs > 0) {
                        railTransfers.addValue(raillegs - 1);
                    } else if (ptLegs > 0) {
                        ptOnlyTransfers.addValue(ptLegs - 1);
                    }

                    if (ptLegs > 0) {
                        ptTransfers.addValue(ptLegs - 1);
                    }

                });
        writeIterationStats(iterationFilename);
    }

    private void writeIterationStats(String iterationFilename) {

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
            csvWriter.writeRow();

            csvWriter.set(fromModeD, "Average PT Trip length [km]");
            csvWriter.set(toModeD, Double.toString((pt_pkm[2] / 1000.) / pt_pkm[3]));
            csvWriter.writeRow();
            csvWriter.writeRow();
            csvWriter.set(fromModeD, "Rail PKM");
            csvWriter.set(toModeD, Integer.toString((int) Math.round(pt_pkm[0])));
            csvWriter.writeRow();

            csvWriter.set(fromModeD, "Non-Rail PT PKM");
            csvWriter.set(toModeD, Integer.toString((int) Math.round(pt_pkm[1])));
            csvWriter.writeRow();
            csvWriter.writeRow();
            csvWriter.set(fromModeD, "Rail Transfer frequency");
            csvWriter.set(n, "0");
            csvWriter.set(minTT, "1");
            csvWriter.set(meanTT, "2");
            csvWriter.set(medianTT, "3");
            csvWriter.set(p95TT, "4");
            csvWriter.set(maxTT, "total trips");
            csvWriter.writeRow();
            csvWriter.set(n, Long.toString(railTransfers.getCount(0)));
            csvWriter.set(minTT, Long.toString(railTransfers.getCount(1)));
            csvWriter.set(meanTT, Long.toString(railTransfers.getCount(2)));
            csvWriter.set(medianTT, Long.toString(railTransfers.getCount(3)));
            csvWriter.set(p95TT, Long.toString(railTransfers.getCount(4)));
            csvWriter.set(maxTT, Long.toString(railTransfers.getSumFreq()));
            csvWriter.writeRow();
            csvWriter.set(n, Double.toString(railTransfers.getPct(0)));
            csvWriter.set(minTT, Double.toString(railTransfers.getPct(1)));
            csvWriter.set(meanTT, Double.toString(railTransfers.getPct(2)));
            csvWriter.set(medianTT, Double.toString(railTransfers.getPct(3)));
            csvWriter.set(p95TT, Double.toString(railTransfers.getPct(4)));
            csvWriter.writeRow();

            csvWriter.set(fromModeD, "PT Transfer (incl. rail) frequency");
            csvWriter.set(n, "0");
            csvWriter.set(minTT, "1");
            csvWriter.set(meanTT, "2");
            csvWriter.set(medianTT, "3");
            csvWriter.set(p95TT, "4");
            csvWriter.set(maxTT, "total trips");
            csvWriter.writeRow();
            csvWriter.set(n, Long.toString(ptTransfers.getCount(0)));
            csvWriter.set(minTT, Long.toString(ptTransfers.getCount(1)));
            csvWriter.set(meanTT, Long.toString(ptTransfers.getCount(2)));
            csvWriter.set(medianTT, Long.toString(ptTransfers.getCount(3)));
            csvWriter.set(p95TT, Long.toString(ptTransfers.getCount(4)));
            csvWriter.set(maxTT, Long.toString(ptTransfers.getSumFreq()));
            csvWriter.writeRow();
            csvWriter.set(n, Double.toString(ptTransfers.getPct(0)));
            csvWriter.set(minTT, Double.toString(ptTransfers.getPct(1)));
            csvWriter.set(meanTT, Double.toString(ptTransfers.getPct(2)));
            csvWriter.set(medianTT, Double.toString(ptTransfers.getPct(3)));
            csvWriter.set(p95TT, Double.toString(ptTransfers.getPct(4)));
            csvWriter.writeRow();

            csvWriter.set(fromModeD, "PT Transfer (excl. rail) frequency");
            csvWriter.set(n, "0");
            csvWriter.set(minTT, "1");
            csvWriter.set(meanTT, "2");
            csvWriter.set(medianTT, "3");
            csvWriter.set(p95TT, "4");
            csvWriter.set(maxTT, "total trips");
            csvWriter.writeRow();
            csvWriter.set(n, Long.toString(ptOnlyTransfers.getCount(0)));
            csvWriter.set(minTT, Long.toString(ptOnlyTransfers.getCount(1)));
            csvWriter.set(meanTT, Long.toString(ptOnlyTransfers.getCount(2)));
            csvWriter.set(medianTT, Long.toString(ptOnlyTransfers.getCount(3)));
            csvWriter.set(p95TT, Long.toString(ptOnlyTransfers.getCount(4)));
            csvWriter.set(maxTT, Long.toString(ptOnlyTransfers.getSumFreq()));
            csvWriter.writeRow();
            csvWriter.set(n, Double.toString(ptOnlyTransfers.getPct(0)));
            csvWriter.set(minTT, Double.toString(ptOnlyTransfers.getPct(1)));
            csvWriter.set(meanTT, Double.toString(ptOnlyTransfers.getPct(2)));
            csvWriter.set(medianTT, Double.toString(ptOnlyTransfers.getPct(3)));
            csvWriter.set(p95TT, Double.toString(ptOnlyTransfers.getPct(4)));
            csvWriter.writeRow();
        } catch (IOException e) {
            LogManager.getLogger(getClass()).error("Error writing transfer stats.");
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

        }
    }

    public static void main(String[] args) {
        String schedule = args[0];
        String experiencedPlans = args[1];
        String config = args[2];
        String outputfile = args[3];
        Scenario scenario = ScenarioUtils.createScenario(RunSBB.buildConfig(config));
        new TransitScheduleReader(scenario).readFile(schedule);
        SBBTransferAnalysisListener sbbTransferAnalysisListener = new SBBTransferAnalysisListener(scenario);
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(scenario);
        Set<Plan> plans = new HashSet<>();
        streamingPopulationReader.addAlgorithm(person -> plans.add(person.getSelectedPlan()));
        streamingPopulationReader.readFile(experiencedPlans);
        sbbTransferAnalysisListener.analyseTransfers(plans, outputfile);
    }
}
