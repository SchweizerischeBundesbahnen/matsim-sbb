package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SBBSupplyConfigGroup;
import ch.sbb.matsim.config.ZonesListConfigGroup;
import ch.sbb.matsim.utils.SBBIntermodalAwareRouterModeIdentifier;
import ch.sbb.matsim.utils.SBBTripsToLegsAlgorithm;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.nio.file.Paths;
import java.util.*;

/**
 * A class to prepare a sim folder for a MATSim routechoice run
 * only with pt plans, and only Routechoice and Time mutation as strategies
 */
public class PrepareRouteChoiceRun {
    private final String inputPlans;

    private final String inputConfig;

    private final String transit;

    private final String zonesFile;

    private final String simFolder;

    private final TransitSchedule schedule;
    private final String selectedPlansPath;
    private final String[] modes;
    private final String slice;

    /**
     * @param inputPlans outputPlans of base run
     * @param inputConfig config of base run
     * @param transit folder containing pt supply
     * @param zonesFile zones file
     * @param simFolder sim folder for routchoice run
     */
    public PrepareRouteChoiceRun(String inputPlans, String inputConfig, String transit, String zonesFile, String simFolder, String selectedPlansPath, String[] modes, String slice) {
        this.inputPlans = inputPlans;
        this.inputConfig = inputConfig;
        this.transit = transit;
        this.zonesFile = zonesFile;
        this.simFolder = simFolder;
        this.selectedPlansPath = selectedPlansPath;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(Paths.get(transit, "transitSchedule.xml.gz").toString());
        this.schedule = scenario.getTransitSchedule();
        this.modes = modes;
        this.slice = slice;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        String inputPlans = args[0];
        String inputConfig = args[1];
        String transit = args[2];
        String zonesFile = args[3];
        String simFolder = args[4];

        String selectedPlansPath = null;
        if (args.length>5) {
            selectedPlansPath = args[5];
        }

        String[] modes = {"pt", "rail", "bus", "tram", "other"};
        if (args.length>99) {
            String regex = "[,]";
            modes = args[6].split(regex);
        }

        String slice="";
        if (args.length>7) {
            slice = args[7];
        }
        new PrepareRouteChoiceRun(inputPlans, inputConfig, transit, zonesFile, simFolder, selectedPlansPath, modes, slice).run();
    }

    private void run() {
        modifyConfig();
        selectModePlans(modes);
    }
    private void modifyConfig() {
        String outputConfig = Paths.get(simFolder, "simulation_config" + slice + ".xml").toString();
        Config config = ConfigUtils.loadConfig(inputConfig, RunSBB.getSbbDefaultConfigGroups());

        config.controller().setLastIteration(60);
        config.controller().setOutputDirectory("output_slice_" + slice);
        String runId = config.controller().getRunId();
        config.controller().setRunId(runId + ".rc_" + slice);
        PostProcessingConfigGroup postProcessing = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        postProcessing.setPopulationSlice(Integer.parseInt(slice));
        Map<String, Double> strategies = new HashMap<>();
        strategies.put("SBBTimeMutation_ReRoute", 0.25);
        strategies.put("ReRoute", 0.05);
        strategies.put("BestScore", 0.7);
        for (ReplanningConfigGroup.StrategySettings s : config.replanning().getStrategySettings()) {
            if (s.getSubpopulation().equals("regular")) {
                if (strategies.containsKey(s.getStrategyName())) {
                    s.setWeight(strategies.get(s.getStrategyName()));
                } else {
                    s.setWeight(0.0);
                }
            }
        }

        if (!transit.equals("-")) {
            config.transit().setTransitScheduleFile(Paths.get(transit, "transitSchedule.xml.gz").toString());
            config.transit().setVehiclesFile(Paths.get(transit, "transitVehicles.xml.gz").toString());
            SBBSupplyConfigGroup supp = ConfigUtils.addOrGetModule(config, SBBSupplyConfigGroup.class);
            supp.setTransitNetworkFile(Paths.get(transit, "transitNetwork.xml.gz").toString());
        }

        if (!zonesFile.equals("-")) {
            ZonesListConfigGroup zonesConfigGroup = ConfigUtils.addOrGetModule(config, ZonesListConfigGroup.class);
            for (ZonesListConfigGroup.ZonesParameterSet group : zonesConfigGroup.getZones()) {
                group.setFilename(zonesFile);
            }
        }

        PlansConfigGroup plansConfigGroup = ConfigUtils.addOrGetModule(config, PlansConfigGroup.class);
        plansConfigGroup.setInputFile("prepared/plans" + slice + ".xml.gz");


        new ConfigWriter(config).write(outputConfig);

    }

    private void selectModePlans(String[] modes) {
        Config config = ConfigUtils.loadConfig(inputConfig, RunSBB.getSbbDefaultConfigGroups());

        String prepared = Paths.get(simFolder, "prepared").toString();
        String outputPlansFile = Paths.get(prepared, "plans" + slice + ".xml.gz").toString();

        Set<String> modesToRemoveRoutes = CollectionUtils.stringArrayToSet(new String[] {"rail", "bus", "tram", "other", "pt", "car", "ride", "avtaxi", "bike", "walk_main", "walk", "non_network_walk", "ebike"});

        SBBTripsToLegsAlgorithm tripsToLegsAlgorithm = new SBBTripsToLegsAlgorithm(new SBBIntermodalAwareRouterModeIdentifier(config), modesToRemoveRoutes);

        List<Id<Person>> selectedPersons = new ArrayList<>();
        if (this.selectedPlansPath != null) {
            if (!this.selectedPlansPath.equals("-")) {
                StreamingPopulationReader selectedPersonsReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
                selectedPersonsReader.addAlgorithm(person -> {
                    selectedPersons.add(person.getId());
                });
                selectedPersonsReader.readFile(this.selectedPlansPath);
            }
        }

        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(outputPlansFile);

        StreamingPopulationReader routedReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        routedReader.addAlgorithm(person -> {
            if ((selectedPersons.isEmpty()) | selectedPersons.contains(person.getId())) {
                PersonUtils.removeUnselectedPlans(person);
                var ptlegs = TripStructureUtils.getLegs(person.getSelectedPlan());
                for (Leg l : ptlegs) {
                    if (Arrays.stream(modes).anyMatch(mode -> l.getMode().equals(mode))) {
                        tripsToLegsAlgorithm.run(person.getSelectedPlan());
                        spw.run(person);
                        break;
                    }
                }
            }
        });
        routedReader.readFile(inputPlans);

        spw.closeStreaming();
    }
}
