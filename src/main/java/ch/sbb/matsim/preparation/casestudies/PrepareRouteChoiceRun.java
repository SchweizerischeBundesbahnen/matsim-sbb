package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.SBBSupplyConfigGroup;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * A class to prepare a sim folder for a MATSim routechoice run
 * only with pt plans, and only Routechoice and Time mutation as strategies
 */
public class PrepareRouteChoiceRun {
    private final String inputPlans;

    private final String inputConfig;

    private final String transit;

    private final String simFolder;

    /**
     * @param inputPlans outputPlans of base run
     * @param inputConfig config of base run
     * @param transit folder containing pt supply
     * @param simFolder sim folder for routchoice run
     */
    public PrepareRouteChoiceRun(String inputPlans, String inputConfig, String transit, String simFolder) {
        this.inputPlans = inputPlans;
        this.inputConfig = inputConfig;
        this.transit = transit;
        this.simFolder = simFolder;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        String inputPlans = args[0];
        String inputConfig = args[1];
        String transit = args[2];
        String simFolder = args[3];
        new PrepareRouteChoiceRun(inputPlans, inputConfig, transit, simFolder).run();
    }

    private void run() {
        modifyConfig();
        selectPtTrips();
    }
    private void modifyConfig() {
        String outputConfig = Paths.get(simFolder, "config_scoring_parsed.xml").toString();
        Config config = ConfigUtils.loadConfig(inputConfig, RunSBB.getSbbDefaultConfigGroups());

        Map<String, Double> strategies = new HashMap<>();
        strategies.put("SBBTimeMutation_ReRoute", 0.25);
        strategies.put("ReRoute", 0.05);
        strategies.put("BestScore", 0.7);
        for (StrategyConfigGroup.StrategySettings s: config.strategy().getStrategySettings()) {
            if (s.getSubpopulation().equals("regular")) {
                if (strategies.containsKey(s.getStrategyName())) {
                    s.setWeight(strategies.get(s.getStrategyName()));
                    strategies.remove(s.getStrategyName());
                }
            }
        }

        for (Map.Entry<String, Double> e : strategies.entrySet()) {
            StrategyConfigGroup.StrategySettings s = new StrategyConfigGroup.StrategySettings();
            s.setWeight(e.getValue());
            s.setSubpopulation("regular");
            s.setStrategyName(e.getKey());
            config.strategy().addStrategySettings(s);

        }

        if (!transit.equals("-")) {
            config.transit().setTransitScheduleFile(Paths.get(transit, "transitSchedule.xml.gz").toString());
            config.transit().setVehiclesFile(Paths.get(transit, "transitVehicles.xml.gz").toString());
            SBBSupplyConfigGroup supp = ConfigUtils.addOrGetModule(config, SBBSupplyConfigGroup.class);
            supp.setTransitNetworkFile(Paths.get(transit, "transitNetwork.xml.gz").toString());
        }
        new ConfigWriter(config).write(outputConfig);

    }
    private void selectPtTrips() {
        String prepared = Paths.get(simFolder, "prepared").toString();
        String outputPlansFile = Paths.get(prepared, "plans.xml.gz").toString();

        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(outputPlansFile);

        StreamingPopulationReader routedReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        routedReader.addAlgorithm(person -> {
            PersonUtils.removeUnselectedPlans(person);
            var ptlegs = TripStructureUtils.getLegs(person.getSelectedPlan());
            for (Leg l: ptlegs) {
                if (l.getMode().equals("pt")) {
                    spw.run(person);
                    break;
                }
            }
        });
        routedReader.readFile(inputPlans);
    }
}
