package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.RunSBB;
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
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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
    private final String mode;

    /**
     * @param inputPlans outputPlans of base run
     * @param inputConfig config of base run
     * @param transit folder containing pt supply
     * @param zonesFile zones file
     * @param simFolder sim folder for routchoice run
     */
    public PrepareRouteChoiceRun(String inputPlans, String inputConfig, String transit, String zonesFile, String simFolder, String selectedPlansPath, String mode) {
        this.inputPlans = inputPlans;
        this.inputConfig = inputConfig;
        this.transit = transit;
        this.zonesFile = zonesFile;
        this.simFolder = simFolder;
        this.selectedPlansPath = selectedPlansPath;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(Paths.get(transit, "transitSchedule.xml.gz").toString());
        this.schedule = scenario.getTransitSchedule();
        this.mode = mode;
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
        if (args.length>4) {
            selectedPlansPath = args[5];
        }
        String mode = "pt";
        if (args.length>5) {
            mode = args[6];
        }
        new PrepareRouteChoiceRun(inputPlans, inputConfig, transit, zonesFile, simFolder, selectedPlansPath, mode).run();
    }

    private void run() {
        modifyConfig();
        selectModePlans(mode);
    }
    private void modifyConfig() {
        String outputConfig = Paths.get(simFolder, "config_scoring_parsed.xml").toString();
        Config config = ConfigUtils.loadConfig(inputConfig, RunSBB.getSbbDefaultConfigGroups());

        config.controler().setLastIteration(60);
        Map<String, Double> strategies = new HashMap<>();
        strategies.put("SBBTimeMutation_ReRoute", 0.25);
        strategies.put("ReRoute", 0.05);
        strategies.put("BestScore", 0.7);
        for (StrategyConfigGroup.StrategySettings s: config.strategy().getStrategySettings()) {
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
        plansConfigGroup.setInputFile("prepared/plans.xml.gz");


        new ConfigWriter(config).write(outputConfig);

    }

    private void selectModePlans(String mode) {
        Config config = ConfigUtils.loadConfig(inputConfig, RunSBB.getSbbDefaultConfigGroups());

        String prepared = Paths.get(simFolder, "prepared").toString();
        String outputPlansFile = Paths.get(prepared, "plans.xml.gz").toString();

        Set<String> modesToRemoveRoutes = CollectionUtils.stringArrayToSet(new String[] {"pt", "car", "ride", "avtaxi", "bike", "walk_main", "walk"});

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
            if ((selectedPersons.size()==0) | selectedPersons.contains(person.getId())) {
                PersonUtils.removeUnselectedPlans(person);
                var ptlegs = TripStructureUtils.getLegs(person.getSelectedPlan());
                boolean include = false;
                for (Leg l : ptlegs) {
                    if (l.getMode().equals(mode)) {
                        include = true;
                        break;
                    }
                }
                if (include) {
                    var ptroutes = TripStructureUtils.getLegs(person.getSelectedPlan()).stream().filter(leg -> leg.getRoute().getRouteType().equals(DefaultTransitPassengerRoute.ROUTE_TYPE))
                            .map(leg -> (DefaultTransitPassengerRoute) leg.getRoute()).collect(Collectors.toSet());
                    for (DefaultTransitPassengerRoute r : ptroutes) {
                        var transitLine = schedule.getTransitLines().get(r.getLineId());
                        boolean hasRoute = false;
                        boolean hasLine = false;
                        if (transitLine != null) {
                            hasRoute = transitLine.getRoutes().containsKey(r.getRouteId());
                            hasLine = true;
                        }
                        if (!hasLine || !hasRoute) {
                            tripsToLegsAlgorithm.run(person.getSelectedPlan());
                        }
                    }
                    var trips = TripStructureUtils.getTrips(person.getSelectedPlan());
                    for (TripStructureUtils.Trip t : trips) {
                        if (t.getOriginActivity().getLinkId() != null) {
                            t.getOriginActivity().setLinkId(null);
                        }
                        if (t.getDestinationActivity().getLinkId() != null) {
                            t.getDestinationActivity().setLinkId(null);
                        }
                        if (t.getOriginActivity().getFacilityId() != null) {
                            t.getOriginActivity().setFacilityId(null);
                        }
                        if (t.getDestinationActivity().getFacilityId() != null) {
                            t.getDestinationActivity().setFacilityId(null);
                        }
                    }
                    spw.run(person);
                }
            }
        });
        routedReader.readFile(inputPlans);

        spw.closeStreaming();
    }
}
