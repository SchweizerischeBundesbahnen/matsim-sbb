package ch.sbb.matsim.analysis.modalsplit;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
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

public class ModalSplitStats {

    @Inject private ExperiencedPlansService experiencedPlansService;
    @Inject private Population population;
    @Inject private Config config;
    @Inject private TransitSchedule transitSchedule;
    private final Zones zones;
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final LongestMainModeIdentifier mainModeIdentifier = new LongestMainModeIdentifier();
    private final PopulationFactory pf = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
    private String outputLocation;
    private Map<Id<TransitStopFacility>, StopStation> stopStationsMap;
    private Map<String, Integer> modesMap;
    private Map<String, Integer> variablesMSMap;
    private Map<String, double[][]> subpopulaionDistanceMap;
    private Map<String, double[][]> subpopulaionMSPFMap;
    private Map<String, double[][]> subpopulaionMSPKMMap;
    private Map<String, double[][]> subpopulationChangeMap;
    private Map<String, int[][]> timeMap;
    private Map<String, int[][]> travelTimeMap;
    private Map<String, Integer> variablesTimeStepsMap;
    private Map<String, TrainStation> trainStationMap;

    @Inject
    public ModalSplitStats(ZonesCollection zonesCollection, final PostProcessingConfigGroup ppConfig, RailTripsAnalyzer railTripsAnalyzer) {
        this.zones = zonesCollection.getZones(ppConfig.getZonesId());
        this.railTripsAnalyzer = railTripsAnalyzer;
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
        config.qsim().setEndTime(30*3600);
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        config.controler().setOutputDirectory(outputFile);
        ppConfig.setSimulationSampleSize(sampleSize);
        ppConfig.setZonesId("zones");
        Scenario scenario = ScenarioUtils.createScenario(config);
        Scenario scenario2 = ScenarioUtils.createScenario(config);

        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new PopulationReader(scenario).readFile(plansFile);
        new PopulationReader(scenario2).readFile(experiencedPlansFile);

        IdMap<Person, Plan> experiencedPlans = new IdMap<>(Person.class, scenario2.getPopulation().getPersons().size());
        scenario2.getPopulation().getPersons().values().forEach(p -> experiencedPlans.put(p.getId(), p.getSelectedPlan()));

        Zones zones = ZonesLoader.loadZones("zones", zonesFile, "zone_id");
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(zones);
        RailTripsAnalyzer railTripsAnalyzer1 = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());

        ModalSplitStats modalSplitStats = new ModalSplitStats(zonesCollection, ppConfig,  railTripsAnalyzer1);
        modalSplitStats.config = config;
        modalSplitStats.transitSchedule = scenario.getTransitSchedule();
        modalSplitStats.population = scenario.getPopulation();

        modalSplitStats.analyzeAndWriteStats(config.controler().getOutputDirectory(), experiencedPlans);
    }

    public void analyzeAndWriteStats(String outputLocation) {
        analyzeAndWriteStats(outputLocation, experiencedPlansService.getExperiencedPlans());
    }

    private void analyzeAndWriteStats(String outputLocation, IdMap<Person, Plan> experiencedPlans) {

        // prepare necessary information
        this.outputLocation = outputLocation + "SBB_";
        this.stopStationsMap = generateStopStationMap();
        this.trainStationMap = generateTrainStationMap();
        this.modesMap = getModesMap();
        this.variablesMSMap = createVariablesModalSplitMap();
        this.variablesTimeStepsMap = createVariablesTimeStepsMap();
        this.subpopulaionDistanceMap = createArrayForSubpopulationMap(this.modesMap.size(), distanceClassesLable.size());
        this.subpopulaionMSPFMap = createArrayForSubpopulationMap(this.modesMap.size(), this.variablesMSMap.size());
        this.subpopulaionMSPKMMap = createArrayForSubpopulationMap(this.modesMap.size(), this.variablesMSMap.size());
        this.subpopulationChangeMap = createArrayForSubpopulationMap(changeOrderList.size(), changeLableList.size());
        this.timeMap = createTimeStepsForSubpopulaitonMap((int) (this.config.qsim().getEndTime().seconds() / timeSplit), this.variablesTimeStepsMap.size());
        this.travelTimeMap = createTimeStepsForSubpopulaitonMap((lastTravelTimeValue / travelTimeSplit) + 2, this.variablesTimeStepsMap.size());

        // analyzing
        startAnalyze(experiencedPlans);

        // writing the different files
        writeStopStationAnalysis();
        writeTrainStationAnalysis();
        writeDistanceClassesAnalysis();
        writeModalSplit();
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
            // analysis public transport changes
            analyzeChanges(entry);
            // analyze travel time and middle time between to activities
            analyzeTimes(entry);

        }
    }

    private void analyzeTrainsStations(Entry<Id<Person>, Plan> entry) {
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(SBBModes.PT)) {
                List<Leg> legs = trip.getLegsOnly();
                Leg legBefore = pf.createLeg(SBBModes.WALK_FOR_ANALYSIS);
                for (Leg leg : legs) {
                    if (leg.getMode().equals(SBBModes.PT)) {
                        Route route = leg.getRoute();
                        TransitStopFacility startTrainStationFacility = getStartTrainFacility(route);
                        String startTrainStationId = startTrainStationFacility.getAttributes().getAttribute("02_Stop_No").toString();
                        TransitStopFacility endTrainStationFacility = getEndTrainFacility(route);
                        String endTrainStationId = endTrainStationFacility.getAttributes().getAttribute("02_Stop_No").toString();
                        String subMode = getModeOfTransitRoute(leg.getRoute());
                        Leg legAfter = getLegAfter(legs, legs.indexOf(leg));
                        if (railTripsAnalyzer.hasFQRelevantLeg(List.of((TransitPassengerRoute) leg.getRoute()))) {
                            if (legBefore.getMode().equals(SBBModes.PT) && subMode.equals(PTSubModes.RAIL)) {
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
                            if (legAfter.getMode().equals(SBBModes.PT) && subMode.equals(PTSubModes.RAIL)) {
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
            if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(SBBModes.PT)) {
                List<Leg> legs = trip.getLegsOnly();
                Leg legBefore = null;
                for (Leg leg : legs) {
                    if (leg.getMode().equals(SBBModes.PT)) {
                        Route route = leg.getRoute();
                        TransitStopFacility startStopStationFacility = getStartTrainFacility(route);
                        TransitStopFacility endStopStationFacility = getEndTrainFacility(route);

                        StopStation startStopStation = stopStationsMap.get(startStopStationFacility.getId());
                        startStopStation.addEntred();
                        if (legBefore != null) {
                            startStopStation.getEnteredMode()[StopStation.getModes().indexOf(legBefore.getMode())] =
                                startStopStation.getEnteredMode()[StopStation.getModes().indexOf(legBefore.getMode())] + 1;
                            if (legBefore.getMode().equals(SBBModes.PT)) {
                                String subPTMode = getModeOfTransitRoute(legBefore.getRoute());
                                startStopStation.getEnteredMode()[StopStation.getModes().indexOf(subPTMode)] = startStopStation.getEnteredMode()[StopStation.getModes().indexOf(subPTMode)] + 1;
                                if (subPTMode.equals(PTSubModes.RAIL)) {
                                    if (getEndTrainFacility(legBefore.getRoute()).equals(startStopStationFacility)) {
                                        startStopStation.addUmstiegeBahnBahn();
                                    } else {
                                        startStopStation.addUmsteigeAHPBahn();
                                    }
                                }
                            }
                        } else {
                            startStopStation.getEnteredMode()[StopStation.getModes().indexOf("walk")] = startStopStation.getEnteredMode()[StopStation.getModes().indexOf("walk")] + 1;
                        }

                        StopStation endStopStation = stopStationsMap.get(endStopStationFacility.getId());
                        endStopStation.addExited();
                        int currentLegIndex = legs.indexOf(leg);
                        Leg legAfter = getLegAfter(legs, currentLegIndex);
                        endStopStation.getExitedMode()[StopStation.getModes().indexOf(legAfter.getMode())] =
                            endStopStation.getExitedMode()[StopStation.getModes().indexOf(legAfter.getMode())] + 1;
                        if (legAfter.getMode().equals(SBBModes.PT)) {
                            String subPTMode = getModeOfTransitRoute(legAfter.getRoute());
                            endStopStation.getExitedMode()[StopStation.getModes().indexOf(subPTMode)] = endStopStation.getExitedMode()[StopStation.getModes().indexOf(subPTMode)] + 1;
                            if (subPTMode.equals(PTSubModes.RAIL)) {
                                if (getStartTrainFacility(legAfter.getRoute()).equals(endStopStationFacility)) {
                                    endStopStation.addUmstiegeBahnBahn();
                                } else {
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
            int middle = (int) ((trip.getOriginActivity().getEndTime().seconds() + trip.getDestinationActivity().getStartTime().seconds()) / 2);
            int time = (middle - (middle % timeSplit)) / timeSplit;
            int[][] subpopulationArrray = timeMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            subpopulationArrray[time][variablesTimeStepsMap.get(all)] = subpopulationArrray[time][variablesTimeStepsMap.get(all)] + 1;
            int travelTime = (int) ((trip.getDestinationActivity().getStartTime().seconds() - trip.getOriginActivity().getEndTime().seconds()));
            int timeArray = (travelTime - (travelTime % travelTimeSplit)) / travelTimeSplit;
            int[][] subpopulationTravelTime = travelTimeMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            if (timeArray >= subpopulationTravelTime.length) {
                timeArray = subpopulationTravelTime.length - 1;
            }
            subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(all)] = subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(all)] + 1;
            for (String mode : modesMS) {
                if ((MSVariables.mode + separator + tmpMode).equals(mode)) {
                    subpopulationArrray[time][variablesTimeStepsMap.get(mode)] = subpopulationArrray[time][variablesTimeStepsMap.get(mode)] + 1;
                    subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(mode)] = subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(mode)] + 1;
                    break;
                }
            }
        }
    }

    private void analyzeChanges(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        List<String> modes = new ArrayList<>();
        boolean feeder = false;
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            for (Leg leg : trip.getLegsOnly()) {
                if (leg.getMode().equals(SBBModes.PT)) {
                    modes.add(getModeOfTransitRoute(leg.getRoute()));
                } else if (SBBModes.TRAIN_FEEDER_MODES.contains(leg.getMode())){
                    feeder = true;
                }
            }
        }
        if (modes.size() == 0) {
            if (feeder) {
                subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION))[changeOrderList.indexOf("opnv")][0]++;
                subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION))[changeOrderList.indexOf("total")][0]++;
            }
            return;
        } else if (modes.size() < 2) {
            if (modes.get(0).equals(PTSubModes.RAIL)) {
                subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION))[changeOrderList.indexOf("train")][0]++;
                subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION))[changeOrderList.indexOf("total")][0]++;
            } else {
                subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION))[changeOrderList.indexOf("opnv")][0]++;
                subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION))[changeOrderList.indexOf("total")][0]++;
            }
        } else {
            int train = -1;
            int opnv = -1;
            int oev = -1;
            int total = -1;
            Iterator<String> iterator = modes.iterator();
            String firstMode = iterator.next();
            while (iterator.hasNext()) {
                String secondMode = iterator.next();
                if (firstMode.equals(PTSubModes.RAIL) && secondMode.equals(PTSubModes.RAIL)) {
                    train++;
                    total++;
                } else if (!firstMode.equals(PTSubModes.RAIL) && !secondMode.equals(PTSubModes.RAIL)) {
                    opnv++;
                    total++;
                } else {
                    oev++;
                    total++;
                }
                firstMode = secondMode;
            }
            double[][] array = subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            if (train > 5) {
                array[changeOrderList.indexOf("train")][5]++;
            } else if (train > -1) {
                array[changeOrderList.indexOf("train")][train]++;
            }
            if (opnv > 5) {
                array[changeOrderList.indexOf("opnv")][5]++;
            } else if (opnv > -1) {
                array[changeOrderList.indexOf("opnv")][opnv+1]++;
            }
            if (oev > 5) {
                array[changeOrderList.indexOf("oev")][5]++;
            } else if (oev > -1) {
                array[changeOrderList.indexOf("oev")][oev+1]++;
            }
            if (total > 5) {
                array[changeOrderList.indexOf("total")][5]++;
            } else if (total > 0) {
                array[changeOrderList.indexOf("total")][total]++;
            }
        }
    }

    private void analyzeModalSplit(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            if (trip.getOriginActivity().getFacilityId() == null || trip.getDestinationActivity().getFacilityId() == null) {
                continue;
            }

            String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (tmpMode.equals(SBBModes.WALK_MAIN_MAINMODE)) {
                tmpMode = SBBModes.WALK_FOR_ANALYSIS;
            }
            int modeId = modesMap.get(tmpMode);
            double distance = 0;
            for (Leg leg : trip.getLegsOnly()) {
                distance += leg.getRoute().getDistance() / 1000;
            }

            double[][] pfArray = subpopulaionMSPFMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            pfArray[modeId][variablesMSMap.get(all)] = pfArray[modeId][variablesMSMap.get(all)] + 1;

            double[][] pkmArray = subpopulaionMSPKMMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            pkmArray[modeId][variablesMSMap.get(all)] = pkmArray[modeId][variablesMSMap.get(all)] + distance;

            // car available
            for (String carAva : carAvailable) {
                if (attributes.getAttribute(Variables.CAR_AVAIL) != null && (Variables.CAR_AVAIL + "_" + attributes.getAttribute(Variables.CAR_AVAIL).toString()).equals(carAva)) {
                    pfArray[modeId][variablesMSMap.get(carAva)] = pfArray[modeId][variablesMSMap.get(carAva)] + 1;
                    pkmArray[modeId][variablesMSMap.get(carAva)] = pkmArray[modeId][variablesMSMap.get(carAva)] + distance;
                    break;
                }
            }
            // pt subscription
            for (String ptSub : ptSubscription) {
                if (attributes.getAttribute(Variables.PT_SUBSCRIPTION) != null && (Variables.PT_SUBSCRIPTION + "_" + attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString()).equals(ptSub)) {
                    pfArray[modeId][variablesMSMap.get(ptSub)] = pfArray[modeId][variablesMSMap.get(ptSub)] + 1;
                    pkmArray[modeId][variablesMSMap.get(ptSub)] = pkmArray[modeId][variablesMSMap.get(ptSub)] + distance;
                    break;
                }
            }
            // combination car available and pt subscription
            for (String carPT : carAndPt) {
                if (attributes.getAttribute(Variables.CAR_AVAIL) != null && attributes.getAttribute(Variables.PT_SUBSCRIPTION) != null && (Variables.CAR_AVAIL + "_" + attributes.getAttribute(Variables.CAR_AVAIL).toString() + "_" + Variables.PT_SUBSCRIPTION + "_" + attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString()).equals(carPT)) {
                    pfArray[modeId][variablesMSMap.get(carPT)] = pfArray[modeId][variablesMSMap.get(carPT)] + 1;
                    pkmArray[modeId][variablesMSMap.get(carPT)] = pkmArray[modeId][variablesMSMap.get(carPT)] + distance;
                    break;
                }
            }
            // kind of education
            for (String edu : educationType) {
                if (attributes.getAttribute(Variables.CURRENT_EDUCATION) != null && (Variables.CURRENT_EDUCATION + "_" + attributes.getAttribute(Variables.CURRENT_EDUCATION).toString()).equals(edu)) {
                    pfArray[modeId][variablesMSMap.get(edu)] = pfArray[modeId][variablesMSMap.get(edu)] + 1;
                    pkmArray[modeId][variablesMSMap.get(edu)] = pkmArray[modeId][variablesMSMap.get(edu)] + distance;
                    break;
                }
            }
            // employment rate
            for (String empRate : employmentRate) {
                if (attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT) != null && (Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT).toString()).equals(empRate)) {
                    pfArray[modeId][variablesMSMap.get(empRate)] = pfArray[modeId][variablesMSMap.get(empRate)] + 1;
                    pkmArray[modeId][variablesMSMap.get(empRate)] = pkmArray[modeId][variablesMSMap.get(empRate)] + distance;
                    break;
                }
            }
            // age categorie
            for (String age : ageCategorie) {
                if (attributes.getAttribute(Variables.AGE_CATEGORIE) != null && (Variables.AGE_CATEGORIE + "_" + attributes.getAttribute(Variables.AGE_CATEGORIE).toString()).equals(age)) {
                    pfArray[modeId][variablesMSMap.get(age)] = pfArray[modeId][variablesMSMap.get(age)] + 1;
                    pkmArray[modeId][variablesMSMap.get(age)] = pkmArray[modeId][variablesMSMap.get(age)] + distance;
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
                    pfArray[modeId][variablesMSMap.get(act)] = pfArray[modeId][variablesMSMap.get(act)] + 1;
                    pkmArray[modeId][variablesMSMap.get(act)] = pkmArray[modeId][variablesMSMap.get(act)] + distance;
                    break;
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
            double[][] disArray = this.subpopulaionDistanceMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            for (int disClass : distanceClassesValue) {
                if (distance <= disClass) {
                    disArray[modeID][distanceClassesValue.indexOf(disClass)] = disArray[modeID][distanceClassesValue.indexOf(disClass)] + 1;
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
        String time = "time";
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
                for (int i = 0; i < (lastTravelTimeValue / travelTimeSplit) + 2; i++) {
                    csvWriter.set(runID, config.controler().getRunId());
                    csvWriter.set(subpopulation, entry.getKey());
                    csvWriter.set(time, Integer.toString(i * travelTimeSplit));
                    if (i == (lastTravelTimeValue / travelTimeSplit) + 1) {
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

    private void writeTrainStationAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        final String stopNumber = "Stop_Nummer";
        final String zone = "Zone";
        final String zielAussteiger = "Ziel_Aussteiger";
        final String quellEinsteiger = "Quell_Einsteiger";
        final String umsteigerTyp5a = "Umsteiger_Typ_5a";
        final String umsteigerTyp5b = "Umsteiger_Typ_5b";
        final String umsteigerSimbaSimba = "Umsteiger_Simba_Simba";
        final String umsteigerSimbaAndere = "Umsteiger_Simba_Andere";
        final String umsteigerAndereSimba = "Umsteiger_Andere_Simba";
        final String umsteigerAndereAndere = "Umsteiger_Andere_Andere";
        String head = runID + "," + stopNumber + "," + zone + "," + zielAussteiger + "," + quellEinsteiger + "," + umsteigerTyp5a + "," + umsteigerTyp5b + "," +
            umsteigerSimbaSimba + "," + umsteigerSimbaAndere + "," + umsteigerAndereSimba + "," + umsteigerAndereAndere;
        String[] columns = head.split(",");
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + oNTrainStrationsCount)) {
            for (TrainStation station : trainStationMap.values()) {
                csvWriter.set(runID, config.controler().getRunId());
                csvWriter.set(stopNumber, station.getStation());
                csvWriter.set(zone, station.getZoneId());
                csvWriter.set(zielAussteiger, Integer.toString((int) (station.getZielAussteiger()/sampleSize)));
                csvWriter.set(quellEinsteiger, Integer.toString((int) (station.getQuellEinsteiger()/sampleSize)));
                csvWriter.set(umsteigerTyp5a, Integer.toString((int) (station.getUmsteigerTyp5a()/sampleSize)));
                csvWriter.set(umsteigerTyp5b, Integer.toString((int) (station.getUmsteigerTyp5b()/sampleSize)));
                csvWriter.set(umsteigerSimbaSimba, Integer.toString((int) (station.getUmsteigerSimbaSimba()/sampleSize)));
                csvWriter.set(umsteigerSimbaAndere, Integer.toString((int) (station.getUmsteigerSimbaAndere()/sampleSize)));
                csvWriter.set(umsteigerAndereSimba, Integer.toString((int) (station.getUmsteigerAndereSimba()/sampleSize)));
                csvWriter.set(umsteigerAndereAndere, Integer.toString((int) (station.getUmsteigerAndereAndere()/sampleSize)));
                csvWriter.writeRow();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void writeStopStationAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        final String codeID = "HST_Nummer";
        final String code = "Code";
        final String stopNumber = "Stop_Nummer";
        final String trainStationName = "Name";
        final String x = "X";
        final String y = "Y";
        final String zone = "Zone";
        final String einstiege = "Einstiege_Gesamt";
        final String ausstiege = "Ausstiege_Gesamt";
        final String umstiege = "Umstiege_Bahn_Bahn";
        final String zustiege = "Umsteige_AHP_Bahn";
        final String wegstiege = "Umsteige_Bahn_AHP";
        StringBuilder head = new StringBuilder(
            runID + "," + codeID + "," + stopNumber + "," + code + "," + trainStationName + "," + x + "," + y + "," + zone + "," + einstiege + "," + ausstiege + "," + umstiege + "," + zustiege
                + ","
                + wegstiege);
        for (String mode : StopStation.getModes()) {
            head.append(",").append("Zielaustieg_").append(mode);
            head.append(",").append("Quelleinstieg_").append(mode);
        }
        String[] columns = head.toString().split(",");
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + oNStopStationsCount)) {
            for (Entry<Id<TransitStopFacility>, StopStation> entry : stopStationsMap.entrySet()) {
                csvWriter.set(runID, config.controler().getRunId());
                csvWriter.set(codeID, entry.getValue().getStop().getId().toString());
                csvWriter.set(stopNumber, entry.getValue().getStop().getAttributes().getAttribute("02_Stop_No").toString());
                Id<TransitStopFacility> stopId = Id.create(entry.getKey(), TransitStopFacility.class);
                Object codeAttribute = entry.getValue().getStop().getAttributes().getAttribute("03_Stop_Code");
                if (codeAttribute == null) {
                    csvWriter.set(code, "NA");
                } else {
                    csvWriter.set(code, codeAttribute.toString());
                }
                String name = transitSchedule.getFacilities().get(stopId).getName();
                csvWriter.set(trainStationName, name);
                csvWriter.set(x, Double.toString(entry.getValue().getStop().getCoord().getX()));
                csvWriter.set(y, Double.toString(entry.getValue().getStop().getCoord().getY()));
                csvWriter.set(zone, entry.getValue().getZoneId());
                csvWriter.set(einstiege, Integer.toString((int) (entry.getValue().getEntered() / sampleSize)));
                csvWriter.set(ausstiege, Integer.toString((int) (entry.getValue().getExited() / sampleSize)));
                for (String mode : StopStation.getModes()) {
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
        String[] columns = {"RunID", "Subpopulation", "Umsteigetyp", "0", "1", "2", "3", "4", ">=5"};
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        try (CSVWriter csvWriter = new CSVWriter("", columns, outputLocation + oNChangesCount)) {
            for (Entry<String, double[][]> entry : subpopulationChangeMap.entrySet()) {
                Map<String, Integer> mapChange = new HashMap<>();
                mapChange.put("changesTrain", 0);
                mapChange.put("changesOPNV", 1);
                mapChange.put("changesOEV", 2);
                mapChange.put("changesTotal", 3);
                for (Entry<String, Integer> change : mapChange.entrySet()) {
                    csvWriter.set("RunID", config.controler().getRunId());
                    csvWriter.set("Subpopulation", entry.getKey());
                    csvWriter.set("Umsteigetyp", change.getKey());
                    for (int i = 0; i < 6; i++) {
                        csvWriter.set(changeLableList.get(i), Integer.toString((int) (entry.getValue()[change.getValue()][i] / sampleSize)));
                    }
                    csvWriter.writeRow();
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
                TrainStation trainStation = new TrainStation(id, zones.findZone(transitStopFacility.getCoord()));
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
