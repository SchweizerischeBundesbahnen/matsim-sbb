package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.util.*;
import java.util.stream.Collectors;

import static ch.sbb.matsim.RunSBB.getSbbDefaultConfigGroups;

/**
 * Orchestrates all modal-split analyses.
 *
 * <p>Each analysis domain is encapsulated in its own {@link TripDataCollector}.
 * This class only wires the collectors, iterates over experienced plans once, and triggers writing.
 *
 * <p>Binding (Guice): {@code bind(ModalSplitStats.class)} in RunSBB.
 */
public class ModalSplitStats {

    public static final String NO_CODE = "";

    static final List<String> ANALYSIS_SUBPOPULATIONS = List.of(
            Variables.REGULAR, Variables.AIRPORT_RAIL, Variables.AIRPORT_ROAD,
            Variables.CB_RAIL, Variables.CB_ROAD, Variables.TOURISM_RAIL,
            Variables.FREIGHT_ROAD, "foreign", Variables.CB_COMMUTER,
            Variables.LIECHTENSTEIN, Variables.TOURIST);

    @Inject private ExperiencedPlansService experiencedPlansService;
    @Inject private Population population;
    @Inject private Config config;
    @Inject private TransitSchedule transitSchedule;

    private final Zones zones;
    private final RailTripsAnalyzer railTripsAnalyzer;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Inject
    public ModalSplitStats(ZonesCollection zonesCollection, Config config, Scenario scenario) {
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        this.zones = zonesCollection.getZones(ppConfig.getZonesId());
        this.railTripsAnalyzer = new RailTripsAnalyzer(
                scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);
        // also store direct references for standalone main() use
        this.population      = scenario.getPopulation();
        this.transitSchedule = scenario.getTransitSchedule();
        this.config          = config;
    }

    // -------------------------------------------------------------------------
    // Entry points
    // -------------------------------------------------------------------------

    /** Called from SBBDefaultAnalysisListener during a simulation run. */
    public void analyzeAndWriteStats(String outputLocation) {
        analyzeAndWriteStats(outputLocation, experiencedPlansService.getExperiencedPlans());
    }

    /** Called from the standalone {@code main()} or for post-processing. */
    public void analyzeAndWriteStats(String outputLocation, Map<Id<Person>, Plan> experiencedPlans) {
        Set<String> feederModes = getFeederModes();

        Map<String, Integer> modesMap       = ModalSplitCollector.buildModesMapStatic(config);
        Map<String, Integer> modesInclRailFQ = buildModesInclRailFQ(modesMap);

        List<TripDataCollector> collectors = List.of(
                new StopBoardingCollector(transitSchedule, zones, railTripsAnalyzer, feederModes),
                new TrainStationCollector(transitSchedule, zones, railTripsAnalyzer),
                new ModalSplitCollector(config, railTripsAnalyzer, ANALYSIS_SUBPOPULATIONS),
                new FeederCollector(zones, feederModes, ANALYSIS_SUBPOPULATIONS),
                new DistanceClassCollector(modesInclRailFQ, railTripsAnalyzer, ANALYSIS_SUBPOPULATIONS),
                new ChangesCollector(railTripsAnalyzer, ANALYSIS_SUBPOPULATIONS),
                new TimesCollector(config, railTripsAnalyzer, ANALYSIS_SUBPOPULATIONS)
        );

        // single pass over all plans
        for (Map.Entry<Id<Person>, Plan> entry : experiencedPlans.entrySet()) {
            Person person = population.getPersons().get(entry.getKey());
            if (person == null) continue;
            for (TripDataCollector c : collectors) c.collect(person, entry.getValue());
        }

        // write all results
        String prefix    = outputLocation + "SBB_";
        double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class)
                .getSimulationSampleSize();
        String runId = config.controller().getRunId();
        for (TripDataCollector c : collectors) c.write(prefix, runId, sampleSize);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Set<String> getFeederModes() {
        return ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class)
                .getIntermodalAccessEgressParameterSets().stream()
                .map(SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet::getMode)
                .collect(Collectors.toSet());
    }

    static Map<String, Integer> buildModesInclRailFQ(Map<String, Integer> modesMap) {
        Map<String, Integer> result = new LinkedHashMap<>(modesMap);
        int max = result.values().stream().max(Integer::compareTo).orElse(-1);
        result.put(SBBModes.RAIL, max + 1);
        result.put("railFQ",      max + 2);
        return result;
    }

    // -------------------------------------------------------------------------
    // Standalone main()
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        String experiencedPlansFile = args[0];
        String networkFile          = args[1];
        String transitScheduleFile  = args[2];
        String zonesFile            = args[3];
        String runId                = args[4];
        String plansFile            = args[5];
        double sampleSize           = Double.parseDouble(args[6]);
        String outputFile           = args[7];
        String configFile           = args[8];

        final Config config = ConfigUtils.loadConfig(configFile, getSbbDefaultConfigGroups());
        config.controller().setRunId(runId);
        config.qsim().setEndTime(30 * 3600);
        config.controller().setOutputDirectory(outputFile);
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        ppConfig.setSimulationSampleSize(sampleSize);
        ppConfig.setZonesId("zones");

        Zones zones = ZonesLoader.loadZones("zones", zonesFile, "zone_id");
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(zones);

        Scenario scenario  = ScenarioUtils.createScenario(config);
        Scenario scenario2 = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new PopulationReader(scenario).readFile(plansFile);
        new PopulationReader(scenario2).readFile(experiencedPlansFile);

        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class,
                scenario2.getPopulation().getPersons().size());
        scenario2.getPopulation().getPersons().values()
                .forEach(p -> experiencedPlans.put(p.getId(), p.getSelectedPlan()));

        ModalSplitStats stats = new ModalSplitStats(zonesCollection, config, scenario);
        stats.analyzeAndWriteStats(config.controller().getOutputDirectory(), experiencedPlans);
    }
}
