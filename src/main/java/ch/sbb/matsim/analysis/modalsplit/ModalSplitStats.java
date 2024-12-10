package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static ch.sbb.matsim.RunSBB.getSbbDefaultConfigGroups;
import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;
import static ch.sbb.matsim.config.variables.SBBModes.PT;
import static ch.sbb.matsim.config.variables.SBBModes.RAIL;

public class ModalSplitStats {

    private final List<String> analysisSubpopulations = List.of(Variables.REGULAR, Variables.AIRPORT_RAIL, Variables.AIRPORT_ROAD, Variables.CB_RAIL, Variables.CB_ROAD, Variables.TOURISM_RAIL, Variables.FREIGHT_ROAD, "foreign", Variables.CB_COMMUTER, Variables.LIECHTENSTEIN, Variables.TOURIST);
    @Inject
    private ExperiencedPlansService experiencedPlansService;
    @Inject
    private Population population;
    @Inject
    private Config config;
    @Inject
    private TransitSchedule transitSchedule;

    private final Zones zones;
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final LongestMainModeIdentifier mainModeIdentifier = new LongestMainModeIdentifier();
    private final PopulationFactory pf = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
    private String outputLocation;
    private Map<Id<TransitStopFacility>, StopStation> stopStationsMap;
    private Map<String, Integer> modesMap;
    private Map<String, Integer> modesInclRailFQMap;
    private Map<String, Integer> feederModesMap;
    private Map<String, Integer> variablesMSMap;
    private Map<String, Integer> variablesMSFeederMap;
    private Map<String, double[][]> subpopulationDistanceMap;
    private Map<String, double[][]> subpopulationMSPFMap;
    private Map<String, double[][]> subpopulationMSPKMMap;
    private Map<String, double[][]> subpopulationAccessMSPFMap;
    private Map<String, double[][]> subpopulationAccessMSPKMMap;
    private Map<String, double[][]> subpopulationEgressMSPFMap;
    private Map<String, double[][]> subpopulationEgressMSPKMMap;
    private Map<String, Map<String, double[][]>> zonesAccessMSPFMap;
    private Map<String, Map<String, double[][]>> zonesEgressMSPFMap;
    private Map<String, Map<String, double[][]>> zonesAccessMSPkmMap;
    private Map<String, Map<String, double[][]>> zonesEgressMSPkmMap;

    private Map<String, double[][]> subpopulationChangeMap;
    private Map<String, double[][]> subpopulationChangePKMMap;
    private Map<String, int[][]> timeMap;
    private Map<String, int[][]> travelTimeMap;
    private Map<String, Integer> variablesTimeStepsMap;
    private Map<String, TrainStation> trainStationMap;
    private Map<String,double[][]> subpopulationDistanceChangeMap;
    private Map<String,double[][]> subpopulationDistanceChangePKMMap;
    private Set<String> feederModesFromConfig;
    private List<String> possibleModesAtStop;
    private List<String> possibleOriginDestinationModesAtStop;
    private Map<Id<TransitStopFacility>, Set<String>> actualModesAtStop;


    @Inject
    public ModalSplitStats(ZonesCollection zonesCollection, Config config, Scenario scenario) {
        PostProcessingConfigGroup postProcessingConfigGroup = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        this.zones = zonesCollection.getZones(postProcessingConfigGroup.getZonesId());
        this.railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);
        this.population = scenario.getPopulation();
        this.transitSchedule = scenario.getTransitSchedule();
        this.config = config;
    }

    public static void main(String[] args) {
        String experiencedPlansFile = args[0];
        String networkFile = args[1];
        String transitScheduleFile = args[2];
        String zonesFile = args[3];
        String runId = args[4];
        String plansFile = args[5];
        double sampleSize = Double.parseDouble(args[6]);
        String outputFile = args[7];
        String configFile = args[8];

        final Config config = ConfigUtils.loadConfig(configFile, getSbbDefaultConfigGroups());
        // final Config config = ConfigUtils.createConfig();
        config.controller().setRunId(runId);
        config.qsim().setEndTime(30 * 3600);
        config.controller().setOutputDirectory(outputFile);
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        ppConfig.setSimulationSampleSize(sampleSize);
        ppConfig.setZonesId("zones");
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, "zone_id");
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(zones);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Scenario scenario2 = ScenarioUtils.createScenario(config);

        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new PopulationReader(scenario).readFile(plansFile);
        new PopulationReader(scenario2).readFile(experiencedPlansFile);

        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, scenario2.getPopulation().getPersons().size());
        scenario2.getPopulation().getPersons().values().forEach(p -> experiencedPlans.put(p.getId(), p.getSelectedPlan()));

        ModalSplitStats modalSplitStats = new ModalSplitStats(zonesCollection, config, scenario);
        modalSplitStats.analyzeAndWriteStats(config.controller().getOutputDirectory(), experiencedPlans);
    }

    public void analyzeAndWriteStats(String outputLocation) {
        analyzeAndWriteStats(outputLocation, experiencedPlansService.getExperiencedPlans());
    }

    public void analyzeAndWriteStats(String outputLocation, IdMap<Person, Plan> experiencedPlans) {

        this.feederModesFromConfig = getFeederModesinConfig();
        this.possibleModesAtStop = getPossibleModesAtStop();
        this.possibleOriginDestinationModesAtStop = getPossibleOriginDestinationModesAtStop();
        this.outputLocation = outputLocation + "SBB_";
        this.stopStationsMap = generateStopStationMap();
        this.trainStationMap = generateTrainStationMap();
        this.modesMap = getAnalysisMainModesMap();
        this.modesInclRailFQMap = getModesInclRailFQMap();
        this.feederModesMap = getFeederModesMap();
        this.variablesMSMap = createVariablesModalSplitMap();
        this.variablesMSFeederMap = createVariablesModalSplitFeederMap();
        this.variablesTimeStepsMap = createVariablesTimeStepsMap();
        this.subpopulationDistanceMap = createArrayForSubpopulationMap(this.modesInclRailFQMap.size(), distanceClassesLabel.size());
        this.subpopulationMSPFMap = createArrayForSubpopulationMap(this.modesMap.size(), this.variablesMSMap.size());
        this.subpopulationMSPKMMap = createArrayForSubpopulationMap(this.modesMap.size(), this.variablesMSMap.size());
        this.subpopulationAccessMSPFMap = createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size());
        this.subpopulationEgressMSPFMap = createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size());
        this.subpopulationAccessMSPKMMap = createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size());
        this.subpopulationEgressMSPKMMap = createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size());
        this.zonesAccessMSPFMap = new HashMap<>();
        this.zonesAccessMSPkmMap = new HashMap<>();
        this.zonesEgressMSPFMap = new HashMap<>();
        this.zonesEgressMSPkmMap = new HashMap<>();
        this.subpopulationChangeMap = createArrayForSubpopulationMap(changeOrderList.size(), changeLabelList.size());
        this.subpopulationDistanceChangeMap = createArrayForSubpopulationMap(changeOrderList.size()* distanceClassesLabel.size(), changeLabelList.size());
        this.subpopulationChangePKMMap = createArrayForSubpopulationMap(changeOrderList.size(), changeLabelList.size());
        this.subpopulationDistanceChangePKMMap = createArrayForSubpopulationMap(changeOrderList.size()* distanceClassesLabel.size(), changeLabelList.size());
        this.timeMap = createTimeStepsForSubpopulaitonMap((int) (this.config.qsim().getEndTime().seconds() / timeSplit), this.variablesTimeStepsMap.size());
        this.travelTimeMap = createTimeStepsForSubpopulaitonMap((lastTravelTimeValue / travelTimeSplit) + 1, this.variablesTimeStepsMap.size());
        this.actualModesAtStop = getActualModesAtStop();
        // analyzing
        analyze(experiencedPlans);

        // writing the different files
        writeStopStationAnalysis();
        writeTrainStationAnalysis();
        writeDistanceClassesAnalysis();
        writeModalSplit();
        writeFeederModalSplit();
        writeChanges();
        writeTimeSteps();

    }

    private Map<Id<TransitStopFacility>, Set<String>> getActualModesAtStop() {
        Map<Id<TransitStopFacility>, Set<String>> actualModesAtStop = new HashMap<>();
        this.transitSchedule.getTransitLines().values()
                .stream()
                .flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .forEach(transitRoute -> {
                    transitRoute.getStops().stream().map(stop-> stop.getStopFacility().getId()).forEach(
                            stopId->actualModesAtStop.computeIfAbsent(stopId,a->new HashSet<>()).add(transitRoute.getTransportMode())
                    );
                });

        return actualModesAtStop;


    }

    private List<String> getPossibleModesAtStop() {
        Set<String> modeSet = new HashSet<>(SBBModes.TRAIN_STATION_MODES);
        modeSet.addAll(this.feederModesFromConfig);
        return new ArrayList<>(modeSet);
    }

    private List<String> getPossibleOriginDestinationModesAtStop() {
        Set<String> modeSet = new HashSet<>(SBBModes.TRAIN_STATION_ORIGDEST_MODES);
        modeSet.addAll(this.feederModesFromConfig);
        return new ArrayList<>(modeSet);
    }


    private void analyze(IdMap<Person, Plan> experiencedPlans) {
        for (Entry<Id<Person>, Plan> entry : experiencedPlans.entrySet()) {

            // analysis for access and egress mode for each stop station
            analyzeStopsStations(entry);
            // analysis for access and egress mode for each train station
            analyzeTrainsStations(entry);
            // analysis for distance classes
            analyzeDistanceClasses(entry);
//            // analysis modal split for persons trips and person km
            analyzeModalSplit(entry);
//            // analysis access/egress modal split for persons trips and person km
            analyzeFeederModalSplit(entry);
//            // analysis public transport changes
            analyzeChanges(entry);
//            // analyze travel time and middle time between to activities
            analyzeTimes(entry);

        }
    }

    private void analyzeTrainsStations(Entry<Id<Person>, Plan> entry) {
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(PT)) {
                List<Leg> legs = trip.getLegsOnly();
                Leg legBefore = pf.createLeg(SBBModes.WALK_FOR_ANALYSIS);
                for (Leg leg : legs) {
                    if (isPtMode(leg)) {
                        Route route = leg.getRoute();
                        String startTrainStationId = String.valueOf(getStartTrainFacility(route).getAttributes().getAttribute(STOP_NO));
                        String endTrainStationId = String.valueOf(getEndTrainFacility(route).getAttributes().getAttribute(STOP_NO));
                        String subMode = getModeOfTransitRoute(leg.getRoute());
                        Leg legAfter = getLegAfter(legs, legs.indexOf(leg));
                        if (railTripsAnalyzer.hasFQRelevantLeg(List.of((TransitPassengerRoute) leg.getRoute()))) {
                            if (isPtMode(legBefore) &&
                                    getModeOfTransitRoute(legBefore.getRoute()).equals(PTSubModes.RAIL) &&
                                    subMode.equals(PTSubModes.RAIL)) {
                                if (!getEndTrainFacility(legBefore.getRoute()).getAttributes().getAttribute(STOP_NO).toString().equals(startTrainStationId)) {
                                    trainStationMap.get(startTrainStationId).addUmsteigerTyp5b();
                                } else if (railTripsAnalyzer.hasFQRelevantLeg(List.of((TransitPassengerRoute) legBefore.getRoute()))) {
                                    trainStationMap.get(startTrainStationId).addUmsteigerSimbaSimba();
                                } else {
                                    trainStationMap.get(startTrainStationId).addUmsteigerAndereSimba();
                                }
                            } else {
                                trainStationMap.get(startTrainStationId).addQuellEinsteiger();
                            }
                            if (isPtMode(legAfter) &&
                                    getModeOfTransitRoute(legAfter.getRoute()).equals(PTSubModes.RAIL) &&
                                    subMode.equals(PTSubModes.RAIL)) {
                                if (!getStartTrainFacility(legAfter.getRoute()).getAttributes().getAttribute(STOP_NO).toString().equals(endTrainStationId)) {
                                    trainStationMap.get(endTrainStationId).addUmsteigerTyp5a();
                                } else if (railTripsAnalyzer.hasFQRelevantLeg(List.of((TransitPassengerRoute) legAfter.getRoute()))) {
                                    trainStationMap.get(endTrainStationId).addUmsteigerSimbaSimba();
                                } else {
                                    trainStationMap.get(endTrainStationId).addUmsteigerSimbaAndere();
                                }
                            } else {
                                trainStationMap.get(endTrainStationId).addZielAussteiger();
                            }
                        } else {
                            if (getModeOfTransitRoute(legBefore.getRoute()) != null && getModeOfTransitRoute(legBefore.getRoute()).equals(PTSubModes.RAIL) && subMode.equals(PTSubModes.RAIL)) {
                                trainStationMap.get(startTrainStationId).addUmsteigerAndereAndere();
                            }
                            if (getModeOfTransitRoute(legAfter.getRoute()) != null && getModeOfTransitRoute(legAfter.getRoute()).equals(PTSubModes.RAIL) && subMode.equals(PTSubModes.RAIL)) {
                                trainStationMap.get(endTrainStationId).addUmsteigerAndereAndere();
                            }
                        }
                    }
                    if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                        legBefore = leg;
                    }
                }
            }
        }
    }

    private static boolean isPtMode(Leg leg) {
        return leg.getMode().equals(PT) || PTSubModes.submodes.contains(leg.getMode()) ;
    }

    private void analyzeStopsStations(Entry<Id<Person>, Plan> entry) {
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            boolean isFQ = false;
            try {
                isFQ = railTripsAnalyzer.getFQDistance(trip, true) > 0;
            } catch (Exception ignored) {

            }

            if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(PT)) {
                List<Leg> legs = trip.getLegsOnly();
                Leg legBefore = null;
                for (Leg leg : legs) {
                    if (isPtMode(leg)) {
                        Route route = leg.getRoute();
                        TransitStopFacility startStopStationFacility = getStartTrainFacility(route);
                        TransitStopFacility endStopStationFacility = getEndTrainFacility(route);

                        StopStation startStopStation = stopStationsMap.get(startStopStationFacility.getId());
                        startStopStation.addEntred();
                        String subPTMode = getModeOfTransitRoute(leg.getRoute());
                        boolean isRailLeg = (subPTMode.equals(PTSubModes.RAIL));
                        if (isRailLeg) {
                            startStopStation.setRailStation();
                        }
                        if (isFQ) {
                            startStopStation.addEntredFQ();
                        }
                        if (legBefore != null) {
                            startStopStation.getEnteredMode()[possibleModesAtStop.indexOf(legBefore.getMode())]++;
                            if (isPtMode(legBefore)) {
                                subPTMode = getModeOfTransitRoute(legBefore.getRoute());
                                startStopStation.getEnteredMode()[possibleModesAtStop.indexOf(subPTMode)]++;
                                if (getEndTrainFacility(legBefore.getRoute()).getAttributes().getAttribute(STOP_NO).equals(startStopStationFacility.getAttributes().getAttribute(STOP_NO))) {
                                    if (getEndTrainFacility(legBefore.getRoute()).equals(startStopStationFacility)) {
                                        if (getModeOfTransitRoute(route).equals(PTSubModes.RAIL) &&
                                                subPTMode.equals(PTSubModes.RAIL)) {
                                            startStopStation.addUmstiegeBahnBahn();
                                        }
                                    } else if (getModeOfTransitRoute(route).equals(PTSubModes.RAIL) &&
                                            subPTMode.equals(PTSubModes.RAIL)) {
                                        startStopStation.addUmsteigeAHPBahn();
                                    }
                                }
                            }
                        } else {
                            startStopStation.getEnteredMode()[possibleModesAtStop.indexOf("walk")] = startStopStation.getEnteredMode()[possibleModesAtStop.indexOf("walk")] + 1;
                        }

                        StopStation endStopStation = stopStationsMap.get(endStopStationFacility.getId());
                        endStopStation.addExited();
                        if (isRailLeg) {
                            endStopStation.setRailStation();
                        }
                        if (isFQ) {
                            endStopStation.addExitedFQ();
                        }
                        int currentLegIndex = legs.indexOf(leg);
                        Leg legAfter = getLegAfter(legs, currentLegIndex);
                        endStopStation.getExitedMode()[possibleModesAtStop.indexOf(legAfter.getMode())]++;
                        if (isPtMode(legAfter)) {
                            subPTMode = getModeOfTransitRoute(legAfter.getRoute());
                            endStopStation.getExitedMode()[possibleModesAtStop.indexOf(subPTMode)]++;
                            if (getStartTrainFacility(legAfter.getRoute()).getAttributes().getAttribute(STOP_NO).equals(endStopStationFacility.getAttributes().getAttribute(STOP_NO))) {
                                if (getStartTrainFacility(legAfter.getRoute()).equals(endStopStationFacility)) {
                                    if (getModeOfTransitRoute(route).equals(PTSubModes.RAIL) &&
                                            subPTMode.equals(PTSubModes.RAIL)) {
                                        endStopStation.addUmstiegeBahnBahn();
                                    }
                                } else if (getModeOfTransitRoute(route).equals(PTSubModes.RAIL) &&
                                        subPTMode.equals(PTSubModes.RAIL)) {
                                    endStopStation.addUmsteigeBahnAHP();
                                }
                            }
                        }
                    }

                    if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                        legBefore = leg;
                    }
                }
            }
        }
    }

    private static String getSubpopulation(Attributes attributes) {
        String result;
        Object analyisSubpopulation = attributes.getAttribute(Variables.ANALYSIS_SUBPOPULATION);
        if (analyisSubpopulation != null) {
            result = analyisSubpopulation.toString();
        } else {
            result = attributes.getAttribute(Variables.SUBPOPULATION).toString();
        }
        return result;
    }

    private void analyzeTimes(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        String subpopulation = getSubpopulation(attributes);

        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (tmpMode.equals(SBBModes.WALK_MAIN_MAINMODE)) {
                tmpMode = SBBModes.WALK_FOR_ANALYSIS;
            }
            boolean tmpIsRail = false;
            for (Leg leg : trip.getLegsOnly()) {
                if (PT.contains(leg.getMode())) {
                    if (isPtMode(leg)) {
                        TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                        if (getModeOfTransitRoute(route).equals(PTSubModes.RAIL)) {
                            tmpIsRail = true;
                        }
                    }
                }
            }

            String tmpActivity = trip.getDestinationActivity().getType();
            if (tmpActivity.contains(separator)) {
                tmpActivity = tmpActivity.substring(0, tmpActivity.indexOf("_"));
            }
            int middle = (int) ((trip.getOriginActivity().getEndTime().seconds() + trip.getDestinationActivity().getStartTime().seconds()) / 2);
            int time = (middle - (middle % timeSplit)) / timeSplit;
            int[][] subpopulationArray = timeMap.get(subpopulation);
            if (time >= subpopulationArray.length) {
                time = subpopulationArray.length - 1;
            }
            subpopulationArray[time][variablesTimeStepsMap.get(all)]++;
            int travelTime = (int) ((trip.getDestinationActivity().getStartTime().seconds() - trip.getOriginActivity().getEndTime().seconds()));
            int timeArray = (travelTime - (travelTime % travelTimeSplit)) / travelTimeSplit;
            int[][] subpopulationTravelTime = travelTimeMap.get(subpopulation);
            if (timeArray >= subpopulationTravelTime.length) {
                timeArray = subpopulationTravelTime.length - 1;
            }
            subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(all)]++;
            for (String mode : modesMS) {
                if ((MSVariables.mode + separator + tmpMode).equals(mode)) {
                    subpopulationArray[time][variablesTimeStepsMap.get(mode)]++;
                    subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(mode)]++;
                    break;
                }
            }
            if(tmpIsRail) {
                String submode = MSVariables.submode + separator + SBBModes.RAIL;
                subpopulationArray[time][variablesTimeStepsMap.get(submode)]++;
                subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(submode)]++;
                double fqDistance = railTripsAnalyzer.getFQDistance(trip, true);
                if (fqDistance > 0) {
                    submode = MSVariables.submode + separator + SBBModes.FQRAIL;
                    subpopulationArray[time][variablesTimeStepsMap.get(submode)]++;
                    subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(submode)]++;
                }
            }
            for (String activity : toActTypeList) {
                if ((MSVariables.toActType + separator + tmpActivity).equals(activity)) {
                    subpopulationArray[time][variablesTimeStepsMap.get(activity)]++;
                    subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(activity)]++;
                    break;
                }
            }
        }
    }

    private void analyzeChanges(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        String subpulation = getSubpopulation(attributes);
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            int ptLegs = 0;
            int railLegs = 0;
            double distance = 0;
            boolean isFQ = false;
            for (Leg leg : trip.getLegsOnly()) {
                if (PT.contains(leg.getMode())) {
                    if (isPtMode(leg)) {
                        distance += leg.getRoute().getDistance() / 1000;
                        ptLegs++;
                        TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                        if (getModeOfTransitRoute(route).equals(PTSubModes.RAIL)) {
                            railLegs++;
                        }
                    }
                }
            }
            double fqDistance = 0;
            if (railLegs > 0) {
                fqDistance = railTripsAnalyzer.getFQDistance(trip, true);
                isFQ = (fqDistance > 0);
            }

            double[][] changeArray = subpopulationChangeMap.get(subpulation);
            double[][] changeArrayPKM = subpopulationChangePKMMap.get(subpulation);
            double[][] changeDistanceArray = subpopulationDistanceChangeMap.get(subpulation);
            double[][] changeDistanceArrayPKM = subpopulationDistanceChangePKMMap.get(subpulation);

            int offsetDistance = distanceClassesValue.size()-1;

            for (int disClass : distanceClassesValue) {
                if (distance <= disClass) {
                    offsetDistance = distanceClassesValue.indexOf(disClass);
                    break;
                }
            }

            offsetDistance = offsetDistance * changeOrderList.size();

            boolean isMixed = ((railLegs > 0) & (ptLegs > railLegs));
            if (ptLegs > 6) {
                ptLegs = 6;
                if (railLegs > 6) {
                    railLegs = 6;
                }
            }
            if (railLegs > 0) {
                changeArray[changeOrderList.indexOf(changeTrainAll)][railLegs - 1]++;
                changeArrayPKM[changeOrderList.indexOf(changeTrainAll)][railLegs - 1] += distance;
                changeDistanceArray[offsetDistance + changeOrderList.indexOf(changeTrainAll)][railLegs - 1]++;
                changeDistanceArrayPKM[offsetDistance + changeOrderList.indexOf(changeTrainAll)][railLegs - 1] += distance;
                if (isMixed) {
                    changeArray[changeOrderList.indexOf(changeOEV)][ptLegs - 1]++;
                    changeArrayPKM[changeOrderList.indexOf(changeOEV)][ptLegs - 1]+=distance;
                    changeDistanceArray[offsetDistance + changeOrderList.indexOf(changeOEV)][ptLegs - 1]++;
                    changeDistanceArrayPKM[offsetDistance + changeOrderList.indexOf(changeOEV)][ptLegs - 1]+=distance;
                } else {
                    changeArray[changeOrderList.indexOf(changeTrain)][railLegs - 1]++;
                    changeArrayPKM[changeOrderList.indexOf(changeTrain)][railLegs - 1] += distance;
                    changeDistanceArray[offsetDistance + changeOrderList.indexOf(changeTrain)][railLegs - 1]++;
                    changeDistanceArrayPKM[offsetDistance + changeOrderList.indexOf(changeTrain)][railLegs - 1] += distance;
                }
                if (isFQ) {
                    changeArray[changeOrderList.indexOf(changeTrainFQ)][railLegs - 1]++;
                    changeArrayPKM[changeOrderList.indexOf(changeTrainFQ)][railLegs - 1] += fqDistance;
                    changeDistanceArray[offsetDistance + changeOrderList.indexOf(changeTrainFQ)][railLegs - 1]++;
                    changeDistanceArrayPKM[offsetDistance + changeOrderList.indexOf(changeTrainFQ)][railLegs - 1] += fqDistance;
                }
            } else if (ptLegs > 0) {
                changeArray[changeOrderList.indexOf(changeOPNV)][ptLegs - 1]++;
                changeArrayPKM[changeOrderList.indexOf(changeOPNV)][ptLegs - 1]+=distance;
                changeDistanceArray[offsetDistance +changeOrderList.indexOf(changeOPNV)][ptLegs - 1]++;
                changeDistanceArrayPKM[offsetDistance +changeOrderList.indexOf(changeOPNV)][ptLegs - 1]+=distance;
            }
        }
    }

    private void analyzeModalSplit(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        String subpopulation = getSubpopulation(attributes);

        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            // skip home office activities, it seems that the facility id can be null
            if (trip.getOriginActivity().getFacilityId() != null || trip.getDestinationActivity().getFacilityId() != null) {
                if (trip.getOriginActivity().getFacilityId().equals(trip.getDestinationActivity().getFacilityId())) {
                    continue;
                }
            }

            String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (tmpMode.equals(SBBModes.WALK_MAIN_MAINMODE)) {
                tmpMode = SBBModes.WALK_FOR_ANALYSIS;
            }
            int modeId = modesMap.get(tmpMode);
            double distance = 0;
            double railDistance = 0;
            boolean tmpIsRail = false;
            boolean tmpIsFQRail = false;
            double fqDistance = 0;

            for (Leg leg : trip.getLegsOnly()) {
                if (PT.contains(leg.getMode())) {
                    if (isPtMode(leg)) {
                        TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                        if (getModeOfTransitRoute(route).equals(PTSubModes.RAIL)) {
                            railDistance += leg.getRoute().getDistance() / 1000;
                            tmpIsRail = true;
                        }
                    }
                }
                distance += leg.getRoute().getDistance() / 1000;
            }
            if (tmpIsRail) {
                fqDistance = railTripsAnalyzer.getFQDistance(trip, true) / 1000;
                tmpIsFQRail = (fqDistance > 0);
            }

            double[][] pfArray = subpopulationMSPFMap.get(subpopulation);
            double[][] pkmArray = subpopulationMSPKMMap.get(subpopulation);
            pfArray[modeId][variablesMSMap.get(all)]++;

            pkmArray[modeId][variablesMSMap.get(all)] += distance;

            if (tmpIsRail) {
                String submode = MSVariables.submode + separator + SBBModes.RAIL;
                pfArray[modeId][variablesMSMap.get(submode)]++;
                pkmArray[modeId][variablesMSMap.get(submode)] += railDistance;
                if (tmpIsFQRail) {
                    submode = MSVariables.submode + separator + SBBModes.FQRAIL;
                    pfArray[modeId][variablesMSMap.get(submode)]++;
                    pkmArray[modeId][variablesMSMap.get(submode)] += fqDistance;
                }
            }

            // car available
            String carAva = "0";
            if (attributes.getAttribute(Variables.CAR_AVAIL) != null) {
                carAva = attributes.getAttribute(Variables.CAR_AVAIL).toString();
            }
            for (String attCarAva : carAvailable) {
                if ((Variables.CAR_AVAIL + "_" + carAva).equals(attCarAva)) {
                    pfArray[modeId][variablesMSMap.get(attCarAva)]++;
                    pkmArray[modeId][variablesMSMap.get(attCarAva)] += distance;
                    break;
                }
            }
            // pt subscription
            String ptSub = "none";
            if (attributes.getAttribute(Variables.PT_SUBSCRIPTION) != null) {
                ptSub = attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString();
            }
            for (String attPtSub : ptSubscription) {
                if ((Variables.PT_SUBSCRIPTION + "_" + ptSub).equals(attPtSub)) {
                    pfArray[modeId][variablesMSMap.get(attPtSub)]++;
                    pkmArray[modeId][variablesMSMap.get(attPtSub)] += distance;
                    break;
                }
            }
            // combination car available and pt subscription
            for (String attCarPT : carAndPt) {
                if ((Variables.CAR_AVAIL + "_" + carAva + "_" + Variables.PT_SUBSCRIPTION + "_" + ptSub).equals(attCarPT)) {
                    pfArray[modeId][variablesMSMap.get(attCarPT)]++;
                    pkmArray[modeId][variablesMSMap.get(attCarPT)] += distance;
                    break;
                }
            }
            // kind of education
            String edu = "null";
            if (attributes.getAttribute(Variables.CURRENT_EDUCATION) != null) {
                edu = attributes.getAttribute(Variables.CURRENT_EDUCATION).toString();
            }
            for (String attEdu : educationType) {
                if ((Variables.CURRENT_EDUCATION + "_" + edu).equals(attEdu)) {
                    pfArray[modeId][variablesMSMap.get(attEdu)]++;
                    pkmArray[modeId][variablesMSMap.get(attEdu)] += distance;
                    break;
                }
            }
            // employment rate
            String empRate = "0";
            if (attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT) != null) {
                empRate = attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT).toString();
            }
            for (String attEmpRate : employmentRate) {
                if ((Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + empRate).equals(attEmpRate)) {
                    pfArray[modeId][variablesMSMap.get(attEmpRate)]++;
                    pkmArray[modeId][variablesMSMap.get(attEmpRate)] += distance;
                    break;
                }
            }
            // age category
            for (String age : ageCategory) {
                if (attributes.getAttribute(Variables.AGE_CATEGORY) != null && (Variables.AGE_CATEGORY + "_" + attributes.getAttribute(Variables.AGE_CATEGORY).toString()).equals(age)) {
                    pfArray[modeId][variablesMSMap.get(age)]++;
                    pkmArray[modeId][variablesMSMap.get(age)] += distance;
                    break;
                }
            }
            // activity type for end activity
            String actType = trip.getDestinationActivity().getType();
            for (String act : toActTypeList) {
                if (actType.contains(separator)) {
                    actType = actType.substring(0, actType.indexOf("_"));
                }
                if ((toActType + separator + actType).equals(act)) {
                    pfArray[modeId][variablesMSMap.get(act)]++;
                    pkmArray[modeId][variablesMSMap.get(act)] += distance;
                    break;
                }
            }
        }
    }

    private Map<List<String>, List<String>> getSubgroups() {
        Map<List<String>, List<String>> subgroups = new HashMap<>();
        subgroups.put(List.of(Variables.CAR_AVAIL), carAvailable);
        subgroups.put(List.of(Variables.PT_SUBSCRIPTION), ptSubscription);
        subgroups.put(List.of(Variables.CAR_AVAIL, Variables.PT_SUBSCRIPTION), carAndPt);
        subgroups.put(List.of(Variables.CURRENT_EDUCATION), educationType);
        subgroups.put(List.of(Variables.LEVEL_OF_EMPLOYMENT_CAT), employmentRate);
        subgroups.put(List.of(Variables.AGE_CATEGORY), ageCategory);
        return subgroups;
    }

    private int findFirstRailLeg(List<Leg> legs){
        for (Leg leg : legs) {
            if (isPtMode(leg)) {
                if (getModeOfTransitRoute(leg.getRoute()).equals(SBBModes.PTSubModes.RAIL)) {
                    return legs.indexOf(leg);
                }
            }
        }
        return -1;
    }

    private int findLastRailLeg(List<Leg> legs){
        int lastLeg = -1;
        for (Leg leg : legs) {
            if (isPtMode(leg)) {
                if (getModeOfTransitRoute(leg.getRoute()).equals(SBBModes.PTSubModes.RAIL)) {
                    lastLeg =  legs.indexOf(leg);
                }
            }
        }
        return lastLeg;
    }

    private void analyzeFeederModalSplit(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        String subpopulation = getSubpopulation(attributes);
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            // skip home office activities, it seems that the facility id can be null
            if (trip.getOriginActivity().getFacilityId() != null || trip.getDestinationActivity().getFacilityId() != null) {
                if (trip.getOriginActivity().getFacilityId().equals(trip.getDestinationActivity().getFacilityId())) {
                    continue;
                }
            }

            if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(PT)) {
                // identify access/egress mode
                List<Leg> legs = trip.getLegsOnly();
                int firstRailLeg = findFirstRailLeg(legs);
                int lastRailLeg = findLastRailLeg(legs);

                if (firstRailLeg > -1) {
                    int subPTModeEntered = feederModesMap.get(SBBModes.ACCESS_EGRESS_WALK);
                    double distanceEnter = 0;
                    for (int i = 0; i < firstRailLeg; i++) {
                        Leg leg = legs.get(i);
                        if (leg.getRoute() != null) {
                            distanceEnter += leg.getRoute().getDistance();
                        }
                        if (isPtMode(leg)) {
                            String subPTMode = getModeOfTransitRoute(leg.getRoute());
                            subPTModeEntered = feederModesMap.get(subPTMode);
                        } else {
                            if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                                subPTModeEntered = feederModesMap.get(leg.getMode());
                            }
                        }
                    }

                    int subPTModeExited = feederModesMap.get(SBBModes.ACCESS_EGRESS_WALK);
                    double distanceExit = 0;
                    for (int i = lastRailLeg + 1; i < legs.size(); i++) {
                        Leg leg = legs.get(i);
                        if (leg.getRoute() != null) {
                            distanceExit += leg.getRoute().getDistance();
                        }
                        if (isPtMode(leg)) {
                            String subPTMode = getModeOfTransitRoute(leg.getRoute());
                            subPTModeExited = feederModesMap.get(subPTMode);
                        } else {
                            if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                                subPTModeExited = feederModesMap.get(leg.getMode());
                            }
                        }
                    }

                    // count PF and PKM
                    String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                    if (tmpMode.equals(SBBModes.WALK_MAIN_MAINMODE)) {
                        tmpMode = SBBModes.WALK_FOR_ANALYSIS;
                    }
                    if (tmpMode.equals(PT)) {
                        // get origin and dest zon Maps
                        Zone originZone = zones.findZone(trip.getOriginActivity().getCoord());
                        Zone destZone = zones.findZone(trip.getDestinationActivity().getCoord());
                        String originZoneId = "";
                        String destZoneId = "";
                        String originZonesl3 = "";
                        String destZonesl3 = "";

                        if (originZone != null) {
                            originZoneId = String.valueOf(originZone.getId());
                            originZonesl3 = originZone.getAttribute("sl3_id").toString();
                        }
                        if (destZone != null) {
                            destZoneId = String.valueOf(destZone.getId());
                            destZonesl3 = destZone.getAttribute("sl3_id").toString();
                        }

                        if (zonesAccessMSPFMap.get(originZoneId) == null) {
                            zonesAccessMSPFMap.put(originZoneId, createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size()));
                            zonesAccessMSPkmMap.put(originZoneId, createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size()));
                        }
                        if (zonesEgressMSPFMap.get(destZoneId) == null) {
                            zonesEgressMSPFMap.put(destZoneId, createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size()));
                            zonesEgressMSPkmMap.put(destZoneId, createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size()));
                        }

                        double[][] pfOriginZoneArray = zonesAccessMSPFMap.get(originZoneId).get(subpopulation);
                        double[][] pfDestZoneArray = zonesEgressMSPFMap.get(destZoneId).get(subpopulation);

                        double[][] pfAccessArray = subpopulationAccessMSPFMap.get(subpopulation);
                        pfAccessArray[subPTModeEntered][variablesMSFeederMap.get(all)]++;
                        pfOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(all)]++;
                        double[][] pfEgressArray = subpopulationEgressMSPFMap.get(subpopulation);
                        pfEgressArray[subPTModeExited][variablesMSFeederMap.get(all)]++;
                        pfDestZoneArray[subPTModeExited][variablesMSFeederMap.get(all)]++;

                        double[][] pkmOriginZoneArray = zonesAccessMSPkmMap.get(originZoneId).get(subpopulation);
                        double[][] pkmDestZoneArray = zonesEgressMSPkmMap.get(destZoneId).get(subpopulation);

                        double[][] pkmAccessArray = subpopulationAccessMSPKMMap.get(subpopulation);
                        pkmAccessArray[subPTModeEntered][variablesMSFeederMap.get(all)] += distanceEnter;
                        pkmOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(all)] += distanceEnter;
                        double[][] pkmEgressArray = subpopulationEgressMSPKMMap.get(subpopulation);
                        pkmEgressArray[subPTModeExited][variablesMSFeederMap.get(all)] += distanceExit;
                        pkmDestZoneArray[subPTModeExited][variablesMSFeederMap.get(all)] += distanceExit;


                        Map<List<String>, List<String>> subgroups = getSubgroups();

                        for (Entry<List<String>, List<String>> subgroup : subgroups.entrySet()) {
                            // car available
                            for (String value : subgroup.getValue()) {
                                List<String> variables = subgroup.getKey();
                                StringBuilder att = new StringBuilder();
                                for (String variable : variables) {
                                    if (attributes.getAttribute(variable) != null) {
                                        if (!att.isEmpty()) {
                                            att.append("_");
                                        }
                                        att.append(variable).append("_").append(attributes.getAttribute(variable).toString());
                                    }
                                }
                                if (att.toString().equals(value)) {
                                    pfAccessArray[subPTModeEntered][variablesMSFeederMap.get(value)]++;
                                    pfEgressArray[subPTModeExited][variablesMSFeederMap.get(value)]++;
                                    pkmAccessArray[subPTModeEntered][variablesMSFeederMap.get(value)] += distanceEnter;
                                    pkmEgressArray[subPTModeExited][variablesMSFeederMap.get(value)] += distanceExit;
                                    pfOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(value)]++;
                                    pfDestZoneArray[subPTModeExited][variablesMSFeederMap.get(value)]++;
                                    pkmOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(value)] += distanceEnter;
                                    pkmDestZoneArray[subPTModeExited][variablesMSFeederMap.get(value)] += distanceExit;
                                    break;
                                }
                            }
                        }
                        // activity type for end activity
                        String actType = trip.getDestinationActivity().getType();
                        if (actType.contains(separator)) {
                            actType = actType.substring(0, actType.indexOf("_"));
                        }
                        for (String act : toActTypeList) {
                            if ((toActType + separator + actType).equals(act)) {
                                pfAccessArray[subPTModeEntered][variablesMSFeederMap.get(act)]++;
                                pfEgressArray[subPTModeExited][variablesMSFeederMap.get(act)]++;
                                pkmAccessArray[subPTModeEntered][variablesMSFeederMap.get(act)] += distanceEnter;
                                pkmEgressArray[subPTModeExited][variablesMSFeederMap.get(act)] += distanceExit;
                                pfOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(act)]++;
                                pfDestZoneArray[subPTModeExited][variablesMSFeederMap.get(act)]++;
                                pkmOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(act)] += distanceEnter;
                                pkmDestZoneArray[subPTModeExited][variablesMSFeederMap.get(act)] += distanceExit;
                                break;
                            }
                        }

                        // feeder distance
                        for (int disClass : distanceClassesFeederValue) {
                            if (distanceEnter <= disClass) {
                                pfAccessArray[subPTModeEntered][variablesMSFeederMap.get(distanceClassesFeederLabel.get(distanceClassesFeederValue.indexOf(disClass)))]++;
                                pkmAccessArray[subPTModeEntered][variablesMSFeederMap.get(distanceClassesFeederLabel.get(distanceClassesFeederValue.indexOf(disClass)))] += distanceEnter;
                                pfOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(distanceClassesFeederLabel.get(distanceClassesFeederValue.indexOf(disClass)))]++;
                                pkmOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(distanceClassesFeederLabel.get(distanceClassesFeederValue.indexOf(disClass)))] += distanceEnter;
                                break;
                            }
                        }

                        for (int disClass : distanceClassesFeederValue) {
                            if (distanceExit <= disClass) {
                                pfEgressArray[subPTModeExited][variablesMSFeederMap.get(distanceClassesFeederLabel.get(distanceClassesFeederValue.indexOf(disClass)))]++;
                                pkmEgressArray[subPTModeExited][variablesMSFeederMap.get(distanceClassesFeederLabel.get(distanceClassesFeederValue.indexOf(disClass)))] += distanceExit;
                                pfDestZoneArray[subPTModeExited][variablesMSFeederMap.get(distanceClassesFeederLabel.get(distanceClassesFeederValue.indexOf(disClass)))]++;
                                pkmDestZoneArray[subPTModeExited][variablesMSFeederMap.get(distanceClassesFeederLabel.get(distanceClassesFeederValue.indexOf(disClass)))] += distanceExit;
                                break;
                            }
                        }

                        // sl3
                        String enterType = "";
                        if (originZonesl3.equals("1")) enterType = sl3Urban;
                        if (originZonesl3.equals("2")) enterType = sl3Suburban;
                        if (originZonesl3.equals("3")) enterType = sl3Rural;
                        if (!enterType.equals("")) {
                            pfAccessArray[subPTModeEntered][variablesMSFeederMap.get(enterType)]++;
                            pkmAccessArray[subPTModeEntered][variablesMSFeederMap.get(enterType)] += distanceEnter;
                            pfOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(enterType)]++;
                            pkmOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(enterType)] += distanceEnter;
                        }
                        String exitType = "";
                        if (destZonesl3.equals("1")) exitType = sl3Urban;
                        if (destZonesl3.equals("2")) exitType = sl3Suburban;
                        if (destZonesl3.equals("3")) exitType = sl3Rural;
                        if (!exitType.equals("")) {
                            pfEgressArray[subPTModeExited][variablesMSFeederMap.get(exitType)]++;
                            pkmEgressArray[subPTModeExited][variablesMSFeederMap.get(exitType)] += distanceExit;
                            pfDestZoneArray[subPTModeExited][variablesMSFeederMap.get(exitType)]++;
                            pkmDestZoneArray[subPTModeExited][variablesMSFeederMap.get(exitType)] += distanceExit;
                        }

                    }
                }
            }
        }
    }

    private void analyzeDistanceClasses(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = this.population.getPersons().get(entry.getKey()).getAttributes();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            // skip home office activities, it seems that the facility id can be null
            if (trip.getOriginActivity().getFacilityId() != null || trip.getDestinationActivity().getFacilityId() != null) {
                if (trip.getOriginActivity().getFacilityId().equals(trip.getDestinationActivity().getFacilityId())) {
                    continue;
                }
            }

            String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (tmpMode.equals(SBBModes.WALK_MAIN_MAINMODE)) {
                tmpMode = SBBModes.WALK_FOR_ANALYSIS;
            }
            int modeID = this.modesInclRailFQMap.get(tmpMode);
            double distance = 0;
            for (Leg leg : trip.getLegsOnly()) {
                distance += leg.getRoute().getDistance() / 1000;
            }
            int disCl = distanceClassesValue.size();
            double[][] disArray = this.subpopulationDistanceMap.get(getSubpopulation(attributes));
            for (int disClass : distanceClassesValue) {
                if (distance <= disClass) {
                    disCl = distanceClassesValue.indexOf(disClass);
                    break;
                }
            }
            disArray[modeID][disCl]++;
            if (tmpMode.equals(SBBModes.PT)) {
                List<Leg> legs = trip.getLegsOnly();
                int firstRailLeg = findFirstRailLeg(legs);
                int lastRailLeg = findLastRailLeg(legs);
                if (firstRailLeg > -1) {
                    double railDistance = 0;
                    for (int i = firstRailLeg; i <= lastRailLeg; i++) {
                        Leg leg = legs.get(i);
                        railDistance += leg.getRoute().getDistance() / 1000;
                    }
                    int railDisCl = distanceClassesValue.size();
                    for (int disClass : distanceClassesValue) {
                        if (railDistance <= disClass) {
                            railDisCl = distanceClassesValue.indexOf(disClass);
                            break;
                        }
                    }
                    int modeRailID = this.modesInclRailFQMap.get(PTSubModes.RAIL);
                    disArray[modeRailID][railDisCl]++;
                    double railFQdist = railTripsAnalyzer.getFQDistance(trip, true) / 1000;
                    if (railFQdist > 0) {
                        int modeRailFQID = this.modesInclRailFQMap.get("railFQ");
                        disArray[modeRailFQID][railDisCl]++;
                    }
                }
            }
        }
    }

    private void writeDistanceClassesAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(this.config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = new String[3 + distanceClassesLabel.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        columns[2] = mode;
        for (int i = 0; i < distanceClassesLabel.size(); i++) {
            columns[i + 3] = distanceClassesLabel.get(i);
        }
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + oNDistanceClasses)) {
            for (String tmpSubpopulation : analysisSubpopulations) {
                for (Entry<String, Integer> col : this.modesInclRailFQMap.entrySet()) {
                    csvWriter.set(runID, this.config.controller().getRunId());
                    csvWriter.set(subpopulation, tmpSubpopulation);
                    csvWriter.set(mode, col.getKey());
                    for (int i = 0; i < distanceClassesLabel.size(); i++) {
                        csvWriter.set(distanceClassesLabel.get(i), Integer.toString((int) (this.subpopulationDistanceMap.get(tmpSubpopulation)[col.getValue()][i] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private void writeTimeSteps() {

        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        final String time = "time";
        String[] columns = new String[3 + variablesTimeStepsMap.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        columns[2] = time;
        int index = 3;
        for (String var : variablesTimeStepsMap.keySet()) {
            columns[index++] = var;
        }
        try (CSVWriter csvWriter = new CSVWriter("", columns, outputLocation + oNMiddleTimeSteps)) {
            for (Entry<String, int[][]> entry : timeMap.entrySet()) {
                for (int i = 0; i < config.qsim().getEndTime().seconds() / timeSplit; i++) {
                    csvWriter.set(runID, config.controller().getRunId());
                    csvWriter.set(subpopulation, entry.getKey());
                    csvWriter.set(time, Integer.toString(i * timeSplit));
                    for (Entry<String, Integer> var : variablesTimeStepsMap.entrySet()) {
                        csvWriter.set(var.getKey(), Integer.toString((int) (entry.getValue()[i][var.getValue()] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
        try (CSVWriter csvWriter = new CSVWriter("", columns, outputLocation + oNTravelTimeDistribution)) {
            for (Entry<String, int[][]> entry : travelTimeMap.entrySet()) {
                for (int i = 0; i < (lastTravelTimeValue / travelTimeSplit) + 1; i++) {
                    csvWriter.set(runID, config.controller().getRunId());
                    csvWriter.set(subpopulation, entry.getKey());
                    csvWriter.set(time, Integer.toString(i * travelTimeSplit));
                    if (i == (lastTravelTimeValue / travelTimeSplit)) {
                        csvWriter.set(time, ">18000");
                    }
                    for (Entry<String, Integer> var : variablesTimeStepsMap.entrySet()) {
                        csvWriter.set(var.getKey(), Integer.toString((int) (entry.getValue()[i][var.getValue()] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private void writeModalSplit() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = new String[2 + variablesMSMap.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        int i = 2;
        for (String var : this.variablesMSMap.keySet()) {
            columns[i++] = var;
        }
        try (CSVWriter csvWriterPF = new CSVWriter("", columns, outputLocation + oNModalSplitPF)) {
            for (String tmpSubpopulation : analysisSubpopulations) {
                for (Entry<String, Integer> modeEntry : modesMap.entrySet()) {
                    csvWriterPF.set(runID, config.controller().getRunId());
                    csvWriterPF.set(subpopulation, tmpSubpopulation);
                    for (Entry<String, Integer> entry : this.variablesMSMap.entrySet()) {
                        if (entry.getKey().equals(mode)) {
                            csvWriterPF.set(mode, modeEntry.getKey());
                        } else {
                            String key = entry.getKey();
                            Integer value = entry.getValue();
                            csvWriterPF.set(key, Integer.toString((int) (this.subpopulationMSPFMap.get(tmpSubpopulation)[modeEntry.getValue()][value] / sampleSize)));
                        }
                    }
                    csvWriterPF.writeRow();
                }
            }
        } catch (
            IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
        try (CSVWriter csvWriterPKM = new CSVWriter("", columns, outputLocation + oNModalSplitPKM)) {
            for (String tmpSubpopulation : analysisSubpopulations) {
                for (Entry<String, Integer> modeEntry : modesMap.entrySet()) {
                    csvWriterPKM.set(runID, config.controller().getRunId());
                    csvWriterPKM.set(subpopulation, tmpSubpopulation);
                    for (Entry<String, Integer> entry : this.variablesMSMap.entrySet()) {
                        if (entry.getKey().equals(mode)) {
                            csvWriterPKM.set(mode, modeEntry.getKey());
                        } else {
                            String key = entry.getKey();
                            Integer value = entry.getValue();
                            csvWriterPKM.set(key, Integer.toString((int) (this.subpopulationMSPKMMap.get(tmpSubpopulation)[modeEntry.getValue()][value] / sampleSize)));
                        }
                    }
                    csvWriterPKM.writeRow();
                }
            }
        } catch (
            IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private void writeFeederModalSplit() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = new String[3 + variablesMSFeederMap.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        columns[2] = mode;
        int i = 3;
        for (String var : this.variablesMSFeederMap.keySet()) {
            columns[i++] = var;
        }
        try (CSVWriter csvWriterPF = new CSVWriter("", columns, outputLocation + oNModalSplitFeederPF)) {
            for (String tmpSubpopulation : analysisSubpopulations) {
                for (Entry<String, Integer> modeAccessEntry : feederModesMap.entrySet()) {
                    csvWriterPF.set(runID, config.controller().getRunId());
                    csvWriterPF.set(subpopulation, tmpSubpopulation);
                    csvWriterPF.set(mode, PT);
                    for (Entry<String, Integer> entry : this.variablesMSFeederMap.entrySet()) {
                        if (entry.getKey().equals(accessMode)) {
                            csvWriterPF.set(accessMode, modeAccessEntry.getKey());
                        } else {
                            if (entry.getKey().equals(egressMode)) {
                                csvWriterPF.set(egressMode, all);
                            } else {
                                String key = entry.getKey();
                                Integer value = entry.getValue();
                                csvWriterPF.set(key, Integer.toString((int) (this.subpopulationAccessMSPFMap.get(tmpSubpopulation)[modeAccessEntry.getValue()][value] / sampleSize)));
                            }
                        }
                    }
                    csvWriterPF.writeRow();
                }
                for (Entry<String, Integer> modeEgressEntry : feederModesMap.entrySet()) {
                    csvWriterPF.set(runID, config.controller().getRunId());
                    csvWriterPF.set(subpopulation, tmpSubpopulation);
                    csvWriterPF.set(mode, PT);
                    for (Entry<String, Integer> entry : this.variablesMSFeederMap.entrySet()) {
                        if (entry.getKey().equals(egressMode)) {
                            csvWriterPF.set(egressMode, modeEgressEntry.getKey());
                        } else {
                            if (entry.getKey().equals(accessMode)) {
                                csvWriterPF.set(accessMode, all);
                            } else {
                                String key = entry.getKey();
                                Integer value = entry.getValue();
                                csvWriterPF.set(key, Integer.toString((int) (this.subpopulationEgressMSPFMap.get(tmpSubpopulation)[modeEgressEntry.getValue()][value] / sampleSize)));
                            }
                        }
                    }
                    csvWriterPF.writeRow();
                }
            }
        } catch (
                IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
        try (CSVWriter csvWriterPKM = new CSVWriter("", columns, outputLocation + oNModalSplitFeederPKM)) {
            for (String tmpSubpopulation : analysisSubpopulations) {
                for (Entry<String, Integer> accessModeEntry : feederModesMap.entrySet()) {
                    csvWriterPKM.set(runID, config.controller().getRunId());
                    csvWriterPKM.set(subpopulation, tmpSubpopulation);
                    csvWriterPKM.set(mode, PT);
                    for (Entry<String, Integer> entry : this.variablesMSFeederMap.entrySet()) {
                        if (entry.getKey().equals(accessMode)) {
                            csvWriterPKM.set(accessMode, accessModeEntry.getKey());
                        } else {
                            if(entry.getKey().equals(egressMode)){
                                csvWriterPKM.set(egressMode, all);
                            } else{
                                String key = entry.getKey();
                                Integer value = entry.getValue();
                                csvWriterPKM.set(key, Integer.toString((int) (this.subpopulationAccessMSPKMMap.get(tmpSubpopulation)[accessModeEntry.getValue()][value] / sampleSize)));
                            }
                        }
                    }
                    csvWriterPKM.writeRow();
                }
                for (Entry<String, Integer> egrcessModeEntry : feederModesMap.entrySet()) {
                    csvWriterPKM.set(runID, config.controller().getRunId());
                    csvWriterPKM.set(subpopulation, tmpSubpopulation);
                    csvWriterPKM.set(mode, PT);
                    for (Entry<String, Integer> entry : this.variablesMSFeederMap.entrySet()) {
                        if (entry.getKey().equals(egressMode)) {
                            csvWriterPKM.set(egressMode, egrcessModeEntry.getKey());
                        } else {
                            if(entry.getKey().equals(accessMode)){
                                csvWriterPKM.set(accessMode, all);
                            } else{
                                String key = entry.getKey();
                                Integer value = entry.getValue();
                                csvWriterPKM.set(key, Integer.toString((int) (this.subpopulationEgressMSPKMMap.get(tmpSubpopulation)[egrcessModeEntry.getValue()][value] / sampleSize)));
                            }
                        }
                    }
                    csvWriterPKM.writeRow();
                }
            }
        } catch (
                IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

        columns = new String[4 + variablesMSFeederMap.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        columns[2] = mode;
        columns[3] = zone;
        i = 4;
        for (String var : this.variablesMSFeederMap.keySet()) {
            columns[i++] = var;
        }
        try (CSVWriter csvWriterPKM = new CSVWriter("", columns, outputLocation + oNModalSplitZoneFeederPF)) {
            for (String tmpSubpopulation : analysisSubpopulations) {
                for (Entry<String, Map<String, double[][]>> zoneEntry : zonesAccessMSPFMap.entrySet()){
                    for (Entry<String, Integer> accessModeEntry : feederModesMap.entrySet()) {
                        if (zonesAccessMSPFMap.get(zoneEntry.getKey()).get(tmpSubpopulation)[accessModeEntry.getValue()][this.variablesMSFeederMap.get("all")]>0) {
                            csvWriterPKM.set(runID, config.controller().getRunId());
                            csvWriterPKM.set(subpopulation, tmpSubpopulation);
                            csvWriterPKM.set(zone, zoneEntry.getKey());
                            csvWriterPKM.set(mode, PT);
                            for (Entry<String, Integer> entry : this.variablesMSFeederMap.entrySet()) {
                                if (entry.getKey().equals(accessMode)) {
                                    csvWriterPKM.set(accessMode, accessModeEntry.getKey());
                                } else {
                                    if (entry.getKey().equals(egressMode)) {
                                        csvWriterPKM.set(egressMode, all);
                                    } else {
                                        String key = entry.getKey();
                                        Integer value = entry.getValue();
                                        csvWriterPKM.set(key, Integer.toString((int) (zonesAccessMSPFMap.get(zoneEntry.getKey()).get(tmpSubpopulation)[accessModeEntry.getValue()][value] / sampleSize)));
                                    }
                                }
                            }
                            csvWriterPKM.writeRow();
                        }
                    }
                }
                for (Entry<String, Map<String, double[][]>> zoneEntry : zonesEgressMSPFMap.entrySet()){
                    for (Entry<String, Integer> egressModeEntry : feederModesMap.entrySet()) {
                        if (zonesEgressMSPFMap.get(zoneEntry.getKey()).get(tmpSubpopulation)[egressModeEntry.getValue()][this.variablesMSFeederMap.get("all")]>0) {
                            csvWriterPKM.set(runID, config.controller().getRunId());
                            csvWriterPKM.set(subpopulation, tmpSubpopulation);
                            csvWriterPKM.set(zone, zoneEntry.getKey());
                            csvWriterPKM.set(mode, PT);
                            for (Entry<String, Integer> entry : this.variablesMSFeederMap.entrySet()) {
                                if (entry.getKey().equals(egressMode)) {
                                    csvWriterPKM.set(egressMode, egressModeEntry.getKey());
                                } else {
                                    if (entry.getKey().equals(accessMode)) {
                                        csvWriterPKM.set(accessMode, all);
                                    } else {
                                        String key = entry.getKey();
                                        Integer value = entry.getValue();
                                        csvWriterPKM.set(key, Integer.toString((int) (zonesEgressMSPFMap.get(zoneEntry.getKey()).get(tmpSubpopulation)[egressModeEntry.getValue()][value] / sampleSize)));
                                    }
                                }
                            }
                            csvWriterPKM.writeRow();
                        }
                    }
                }
            }
        } catch (
                IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

    }

    private void writeTrainStationAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        final String hstNumber = "HST_Nummer";
        final String stopCode = "Code";
        final String zone = "Zone";
        final String zielAussteiger = "Ziel_Aussteiger";
        final String quellEinsteiger = "Quell_Einsteiger";
        final String umsteigerTyp5a = "Umsteiger_Typ_5a";
        final String umsteigerTyp5b = "Umsteiger_Typ_5b";
        final String umsteigerSimbaSimba = "Umsteiger_Simba_Simba";
        final String umsteigerSimbaAndere = "Umsteiger_Simba_Andere";
        final String umsteigerAndereSimba = "Umsteiger_Andere_Simba";
        final String umsteigerAndereAndere = "Umsteiger_Andere_Andere";
        String head = String.join(",", runID, hstNumber, stopCode, zone, zielAussteiger, quellEinsteiger, umsteigerTyp5a, umsteigerTyp5b,
            umsteigerSimbaSimba, umsteigerSimbaAndere, umsteigerAndereSimba, umsteigerAndereAndere);
        String[] columns = head.split(",");
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + oNTrainStrationsCount)) {
            for (TrainStation station : trainStationMap.values()) {
                csvWriter.set(runID, config.controller().getRunId());
                csvWriter.set(hstNumber, station.getHstNummer());
                csvWriter.set(stopCode, station.getStopCode());
                csvWriter.set(zone, station.getZoneId());
                csvWriter.set(zielAussteiger, Integer.toString((int) (station.getZielAussteiger() / sampleSize)));
                csvWriter.set(quellEinsteiger, Integer.toString((int) (station.getQuellEinsteiger() / sampleSize)));
                csvWriter.set(umsteigerTyp5a, Integer.toString((int) (station.getUmsteigerTyp5a() / sampleSize)));
                csvWriter.set(umsteigerTyp5b, Integer.toString((int) (station.getUmsteigerTyp5b() / sampleSize)));
                // divided by two because there are counted twice, we always look at the trip before and after the current one
                csvWriter.set(umsteigerSimbaSimba, Integer.toString((int) ((station.getUmsteigerSimbaSimba() / sampleSize)) / 2));
                csvWriter.set(umsteigerSimbaAndere, Integer.toString((int) ((station.getUmsteigerSimbaAndere() / sampleSize)) / 2));
                csvWriter.set(umsteigerAndereSimba, Integer.toString((int) ((station.getUmsteigerAndereSimba() / sampleSize)) / 2));
                csvWriter.set(umsteigerAndereAndere, Integer.toString((int) ((station.getUmsteigerAndereAndere() / sampleSize)) / 2));
                csvWriter.writeRow();
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

    }

    private void writeStopStationAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        final String hstNummer = "HST_Nummer";
        final String code = "Code";
        final String stopCode = "Stop_Code";
        final String stopNumber = "Stop_Nummer";
        final String trainStationName = "Name";
        final String stopName = "Stop_Name";
        final String x = "X";
        final String y = "Y";
        final String zone = "Zone";
        final String einstiege = "Einstiege_Gesamt";
        final String ausstiege = "Ausstiege_Gesamt";
        final String einstiegeFQ = "Einstiege_FQ_Gesamt";
        final String ausstiegeFQ = "Ausstiege_FQ_Gesamt";
        final String umstiege = "Umstiege_Bahn_Bahn";
        final String zustiege = "Umsteige_AHP_Bahn";
        final String wegstiege = "Umsteige_Bahn_AHP";
        final String isRailStop = "isRailStop";
        final String modesAtStop = "modesAtStop";


        List<String> headColumns = new ArrayList<>(List.of(runID, hstNummer, stopNumber, code, stopCode, trainStationName, stopName, isRailStop,modesAtStop, x, y, zone, einstiege, ausstiege, einstiegeFQ, ausstiegeFQ, umstiege, zustiege, wegstiege));
        for (String mode : possibleOriginDestinationModesAtStop) {
            headColumns.add("Zielaustieg_" + mode);
            headColumns.add("Quelleinstieg_" + mode);
        }
        String[] columns = headColumns.toArray(new String[0]);


        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + oNStopStationsCount)) {
            for (Entry<Id<TransitStopFacility>, StopStation> entry : stopStationsMap.entrySet()) {
                csvWriter.set(runID, config.controller().getRunId());
                csvWriter.set(hstNummer, entry.getValue().getStop().getAttributes().getAttribute(STOP_NO).toString());
                csvWriter.set(stopNumber, entry.getValue().getStop().getId().toString());
                if (entry.getValue().getIsRailStation()) {
                    csvWriter.set(isRailStop, "1");
                } else {
                    csvWriter.set(isRailStop, "0");
                }
                Id<TransitStopFacility> stopId = Id.create(entry.getKey(), TransitStopFacility.class);
                Object codeAttribute;
                codeAttribute = entry.getValue().getStop().getAttributes().getAttribute(STOP_CODE);
                if (codeAttribute == null) {
                    csvWriter.set(code, "NA");
                } else {
                    csvWriter.set(code, codeAttribute.toString());
                }
                Object stopCodeAttribute;
                try {
                    stopCodeAttribute =entry.getValue().getStop().getAttributes().getAttribute(STOP_AREA_CODE);
                } catch (Exception e){
                    stopCodeAttribute = "";
                }
                if (stopCodeAttribute == null) {
                    csvWriter.set(stopCode, "NA");
                } else {
                    csvWriter.set(stopCode, stopCodeAttribute.toString());
                }
                String name;
                name = transitSchedule.getFacilities().get(stopId).getName();
                if (name == null) {
                    name = "";
                }
                csvWriter.set(trainStationName, name.replaceAll(",", " "));
                var stpName = entry.getValue().getStop().getAttributes().getAttribute(STOP_AREA_NAME);
                String stpNameString = stpName != null ? stpName.toString() : "";

                csvWriter.set(stopName, stpNameString.replaceAll(",", " "));
                csvWriter.set(x, Double.toString(entry.getValue().getStop().getCoord().getX()));
                csvWriter.set(y, Double.toString(entry.getValue().getStop().getCoord().getY()));
                csvWriter.set(zone, entry.getValue().getZoneId());
                csvWriter.set(einstiege, Integer.toString((int) (entry.getValue().getEntered() / sampleSize)));
                csvWriter.set(ausstiege, Integer.toString((int) (entry.getValue().getExited() / sampleSize)));
                csvWriter.set(einstiegeFQ, Integer.toString((int) (entry.getValue().getEnteredFQ() / sampleSize)));
                csvWriter.set(ausstiegeFQ, Integer.toString((int) (entry.getValue().getExitedFQ() / sampleSize)));
                for (String mode : possibleOriginDestinationModesAtStop) {
                    csvWriter.set("Quelleinstieg_" + mode, Integer.toString((int) (entry.getValue().getEnteredMode()[possibleModesAtStop.indexOf(mode)] / sampleSize)));
                    csvWriter.set("Zielaustieg_" + mode, Integer.toString((int) (entry.getValue().getExitedMode()[possibleModesAtStop.indexOf(mode)] / sampleSize)));
                }
                csvWriter.set(umstiege, Integer.toString((int) (entry.getValue().getUmsteigeBahnBahn() / sampleSize)));
                csvWriter.set(zustiege, Integer.toString((int) (entry.getValue().getUmsteigeAHPBahn() / sampleSize)));
                csvWriter.set(wegstiege, Integer.toString((int) (entry.getValue().getUmsteigeBahnAHP() / sampleSize)));
                csvWriter.set(modesAtStop, String.join(",", actualModesAtStop.get(stopId)));
                csvWriter.writeRow();
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private void writeChanges() {
        final String umsteigetyp = "Umsteigetyp";
        String[] columns = {runID, subpopulation, umsteigetyp, "0", "1", "2", "3", "4", ">=5"};
        String[] columnsDistance = {runID, subpopulation, umsteigetyp, "distanceClass", "0", "1", "2", "3", "4", ">=5"};
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        try (CSVWriter csvWriter = new CSVWriter("", columns, outputLocation + oNChangesCount)) {
            Map<String, Integer> mapChange = new HashMap<>();
            mapChange.put("changesTrain", 0);
            mapChange.put("changesOPNV", 1);
            mapChange.put("changesOEV", 2);
            mapChange.put("changesTrainFQ", 3);
            mapChange.put("changesTrainAll", 4);
            for (Entry<String, double[][]> entry : subpopulationChangeMap.entrySet()) {
                for (Entry<String, Integer> change : mapChange.entrySet()) {
                    csvWriter.set(runID, config.controller().getRunId());
                    csvWriter.set(subpopulation, entry.getKey());
                    csvWriter.set(umsteigetyp, change.getKey());
                    for (int i = 0; i < 6; i++) {
                        csvWriter.set(changeLabelList.get(i), Integer.toString((int) (entry.getValue()[change.getValue()][i] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

        try (CSVWriter csvWriter = new CSVWriter("", columns, outputLocation + oNChangesPKM)) {
            Map<String, Integer> mapChange = new HashMap<>();
            mapChange.put("changesTrain", 0);
            mapChange.put("changesOPNV", 1);
            mapChange.put("changesOEV", 2);
            mapChange.put("changesTrainFQ", 3);
            mapChange.put("changesTrainAll", 4);
            for (Entry<String, double[][]> entry : subpopulationChangePKMMap.entrySet()) {
                for (Entry<String, Integer> change : mapChange.entrySet()) {
                    csvWriter.set(runID, config.controller().getRunId());
                    csvWriter.set(subpopulation, entry.getKey());
                    csvWriter.set(umsteigetyp, change.getKey());
                    for (int i = 0; i < 6; i++) {
                        csvWriter.set(changeLabelList.get(i), Integer.toString((int) (entry.getValue()[change.getValue()][i] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

        try (CSVWriter csvWriter = new CSVWriter("", columnsDistance, outputLocation + oNDistanceChangesCount)) {
            Map<String, Integer> mapChange = new HashMap<>();
            mapChange.put("changesTrain", 0);
            mapChange.put("changesOPNV", 1);
            mapChange.put("changesOEV", 2);
            mapChange.put("changesTrainFQ", 3);
            mapChange.put("changesTrainAll", 4);
            for (Entry<String, double[][]> entry : subpopulationDistanceChangeMap.entrySet()) {
                for (int d = 0; d< distanceClassesLabel.size(); d++) {
                    int offsetDistance = d * changeOrderList.size();
                    for (Entry<String, Integer> change : mapChange.entrySet()) {
                        csvWriter.set("distanceClass", distanceClassesLabel.get(d));
                        csvWriter.set(runID, config.controller().getRunId());
                        csvWriter.set(subpopulation, entry.getKey());
                        csvWriter.set(umsteigetyp, change.getKey());
                        for (int i = 0; i < 6; i++) {
                            csvWriter.set(changeLabelList.get(i), Integer.toString((int) (entry.getValue()[offsetDistance + change.getValue()][i] / sampleSize)));
                        }
                        csvWriter.writeRow();
                    }
                }
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private Map<String, Integer> createVariablesModalSplitMap() {
        Map<String, Integer> variables = new LinkedHashMap<>();
        variables.put(mode, 0);
        variables.put(all, 1);
        int i = 2;
        for (List<String> varList : varList) {
            for (String var : varList) {
                variables.put(var, i++);
            }
        }
        return variables;
    }

    private Map<String, Integer> createVariablesModalSplitFeederMap() {
        Map<String, Integer> variables = new LinkedHashMap<>();
        variables.put(accessMode, 0);
        variables.put(egressMode, 1);
        variables.put(all, 2);
        int i = 3;
        for (List<String> varList : varListFeeder) {
            for (String var : varList) {
                variables.put(var, i++);
            }
        }
        return variables;
    }

    private Map<Id<TransitStopFacility>, StopStation> generateStopStationMap() {
        assert transitSchedule != null;
        Map<Id<TransitStopFacility>, StopStation> stopStationsMap = new HashMap<>();
        for (TransitStopFacility transitStopFacility : transitSchedule.getFacilities().values()) {
            stopStationsMap.put(transitStopFacility.getId(), new StopStation(transitStopFacility, zones.findZone(transitStopFacility.getCoord()), possibleModesAtStop.size()));
        }
        return stopStationsMap;
    }

    private Map<String, Integer> createVariablesTimeStepsMap() {
        Map<String, Integer> variables = new LinkedHashMap<>();
        variables.put(all, 0);
        int i = 1;
        for (List<String> varList : varTimeList) {
            for (String var : varList) {
                variables.put(var, i++);
            }
        }
        return variables;
    }

    private Map<String, TrainStation> generateTrainStationMap() {
        Map<String, TrainStation> trainStationsMap = new HashMap<>();
        for (TransitStopFacility transitStopFacility : transitSchedule.getFacilities().values()) {
            String id = transitStopFacility.getAttributes().getAttribute(STOP_NO).toString();
            if (trainStationsMap.get(id) == null) {
                TrainStation trainStation = new TrainStation(transitStopFacility, zones.findZone(transitStopFacility.getCoord()));
                trainStation.addStop(transitStopFacility);
                trainStationsMap.put(id, trainStation);
            } else {
                TrainStation trainStation = trainStationsMap.get(id);
                trainStation.addStop(transitStopFacility);
            }
        }
        return trainStationsMap;
    }

    private Leg getLegAfter(List<Leg> legs, int currentLegIndex) {
        Leg legAfter = legs.get(currentLegIndex + 1);
        for (int i = currentLegIndex + 1; i < legs.size(); i++) {
            if (!legs.get(i).getMode().contains("walk")) {
                return legs.get(i);
            }
        }
        return legAfter;
    }

    /*
        Returns a map of modes we want to analyze as main modes. No submodes or feeder modes.
     */
    private Map<String, Integer> getAnalysisMainModesMap() {
        Map<String, Integer> coding = new HashMap<>();
        Set<String> modesSet = new HashSet<>();
        var raptorConfigGroup = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        var interModalModes = raptorConfigGroup.getIntermodalAccessEgressParameterSets()
                .stream()
                .map(set -> set.getMode())
                .collect(Collectors.toSet());
        modesSet.addAll(config.scoring().getAllModes());
        modesSet.remove(SBBModes.WALK_MAIN_MAINMODE);
        modesSet.removeAll(PTSubModes.submodes);
        modesSet.removeAll(interModalModes);
        modesSet.add(SBBModes.WALK_FOR_ANALYSIS);

        int i = 0;
        for (String mode : modesSet) {
            coding.put(mode, i);
            i++;
        }

        return coding;
    }

    private Map<String, Integer> getModesInclRailFQMap() {
        Map<String, Integer> coding = new HashMap<>();
        coding.putAll(modesMap);
        int numberOfModes = coding.values().stream().max(Integer::compare).get();
        coding.put(RAIL, numberOfModes + 1);
        coding.put("railFQ", numberOfModes + 2);
        return coding;
    }
    private Map<String, Integer> getFeederModesMap() {
        Map<String, Integer> coding = new HashMap<>();
        Set<String> modesSet = new HashSet<>(SBBModes.TRAIN_STATION_MODES);
        Set<String> additionalFeederModes = getFeederModesinConfig();
        modesSet.addAll(additionalFeederModes);
        List<String> modes = new ArrayList<>(modesSet);
        for (int i = 0; i < modes.size(); i++) {
            coding.put(modes.get(i), i);
        }
        return coding;
    }

    private Set<String> getFeederModesinConfig() {
        return ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class).getIntermodalAccessEgressParameterSets().stream().map(SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet::getMode).collect(Collectors.toSet());
    }

    private Map<String, int[][]> createTimeStepsForSubpopulaitonMap(int timeStepsSize, int varSize) {
        Map<String, int[][]> subpopulationMap = new HashMap<>();
        for (String subpopulation : analysisSubpopulations) {
            subpopulationMap.put(subpopulation, new int[timeStepsSize][varSize]);
        }
        return subpopulationMap;
    }

    private Map<String, double[][]> createArrayForSubpopulationMap(int modeSize, int varSize) {
        Map<String, double[][]> subpopulationMap = new HashMap<>();
        for (String subpopulation : analysisSubpopulations) {
            subpopulationMap.put(subpopulation, new double[modeSize][varSize]);
        }
        return subpopulationMap;
    }

    private TransitStopFacility getStartTrainFacility(Route route) {
        TransitPassengerRoute transitPassengerRoute = (TransitPassengerRoute) route;
        return transitSchedule.getFacilities().get(transitPassengerRoute.getAccessStopId());
    }

    private TransitStopFacility getEndTrainFacility(Route route) {
        TransitPassengerRoute transitPassengerRoute = (TransitPassengerRoute) route;
        return transitSchedule.getFacilities().get(transitPassengerRoute.getEgressStopId());
    }

    private String getModeOfTransitRoute(Route route) {
        if (route instanceof TransitPassengerRoute) {
            return transitSchedule.getTransitLines().get(((TransitPassengerRoute) route).getLineId()).getRoutes().get(((TransitPassengerRoute) route).getRouteId()).getTransportMode();
        }
        return null;
    }

}
