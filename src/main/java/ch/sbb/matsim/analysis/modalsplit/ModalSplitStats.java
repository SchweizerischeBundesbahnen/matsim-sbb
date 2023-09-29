package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
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

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;
import static ch.sbb.matsim.config.variables.SBBModes.PT;

public class ModalSplitStats {

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
    private Map<String, Integer> feederModesMap;
    private Map<String, Integer> variablesMSMap;
    private Map<String, Integer> variablesMSFeederMap;
    private Map<String, double[][]> subpopulaionDistanceMap;
    private Map<String, double[][]> subpopulaionMSPFMap;
    private Map<String, double[][]> subpopulaionMSPKMMap;
    private Map<String, double[][]> subpopulaionAccessMSPFMap;
    private Map<String, double[][]> subpopulaionAccessMSPKMMap;
    private Map<String, double[][]> subpopulaionEgressMSPFMap;
    private Map<String, double[][]> subpopulaionEgressMSPKMMap;
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

        final Config config = ConfigUtils.createConfig();
        config.controler().setRunId(runId);
        config.qsim().setEndTime(30 * 3600);
        config.controler().setOutputDirectory(outputFile);
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
        modalSplitStats.analyzeAndWriteStats(config.controler().getOutputDirectory(), experiencedPlans);
    }

    public void analyzeAndWriteStats(String outputLocation) {
        analyzeAndWriteStats(outputLocation, experiencedPlansService.getExperiencedPlans());
    }

    public void analyzeAndWriteStats(String outputLocation, IdMap<Person, Plan> experiencedPlans) {

        // prepare necessary information
        this.outputLocation = outputLocation + "SBB_";
        this.stopStationsMap = generateStopStationMap();
        this.trainStationMap = generateTrainStationMap();
        this.modesMap = getModesMap();
        this.feederModesMap = getFeederModesMap();
        this.variablesMSMap = createVariablesModalSplitMap();
        this.variablesMSFeederMap = createVariablesModalSplitFeederMap();
        this.variablesTimeStepsMap = createVariablesTimeStepsMap();
        this.subpopulaionDistanceMap = createArrayForSubpopulationMap(this.modesMap.size(), distanceClassesLable.size());
        this.subpopulaionMSPFMap = createArrayForSubpopulationMap(this.modesMap.size(), this.variablesMSMap.size());
        this.subpopulaionMSPKMMap = createArrayForSubpopulationMap(this.modesMap.size(), this.variablesMSMap.size());
        this.subpopulaionAccessMSPFMap = createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size());
        this.subpopulaionEgressMSPFMap = createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size());
        this.subpopulaionAccessMSPKMMap = createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size());
        this.subpopulaionEgressMSPKMMap = createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size());
        this.zonesAccessMSPFMap = new HashMap<>();
        this.zonesAccessMSPkmMap = new HashMap<>();
        this.zonesEgressMSPFMap = new HashMap<>();
        this.zonesEgressMSPkmMap = new HashMap<>();
        this.subpopulationChangeMap = createArrayForSubpopulationMap(changeOrderList.size(), changeLableList.size());
        this.subpopulationDistanceChangeMap = createArrayForSubpopulationMap(changeOrderList.size()*distanceClassesLable.size(), changeLableList.size());
        this.subpopulationChangePKMMap = createArrayForSubpopulationMap(changeOrderList.size(), changeLableList.size());
        this.subpopulationDistanceChangePKMMap = createArrayForSubpopulationMap(changeOrderList.size()*distanceClassesLable.size(), changeLableList.size());
        this.timeMap = createTimeStepsForSubpopulaitonMap((int) (this.config.qsim().getEndTime().seconds() / timeSplit), this.variablesTimeStepsMap.size());
        this.travelTimeMap = createTimeStepsForSubpopulaitonMap((lastTravelTimeValue / travelTimeSplit) + 1, this.variablesTimeStepsMap.size());

        // analyzing
        startAnalyze(experiencedPlans);

        // writing the different files
        writeStopStationAnalysis();
        writeTrainStationAnalysis();
        writeDistanceClassesAnalysis();
        writeModalSplit();
        writeFeederModalSplit();
        writeChanges();
        writeTimeSteps();

    }

    private void startAnalyze(IdMap<Person, Plan> experiencedPlans) {
        for (Entry<Id<Person>, Plan> entry : experiencedPlans.entrySet()) {

            // analysis for access and egress mode for each stop station
            analyzeStopsStations(entry);
            // analysis for access and egress mode for each train station
            analyzeTrainsStations(entry);
            // analysis for distance classes
            analyzeDistanceClasses(entry);
            // analysis modal split for persons trips and person km
            analyzeModalSplit(entry);
            // analysis access/egress modal split for persons trips and person km
            analyzeFeederModalSplit(entry);
            // analysis public transport changes
            analyzeChanges(entry);
            // analyze travel time and middle time between to activities
            analyzeTimes(entry);

        }
    }

    private void analyzeTrainsStations(Entry<Id<Person>, Plan> entry) {
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(PT)) {
                List<Leg> legs = trip.getLegsOnly();
                Leg legBefore = pf.createLeg(SBBModes.WALK_FOR_ANALYSIS);
                for (Leg leg : legs) {
                    if (leg.getMode().equals(PT)) {
                        Route route = leg.getRoute();
                        String startTrainStationId = getStartTrainFacility(route).getAttributes().getAttribute("02_Stop_No").toString();
                        String endTrainStationId = getEndTrainFacility(route).getAttributes().getAttribute("02_Stop_No").toString();
                        String subMode = getModeOfTransitRoute(leg.getRoute());
                        Leg legAfter = getLegAfter(legs, legs.indexOf(leg));
                        if (railTripsAnalyzer.hasFQRelevantLeg(List.of((TransitPassengerRoute) leg.getRoute()))) {
                            if (legBefore.getMode().equals(PT) &&
                                    getModeOfTransitRoute(legBefore.getRoute()).equals(PTSubModes.RAIL) &&
                                    subMode.equals(PTSubModes.RAIL)) {
                                if (!getEndTrainFacility(legBefore.getRoute()).getAttributes().getAttribute("02_Stop_No").toString().equals(startTrainStationId)) {
                                    trainStationMap.get(startTrainStationId).addUmsteigerTyp5b();
                                } else if (railTripsAnalyzer.hasFQRelevantLeg(List.of((TransitPassengerRoute) legBefore.getRoute()))) {
                                    trainStationMap.get(startTrainStationId).addUmsteigerSimbaSimba();
                                } else {
                                    trainStationMap.get(startTrainStationId).addUmsteigerAndereSimba();
                                }
                            } else {
                                trainStationMap.get(startTrainStationId).addQuellEinsteiger();
                            }
                            if (legAfter.getMode().equals(PT) &&
                                    getModeOfTransitRoute(legAfter.getRoute()).equals(PTSubModes.RAIL) &&
                                    subMode.equals(PTSubModes.RAIL)) {
                                if (!getStartTrainFacility(legAfter.getRoute()).getAttributes().getAttribute("02_Stop_No").toString().equals(endTrainStationId)) {
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
                    if (leg.getMode().equals(PT)) {
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
                            startStopStation.getEnteredMode()[StopStation.getModes().indexOf(legBefore.getMode())]++;
                            if (legBefore.getMode().equals(PT)) {
                                subPTMode = getModeOfTransitRoute(legBefore.getRoute());
                                startStopStation.getEnteredMode()[StopStation.getModes().indexOf(subPTMode)]++;
                                if (getEndTrainFacility(legBefore.getRoute()).getAttributes().getAttribute("02_Stop_No").equals(startStopStationFacility.getAttributes().getAttribute("02_Stop_No"))) {
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
                            startStopStation.getEnteredMode()[StopStation.getModes().indexOf("walk")] = startStopStation.getEnteredMode()[StopStation.getModes().indexOf("walk")] + 1;
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
                        endStopStation.getExitedMode()[StopStation.getModes().indexOf(legAfter.getMode())]++;
                        if (legAfter.getMode().equals(PT)) {
                            subPTMode = getModeOfTransitRoute(legAfter.getRoute());
                            endStopStation.getExitedMode()[StopStation.getModes().indexOf(subPTMode)]++;
                            if (getStartTrainFacility(legAfter.getRoute()).getAttributes().getAttribute("02_Stop_No").equals(endStopStationFacility.getAttributes().getAttribute("02_Stop_No"))) {
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

    private void analyzeTimes(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (tmpMode.equals(SBBModes.WALK_MAIN_MAINMODE)) {
                tmpMode = SBBModes.WALK_FOR_ANALYSIS;
            }
            boolean tmpIsRail = false;
            double fqDistance = 0;
            for (Leg leg : trip.getLegsOnly()) {
                if (PT.contains(leg.getMode())) {
                    if (leg.getMode().equals(PT)) {
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
            int[][] subpopulationArrray = timeMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
            subpopulationArrray[time][variablesTimeStepsMap.get(all)]++;
            int travelTime = (int) ((trip.getDestinationActivity().getStartTime().seconds() - trip.getOriginActivity().getEndTime().seconds()));
            int timeArray = (travelTime - (travelTime % travelTimeSplit)) / travelTimeSplit;
            int[][] subpopulationTravelTime = travelTimeMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
            if (timeArray >= subpopulationTravelTime.length) {
                timeArray = subpopulationTravelTime.length - 1;
            }
            subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(all)]++;
            for (String mode : modesMS) {
                if ((MSVariables.mode + separator + tmpMode).equals(mode)) {
                    subpopulationArrray[time][variablesTimeStepsMap.get(mode)]++;
                    subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(mode)]++;
                    break;
                }
            }
            if(tmpIsRail) {
                String submode = MSVariables.submode + separator + SBBModes.RAIL;
                subpopulationArrray[time][variablesTimeStepsMap.get(submode)]++;
                subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(submode)]++;
                fqDistance = railTripsAnalyzer.getFQDistance(trip, true);
                if (fqDistance > 0) {
                    submode = MSVariables.submode + separator + SBBModes.FQRAIL;
                    subpopulationArrray[time][variablesTimeStepsMap.get(submode)]++;
                    subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(submode)]++;
                }
            }
            for (String activity : toActTypeList) {
                if ((MSVariables.toActType + separator + tmpActivity).equals(activity)) {
                    subpopulationArrray[time][variablesTimeStepsMap.get(activity)]++;
                    subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(activity)]++;
                    break;
                }
            }
        }
    }

    private void analyzeChanges(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            int ptLegs = 0;
            int raillegs = 0;
            double distance = 0;
            boolean isFQ = false;
            for (Leg leg : trip.getLegsOnly()) {
                if (PT.contains(leg.getMode())) {
                    if (leg.getMode().equals(PT)) {
                        distance += leg.getRoute().getDistance() / 1000;
                        ptLegs++;
                        TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                        if (getModeOfTransitRoute(route).equals(PTSubModes.RAIL)) {
                            raillegs++;
                        }
                    }
                }
            }
            double fqDistance = 0;
            if (raillegs > 0) {
                fqDistance = railTripsAnalyzer.getFQDistance(trip, true);
                isFQ = (fqDistance > 0);
            }

            double[][] changeArray = subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
            double[][] changeArrayPKM = subpopulationChangePKMMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
            double[][] changeDistanceArray = subpopulationDistanceChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
            double[][] changeDistanceArrayPKM = subpopulationDistanceChangePKMMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());

            int offsetDistance = distanceClassesValue.size()-1;

            for (int disClass : distanceClassesValue) {
                if (distance <= disClass) {
                    offsetDistance = distanceClassesValue.indexOf(disClass);
                    break;
                }
            }

            offsetDistance = offsetDistance * changeOrderList.size();

            boolean isMixed = ((raillegs > 0) & (ptLegs > raillegs));
            if (ptLegs > 6) {
                ptLegs = 6;
                if (raillegs > 6) {
                    raillegs = 6;
                }
            }
            if (raillegs > 0) {
                changeArray[changeOrderList.indexOf(changeTrainAll)][raillegs - 1]++;
                changeArrayPKM[changeOrderList.indexOf(changeTrainAll)][raillegs - 1] += distance;
                changeDistanceArray[offsetDistance + changeOrderList.indexOf(changeTrainAll)][raillegs - 1]++;
                changeDistanceArrayPKM[offsetDistance + changeOrderList.indexOf(changeTrainAll)][raillegs - 1] += distance;
                if (isMixed) {
                    changeArray[changeOrderList.indexOf(changeOEV)][ptLegs - 1]++;
                    changeArrayPKM[changeOrderList.indexOf(changeOEV)][ptLegs - 1]+=distance;
                    changeDistanceArray[offsetDistance + changeOrderList.indexOf(changeOEV)][ptLegs - 1]++;
                    changeDistanceArrayPKM[offsetDistance + changeOrderList.indexOf(changeOEV)][ptLegs - 1]+=distance;
                } else {
                    changeArray[changeOrderList.indexOf(changeTrain)][raillegs - 1]++;
                    changeArrayPKM[changeOrderList.indexOf(changeTrain)][raillegs - 1] += distance;
                    changeDistanceArray[offsetDistance + changeOrderList.indexOf(changeTrain)][raillegs - 1]++;
                    changeDistanceArrayPKM[offsetDistance + changeOrderList.indexOf(changeTrain)][raillegs - 1] += distance;
                }
                if (isFQ) {
                    changeArray[changeOrderList.indexOf(changeTrainFQ)][raillegs - 1]++;
                    changeArrayPKM[changeOrderList.indexOf(changeTrainFQ)][raillegs - 1] += fqDistance;
                    changeDistanceArray[offsetDistance +changeOrderList.indexOf(changeTrainFQ)][raillegs - 1]++;
                    changeDistanceArrayPKM[offsetDistance +changeOrderList.indexOf(changeTrainFQ)][raillegs - 1] += fqDistance;
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
                    if (leg.getMode().equals(PT)) {
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

            double[][] pfArray = subpopulaionMSPFMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
            pfArray[modeId][variablesMSMap.get(all)]++;

            double[][] pkmArray = subpopulaionMSPKMMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
            pkmArray[modeId][variablesMSMap.get(all)] += distance;

            if(tmpIsRail) {
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
            // age categorie
            for (String age : ageCategory) {
                if (attributes.getAttribute(Variables.AGE_CATEGORY) != null && (Variables.AGE_CATEGORY + "_" + attributes.getAttribute(Variables.AGE_CATEGORY).toString()).equals(age)) {
                    pfArray[modeId][variablesMSMap.get(age)]++;
                    pkmArray[modeId][variablesMSMap.get(age)] += distance;
                    break;
                }
            }
            // activity type for end activity
            String acttyp = trip.getDestinationActivity().getType();
            for (String act : toActTypeList) {
                if (acttyp.contains(separator)) {
                    acttyp = acttyp.substring(0, acttyp.indexOf("_"));
                }
                if ((toActType + separator + acttyp).equals(act)) {
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
            if (leg.getMode().equals(PT)) {
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
            if (leg.getMode().equals(PT)) {
                if (getModeOfTransitRoute(leg.getRoute()).equals(SBBModes.PTSubModes.RAIL)) {
                    lastLeg =  legs.indexOf(leg);
                }
            }
        }
        return lastLeg;
    }

    private void analyzeFeederModalSplit(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
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
                    int subPTModeEntered = SBBModes.TRAIN_FEEDER_MODES.indexOf("walk");
                    double distanceEnter = 0;
                    for (int i = 0; i < firstRailLeg; i++) {
                        Leg leg = legs.get(i);
                        if (leg.getRoute() != null) {
                            distanceEnter += leg.getRoute().getDistance();
                        }
                        if (leg.getMode().equals(PT)) {
                            String subPTMode = getModeOfTransitRoute(leg.getRoute());
                            subPTModeEntered = SBBModes.TRAIN_FEEDER_MODES.indexOf(subPTMode);
                        } else {
                            if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                                subPTModeEntered = SBBModes.TRAIN_FEEDER_MODES.indexOf(leg.getMode());
                            }
                        }
                    }

                    int subPTModeExited = SBBModes.TRAIN_FEEDER_MODES.indexOf("walk");
                    double distanceExit = 0;
                    for (int i = lastRailLeg + 1; i < legs.size(); i++) {
                        Leg leg = legs.get(i);
                        if (leg.getRoute() != null) {
                            distanceExit += leg.getRoute().getDistance();
                        }
                        if (leg.getMode().equals(PT)) {
                            String subPTMode = getModeOfTransitRoute(leg.getRoute());
                            subPTModeExited = SBBModes.TRAIN_FEEDER_MODES.indexOf(subPTMode);
                        } else {
                            if (!leg.getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                                subPTModeExited = SBBModes.TRAIN_FEEDER_MODES.indexOf(leg.getMode());
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

                        if (originZone != null) {
                            originZoneId = String.valueOf(originZone.getId());
                        }
                        if (destZone != null) {
                            destZoneId = String.valueOf(destZone.getId());
                        }

                        if (zonesAccessMSPFMap.get(originZoneId) == null) {
                            zonesAccessMSPFMap.put(originZoneId, createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size()));
                            zonesAccessMSPkmMap.put(originZoneId, createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size()));
                        }
                        if (zonesEgressMSPFMap.get(destZoneId) == null) {
                            zonesEgressMSPFMap.put(destZoneId, createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size()));
                            zonesEgressMSPkmMap.put(destZoneId, createArrayForSubpopulationMap(this.feederModesMap.size(), this.variablesMSFeederMap.size()));
                        }

                        double[][] pfOriginZoneArray = zonesAccessMSPFMap.get(originZoneId).get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
                        double[][] pfDestZoneArray = zonesEgressMSPFMap.get(destZoneId).get(attributes.getAttribute(Variables.SUBPOPULATION).toString());

                        double[][] pfAccessArray = subpopulaionAccessMSPFMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
                        pfAccessArray[subPTModeEntered][variablesMSFeederMap.get(all)]++;
                        pfOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(all)]++;
                        double[][] pfEgressArray = subpopulaionEgressMSPFMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
                        pfEgressArray[subPTModeExited][variablesMSFeederMap.get(all)]++;
                        pfDestZoneArray[subPTModeExited][variablesMSFeederMap.get(all)]++;

                        double[][] pkmOriginZoneArray = zonesAccessMSPkmMap.get(originZoneId).get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
                        double[][] pkmDestZoneArray = zonesEgressMSPkmMap.get(destZoneId).get(attributes.getAttribute(Variables.SUBPOPULATION).toString());

                        double[][] pkmAccessArray = subpopulaionAccessMSPKMMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
                        pkmAccessArray[subPTModeEntered][variablesMSFeederMap.get(all)] += distanceEnter;
                        pkmOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(all)] += distanceEnter;
                        double[][] pkmEgressArray = subpopulaionEgressMSPKMMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
                        pkmEgressArray[subPTModeExited][variablesMSFeederMap.get(all)] += distanceExit;
                        pkmDestZoneArray[subPTModeExited][variablesMSFeederMap.get(all)] += distanceExit;


                        Map<List<String>, List<String>> subgroups = getSubgroups();

                        for (Entry<List<String>, List<String>> subgroup : subgroups.entrySet()) {
                            // car available
                            for (String value : subgroup.getValue()) {
                                List<String> variables = subgroup.getKey();
                                String att = "";
                                for (String variable : variables) {
                                    if (attributes.getAttribute(variable) != null) {
                                        if (att.length() > 0) {
                                            att = att + "_";
                                        }
                                        att = att + variable + "_" + attributes.getAttribute(variable).toString();
                                    }
                                }
                                if (att.equals(value)) {
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
                        String acttyp = trip.getDestinationActivity().getType();
                        if (acttyp.contains(separator)) {
                            acttyp = acttyp.substring(0, acttyp.indexOf("_"));
                        }
                        for (String act : toActTypeList) {
                            if ((toActType + separator + acttyp).equals(act)) {
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
                                pfAccessArray[subPTModeEntered][variablesMSFeederMap.get(distanceClassesFeederLable.get(distanceClassesFeederValue.indexOf(disClass)))]++;
                                pkmAccessArray[subPTModeEntered][variablesMSFeederMap.get(distanceClassesFeederLable.get(distanceClassesFeederValue.indexOf(disClass)))] += distanceEnter;
                                pfOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(distanceClassesFeederLable.get(distanceClassesFeederValue.indexOf(disClass)))]++;
                                pkmOriginZoneArray[subPTModeEntered][variablesMSFeederMap.get(distanceClassesFeederLable.get(distanceClassesFeederValue.indexOf(disClass)))] += distanceEnter;
                                break;
                            }
                        }

                        for (int disClass : distanceClassesFeederValue) {
                            if (distanceExit <= disClass) {
                                pfEgressArray[subPTModeExited][variablesMSFeederMap.get(distanceClassesFeederLable.get(distanceClassesFeederValue.indexOf(disClass)))]++;
                                pkmEgressArray[subPTModeExited][variablesMSFeederMap.get(distanceClassesFeederLable.get(distanceClassesFeederValue.indexOf(disClass)))] += distanceExit;
                                pfDestZoneArray[subPTModeExited][variablesMSFeederMap.get(distanceClassesFeederLable.get(distanceClassesFeederValue.indexOf(disClass)))]++;
                                pkmDestZoneArray[subPTModeExited][variablesMSFeederMap.get(distanceClassesFeederLable.get(distanceClassesFeederValue.indexOf(disClass)))] += distanceExit;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void analyzeDistanceClasses(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = this.population.getPersons().get(entry.getKey()).getAttributes();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (tmpMode.equals("walk_main")) {
                tmpMode = "walk";
            }
            int modeID = this.modesMap.get(tmpMode);
            double distance = 0;
            for (Leg leg : trip.getLegsOnly()) {
                distance += leg.getRoute().getDistance() / 1000;
            }
            double[][] disArray = this.subpopulaionDistanceMap.get(attributes.getAttribute(Variables.SUBPOPULATION).toString());
            for (int disClass : distanceClassesValue) {
                if (distance <= disClass) {
                    disArray[modeID][distanceClassesValue.indexOf(disClass)]++;
                    break;
                }
            }
        }
    }

    private void writeDistanceClassesAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(this.config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = new String[3 + distanceClassesValue.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        columns[2] = mode;
        for (int i = 0; i < distanceClassesValue.size(); i++) {
            columns[i + 3] = distanceClassesLable.get(i);
        }
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + oNDistanceClasses)) {
            for (String tmpSubpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> col : this.modesMap.entrySet()) {
                    csvWriter.set(runID, this.config.controler().getRunId());
                    csvWriter.set(subpopulation, tmpSubpopulation);
                    csvWriter.set(mode, col.getKey());
                    for (int i = 0; i < distanceClassesValue.size(); i++) {
                        csvWriter.set(distanceClassesLable.get(i), Integer.toString((int) (this.subpopulaionDistanceMap.get(tmpSubpopulation)[col.getValue()][i] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (IOException e) {
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
                    csvWriter.set(runID, config.controler().getRunId());
                    csvWriter.set(subpopulation, entry.getKey());
                    csvWriter.set(time, Integer.toString(i * timeSplit));
                    for (Entry<String, Integer> var : variablesTimeStepsMap.entrySet()) {
                        csvWriter.set(var.getKey(), Integer.toString((int) (entry.getValue()[i][var.getValue()] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try (CSVWriter csvWriter = new CSVWriter("", columns, outputLocation + oNTravelTimeDistribution)) {
            for (Entry<String, int[][]> entry : travelTimeMap.entrySet()) {
                for (int i = 0; i < (lastTravelTimeValue / travelTimeSplit) + 1; i++) {
                    csvWriter.set(runID, config.controler().getRunId());
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
            e.printStackTrace();
        }
    }

    private void writeModalSplit() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] colums = new String[2 + variablesMSMap.size()];
        colums[0] = runID;
        colums[1] = subpopulation;
        int i = 2;
        for (String var : this.variablesMSMap.keySet()) {
            colums[i++] = var;
        }
        try (CSVWriter csvWriterPF = new CSVWriter("", colums, outputLocation + oNModalSplitPF)) {
            for (String tmpSubpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> modeEntry : modesMap.entrySet()) {
                    csvWriterPF.set(runID, config.controler().getRunId());
                    csvWriterPF.set(subpopulation, tmpSubpopulation);
                    for (Entry<String, Integer> entry : this.variablesMSMap.entrySet()) {
                        if (entry.getKey().equals(mode)) {
                            csvWriterPF.set(mode, modeEntry.getKey());
                        } else {
                            String key = entry.getKey();
                            Integer value = entry.getValue();
                            csvWriterPF.set(key, Integer.toString((int) (this.subpopulaionMSPFMap.get(tmpSubpopulation)[modeEntry.getValue()][value] / sampleSize)));
                        }
                    }
                    csvWriterPF.writeRow();
                }
            }
        } catch (
            IOException e) {
            e.printStackTrace();
        }
        try (CSVWriter csvWriterPKM = new CSVWriter("", colums, outputLocation + oNModalSplitPKM)) {
            for (String tmpSubpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> modeEntry : modesMap.entrySet()) {
                    csvWriterPKM.set(runID, config.controler().getRunId());
                    csvWriterPKM.set(subpopulation, tmpSubpopulation);
                    for (Entry<String, Integer> entry : this.variablesMSMap.entrySet()) {
                        if (entry.getKey().equals(mode)) {
                            csvWriterPKM.set(mode, modeEntry.getKey());
                        } else {
                            String key = entry.getKey();
                            Integer value = entry.getValue();
                            csvWriterPKM.set(key, Integer.toString((int) (this.subpopulaionMSPKMMap.get(tmpSubpopulation)[modeEntry.getValue()][value] / sampleSize)));
                        }
                    }
                    csvWriterPKM.writeRow();
                }
            }
        } catch (
            IOException e) {
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
            for (String tmpSubpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> modeAccessEntry : feederModesMap.entrySet()) {
                    csvWriterPF.set(runID, config.controler().getRunId());
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
                                csvWriterPF.set(key, Integer.toString((int) (this.subpopulaionAccessMSPFMap.get(tmpSubpopulation)[modeAccessEntry.getValue()][value] / sampleSize)));
                            }
                        }
                    }
                    csvWriterPF.writeRow();
                }
                for (Entry<String, Integer> modeEgressEntry : feederModesMap.entrySet()) {
                    csvWriterPF.set(runID, config.controler().getRunId());
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
                                csvWriterPF.set(key, Integer.toString((int) (this.subpopulaionEgressMSPFMap.get(tmpSubpopulation)[modeEgressEntry.getValue()][value] / sampleSize)));
                            }
                        }
                    }
                    csvWriterPF.writeRow();
                }
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
        try (CSVWriter csvWriterPKM = new CSVWriter("", columns, outputLocation + oNModalSplitFeederPKM)) {
            for (String tmpSubpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> accessModeEntry : feederModesMap.entrySet()) {
                    csvWriterPKM.set(runID, config.controler().getRunId());
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
                                csvWriterPKM.set(key, Integer.toString((int) (this.subpopulaionAccessMSPKMMap.get(tmpSubpopulation)[accessModeEntry.getValue()][value] / sampleSize)));
                            }
                        }
                    }
                    csvWriterPKM.writeRow();
                }
                for (Entry<String, Integer> egrcessModeEntry : feederModesMap.entrySet()) {
                    csvWriterPKM.set(runID, config.controler().getRunId());
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
                                csvWriterPKM.set(key, Integer.toString((int) (this.subpopulaionEgressMSPKMMap.get(tmpSubpopulation)[egrcessModeEntry.getValue()][value] / sampleSize)));
                            }
                        }
                    }
                    csvWriterPKM.writeRow();
                }
            }
        } catch (
                IOException e) {
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
            for (String tmpSubpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Map<String, double[][]>> zoneEntry : zonesAccessMSPFMap.entrySet()){
                    for (Entry<String, Integer> accessModeEntry : feederModesMap.entrySet()) {
                        if (zonesAccessMSPFMap.get(zoneEntry.getKey()).get(tmpSubpopulation)[accessModeEntry.getValue()][this.variablesMSFeederMap.get("all")]>0) {
                            csvWriterPKM.set(runID, config.controler().getRunId());
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
                            csvWriterPKM.set(runID, config.controler().getRunId());
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
                csvWriter.set(runID, config.controler().getRunId());
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
            e.printStackTrace();
        }

    }

    private void writeStopStationAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        final String hstNummer = "HST_Nummer";
        final String code = "Code";
        final String stopNumber = "Stop_Nummer";
        final String trainStationName = "Name";
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
        StringBuilder head = new StringBuilder(
            String.join(",", runID, hstNummer, stopNumber, code, isRailStop, trainStationName, x, y, zone, einstiege, ausstiege, einstiegeFQ, ausstiegeFQ, umstiege, zustiege, wegstiege));
        for (String mode : StopStation.getOrigDestModes()) {
            head.append(",").append("Zielaustieg_").append(mode);
            head.append(",").append("Quelleinstieg_").append(mode);
        }
        String[] columns = head.toString().split(",");
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + oNStopStationsCount)) {
            for (Entry<Id<TransitStopFacility>, StopStation> entry : stopStationsMap.entrySet()) {
                csvWriter.set(runID, config.controler().getRunId());
                csvWriter.set(hstNummer, entry.getValue().getStop().getAttributes().getAttribute("02_Stop_No").toString());
                csvWriter.set(stopNumber, entry.getValue().getStop().getId().toString());
                if (entry.getValue().getIsRailStation()) {
                    csvWriter.set(isRailStop, "1");
                } else {
                    csvWriter.set(isRailStop, "0");
                }
                Id<TransitStopFacility> stopId = Id.create(entry.getKey(), TransitStopFacility.class);
                Object codeAttribute = entry.getValue().getStop().getAttributes().getAttribute("03_Stop_Code");
                if (codeAttribute == null) {
                    csvWriter.set(code, "NA");
                } else {
                    csvWriter.set(code, codeAttribute.toString());
                }
                String name = transitSchedule.getFacilities().get(stopId).getName();
                if (name == null) {
                    name = "";
                }
                csvWriter.set(trainStationName, name.replaceAll(",", " "));
                csvWriter.set(x, Double.toString(entry.getValue().getStop().getCoord().getX()));
                csvWriter.set(y, Double.toString(entry.getValue().getStop().getCoord().getY()));
                csvWriter.set(zone, entry.getValue().getZoneId());
                csvWriter.set(einstiege, Integer.toString((int) (entry.getValue().getEntered() / sampleSize)));
                csvWriter.set(ausstiege, Integer.toString((int) (entry.getValue().getExited() / sampleSize)));
                csvWriter.set(einstiegeFQ, Integer.toString((int) (entry.getValue().getEnteredFQ() / sampleSize)));
                csvWriter.set(ausstiegeFQ, Integer.toString((int) (entry.getValue().getExitedFQ() / sampleSize)));
                for (String mode : StopStation.getOrigDestModes()) {
                    csvWriter.set("Quelleinstieg_" + mode, Integer.toString((int) (entry.getValue().getEnteredMode()[StopStation.getModes().indexOf(mode)] / sampleSize)));
                    csvWriter.set("Zielaustieg_" + mode, Integer.toString((int) (entry.getValue().getExitedMode()[StopStation.getModes().indexOf(mode)] / sampleSize)));
                }
                csvWriter.set(umstiege, Integer.toString((int) (entry.getValue().getUmsteigeBahnBahn() / sampleSize)));
                csvWriter.set(zustiege, Integer.toString((int) (entry.getValue().getUmsteigeAHPBahn() / sampleSize)));
                csvWriter.set(wegstiege, Integer.toString((int) (entry.getValue().getUmsteigeBahnAHP() / sampleSize)));
                csvWriter.writeRow();
            }
        } catch (Exception e) {
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
                    csvWriter.set(runID, config.controler().getRunId());
                    csvWriter.set(subpopulation, entry.getKey());
                    csvWriter.set(umsteigetyp, change.getKey());
                    for (int i = 0; i < 6; i++) {
                        csvWriter.set(changeLableList.get(i), Integer.toString((int) (entry.getValue()[change.getValue()][i] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
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
                    csvWriter.set(runID, config.controler().getRunId());
                    csvWriter.set(subpopulation, entry.getKey());
                    csvWriter.set(umsteigetyp, change.getKey());
                    for (int i = 0; i < 6; i++) {
                        csvWriter.set(changeLableList.get(i), Integer.toString((int) (entry.getValue()[change.getValue()][i] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
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
                for (int d=0; d<distanceClassesLable.size(); d++) {
                    int offsetDistance = d * changeOrderList.size();
                    for (Entry<String, Integer> change : mapChange.entrySet()) {
                        csvWriter.set("distanceClass", distanceClassesLable.get(d));
                        csvWriter.set(runID, config.controler().getRunId());
                        csvWriter.set(subpopulation, entry.getKey());
                        csvWriter.set(umsteigetyp, change.getKey());
                        for (int i = 0; i < 6; i++) {
                            csvWriter.set(changeLableList.get(i), Integer.toString((int) (entry.getValue()[offsetDistance + change.getValue()][i] / sampleSize)));
                        }
                        csvWriter.writeRow();
                    }
                }
            }
        } catch (Exception e) {
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
            stopStationsMap.put(transitStopFacility.getId(), new StopStation(transitStopFacility, zones.findZone(transitStopFacility.getCoord())));
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
            String id = transitStopFacility.getAttributes().getAttribute("02_Stop_No").toString();
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

    private Map<String, Integer> getModesMap() {
        Map<String, Integer> coding = new HashMap<>();
        List<String> modes = SBBModes.MAIN_MODES;
        for (int i = 0; i < modes.size(); i++) {
            coding.put(modes.get(i), i);
        }
        return coding;
    }

    private Map<String, Integer> getFeederModesMap() {
        Map<String, Integer> coding = new HashMap<>();
        List<String> modes = SBBModes.TRAIN_FEEDER_MODES;
        for (int i = 0; i < modes.size(); i++) {
            coding.put(modes.get(i), i);
        }
        return coding;
    }

    private Map<String, int[][]> createTimeStepsForSubpopulaitonMap(int timeStepsSize, int varSize) {
        Map<String, int[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new int[timeStepsSize][varSize]);
        }
        return subpopulaionMap;
    }

    private Map<String, double[][]> createArrayForSubpopulationMap(int modeSize, int varSize) {
        Map<String, double[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new double[modeSize][varSize]);
        }
        return subpopulaionMap;
    }

    private TransitStopFacility getStartTrainFacility(Route route) {
        String description = route.getRouteDescription();
        int startIndex = description.indexOf("\"accessFacilityId\":\"") + "\"accessFacilityId\":\"".length();
        int endIndex = description.indexOf("\",\"egressFacilityId");
        int startString = Integer.parseInt(description.substring(startIndex, endIndex));
        return transitSchedule.getFacilities().get(Id.create(startString, TransitStopFacility.class));
    }

    private TransitStopFacility getEndTrainFacility(Route route) {
        String description = route.getRouteDescription();
        int endIndex = description.indexOf("egressFacilityId\":\"") + "\",\"egressFacilityId".length();
        int endString = Integer.parseInt(description.substring(endIndex, description.length() - 2));
        return transitSchedule.getFacilities().get(Id.create(endString, TransitStopFacility.class));
    }

    private String getModeOfTransitRoute(Route route) {
        if (route instanceof TransitPassengerRoute) {
            return transitSchedule.getTransitLines().get(((TransitPassengerRoute) route).getLineId()).getRoutes().get(((TransitPassengerRoute) route).getRouteId()).getTransportMode();
        }
        return null;
    }

}
