package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.intermodal.analysis.SBBTransferAnalysisListener;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;

public class ModalSplitStats {

    private final Zones zones;
    @Inject private ExperiencedPlansService experiencedPlansService;
    @Inject private Population population;
    @Inject private Config config;
    @Inject private TransitSchedule transitSchedule;

    //usesd person attrubutes

    private final String carAvailable1 = Variables.CAR_AVAIL + "_1";
    private final String carAvailable0 = Variables.CAR_AVAIL + "_0";

    private final String all = "all";
    private final String ptSubNone = Variables.PT_SUBSCRIPTION + "_" + Variables.PT_SUBSCRIPTION_NONE;
    private final String ptSubGA = Variables.PT_SUBSCRIPTION + "_" + Variables.GA;
    private final String ptSubVA = Variables.PT_SUBSCRIPTION + "_" + Variables.VA;
    private final String ptSubHTA = Variables.PT_SUBSCRIPTION + "_" + Variables.HTA;

    private final String carNone = "car_no_pt_sub";
    private final String carGa = "car_GA_sub";
    private final String carHTA = "car_HTA_sub";
    private final String carVA = "car_VA_sub";
    private final String nocarNone = "no_car_no_pt_sub";
    private final String nocarGa = "no_car_GA_sub";
    private final String nocarHTA = "no_car_HTA_sub";
    private final String nocarVA = "no_car_VA_sub";

    private final String notInEducation = Variables.CURRENT_EDUCATION + "_" + Variables.NOT_IN_EDUCATION;
    private final String primary = Variables.CURRENT_EDUCATION + "_" + Variables.PRIMRAY;
    private final String secondary = Variables.CURRENT_EDUCATION + "_" + Variables.SECONDARY;
    private final String student = Variables.CURRENT_EDUCATION + "_" + Variables.STUDENT;

    private final String employment0 = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + Variables.LEVEL_OF_EMPLOYMENT_CAT_NONE;
    private final String employment39 = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + Variables.LEVEL_OF_EMPLOYMENT_CAT_01_to_39;
    private final String employment79 = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + Variables.LEVEL_OF_EMPLOYMENT_CAT_40_to_79;
    private final String employment100 = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + Variables.LEVEL_OF_EMPLOYMENT_CAT_80_to_100;

    private final String ageCat17 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_0_17;
    private final String ageCat24 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_18_24;
    private final String ageCat44 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_25_44;
    private final String ageCat64 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_45_64;
    private final String ageCat74 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_65_74;
    private final String ageCatXX = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_75_XX;

    private final String home = "to_act_type_" + SBBActivities.home;
    private final String cbhome = "to_act_type_" + SBBActivities.cbHome;
    private final String leisure = "to_act_type_" + SBBActivities.leisure;
    private final String other = "to_act_type_" + SBBActivities.other;
    private final String freight = "to_act_type_" + SBBActivities.freight;
    private final String business = "to_act_type_" + SBBActivities.business;
    private final String shopping = "to_act_type_" + SBBActivities.shopping;
    private final String work = "to_act_type_" + SBBActivities.work;
    private final String education = "to_act_type_" + SBBActivities.education;
    private final String exogeneous = "to_act_type_" + SBBActivities.exogeneous;
    private final String accompany = "to_act_type_" + SBBActivities.accompany;
    private final String mode = "mode";

    private final String walk = mode + "_" + SBBModes.WALK_FOR_ANALYSIS;
    private final String ride = mode + "_" + SBBModes.RIDE;
    private final String car = mode + "_" + SBBModes.CAR;
    private final String pt = mode + "_" + SBBModes.PT;
    private final String bike = mode + "_" + SBBModes.BIKE;
    private final String avtaxi = mode + "_" + SBBModes.AVTAXI;
    private final String drt = mode + "_" + SBBModes.DRT;

    private final int timeSplit = 30 * 60;
    private final int travelTimeSplit = 10 * 60;
    private final int lastTravelTimeValue = 5 * 60 * 60;
    private Map<Id<TransitStopFacility>, TrainStation> trainStationsMap = new HashMap<>();
    private Map<String, Integer> modesMap = new HashMap<>();
    private Map<String, Integer> variablesMSMap = new HashMap<>();
    private Map<String, int[][]> subpopulaionDistanceMap = new HashMap<>();
    private List<Integer> distanceClassesValue = List.of(0, 2, 4, 6, 8, 10, 15, 20, 25, 30, 40, 50, 100, 150, 200, 300);
    private List<String> distanceClassesLable = List.of("0", "0-2", "2-4", "4-6", "6-8", "8-10", "10-15", "15-20", "20-25", "25-30", "30-40", "40-50", "50-100", "100-150", "150-200", "200-300");
    ;
    private List<String> carAvailable = List.of(carAvailable1, carAvailable0);
    private Map<String, int[][]> subpopulaionMSPFMap = new HashMap<>();

    // Variables
    private final String runID = "RunID";
    private final String subpopulation = "subpopulation";

    private Map<Id<TransitStopFacility>, TrainStation> generateTrainStationMap() {
        assert transitSchedule != null;
        Map<Id<TransitStopFacility>, TrainStation> trainStationsMap = new HashMap<>();
        for (TransitStopFacility transitStopFacility : transitSchedule.getFacilities().values()) {
            trainStationsMap.put(transitStopFacility.getId(), new TrainStation(transitStopFacility, zones.findZone(transitStopFacility.getCoord())));
        }
        return trainStationsMap;
    }

    private Map<String, Integer> createVariablesModalSplitMap() {
        Map<String, Integer> variables = new HashMap<>();
        variables.put(mode, 0);
        variables.put(all, 1);
        variables.put(carAvailable1, 2);
        variables.put(carAvailable0, 3);
        /*
        variables.put(ptSubNone, 4);
        variables.put(ptSubGA, 5);
        variables.put(ptSubVA, 6);
        variables.put(ptSubHTA, 7);
        variables.put(carNone, 8);
        variables.put(carGa, 9);
        variables.put(carHTA, 10);
        variables.put(carVA, 11);
        variables.put(nocarNone, 12);
        variables.put(nocarGa, 13);
        variables.put(nocarHTA, 14);
        variables.put(nocarVA, 15);
        variables.put(notInEducation, 16);
        variables.put(primary, 17);
        variables.put(secondary, 18);
        variables.put(student, 19);
        variables.put(employment0, 20);
        variables.put(employment39, 21);
        variables.put(employment79, 22);
        variables.put(employment100, 23);
        variables.put(ageCat17, 24);
        variables.put(ageCat24, 25);
        variables.put(ageCat44, 26);
        variables.put(ageCat64, 27);
        variables.put(ageCat74, 28);
        variables.put(ageCatXX, 29);
        variables.put(cbhome, 30);
        variables.put(other, 31);
        variables.put(freight, 32);
        variables.put(business, 33);
        variables.put(shopping, 34);
        variables.put(work, 35);
        variables.put(education, 36);
        variables.put(leisure, 37);
        variables.put(home, 38);
        variables.put(exogeneous, 39);
        variables.put(accompany, 40);*/
        return variables;
    }

    private Map<String, Integer> createVariablesTimeStepsMap() {
        Map<String, Integer> variables = new HashMap<>();
        variables.put(all, 0);
        variables.put(walk, 1);
        variables.put(bike, 2);
        variables.put(car, 3);
        variables.put(pt, 4);
        variables.put(ride, 5);
        variables.put(drt, 6);
        variables.put(avtaxi, 7);
        variables.put(cbhome, 8);
        variables.put(other, 9);
        variables.put(freight, 10);
        variables.put(business, 11);
        variables.put(shopping, 12);
        variables.put(work, 13);
        variables.put(education, 14);
        variables.put(leisure, 15);
        variables.put(home, 16);
        variables.put(exogeneous, 17);
        variables.put(accompany, 18);
        return variables;
    }

    private String filename;

    private String outputLocation;

    @Inject
    public ModalSplitStats(ZonesCollection zonesCollection, final PostProcessingConfigGroup ppConfig) {
        zones = zonesCollection.getZones(ppConfig.getZonesId());
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

    public void analyzeAndWriteStats(String outputLocation) {

        // prepare necessary information
        this.outputLocation = outputLocation;
        this.trainStationsMap = generateTrainStationMap();
        this.modesMap = getModesMap();
        this.variablesMSMap = createVariablesModalSplitMap();
        this.subpopulaionDistanceMap = createArrayForSubpopulationMap(this.modesMap.size(), distanceClassesLable.size());
        this.subpopulaionMSPFMap = createArrayForSubpopulationMap(this.modesMap.size(), distanceClassesLable.size());

        // analyzing
        startAnalyze();

        // writing the different files
        writeStationAnalysis();
        writeDistanceClassesAnalysis();
        writeModalSplit();

    }

    private void writeModalSplit() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] colums = new String[4 + this.carAvailable.size()];
        colums[0] = this.runID;
        colums[1] = this.subpopulation;
        colums[2] = this.mode;
        colums[3] = this.all;
        for (int i = 0; i < this.carAvailable.size(); i++) {
            colums[i + 4] = this.carAvailable.get(i);
        }
        try (CSVWriter csvWriterPF = new CSVWriter("", colums, outputLocation + "modal_split_pf.csv")) {
            for (String subpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> modeEntry : modesMap.entrySet()) {
                    csvWriterPF.set(this.runID, config.controler().getRunId());
                    csvWriterPF.set(this.subpopulation, subpopulation);
                    for (Entry<String, Integer> entry : this.variablesMSMap.entrySet()) {
                        if (entry.getKey().equals(mode)) {
                            csvWriterPF.set(mode, modeEntry.getKey());
                        } else {
                            String key = entry.getKey();
                            Integer value = entry.getValue();
                            csvWriterPF.set(key, Integer.toString((int) (this.subpopulaionMSPFMap.get(subpopulation)[modeEntry.getValue()][value] / sampleSize)));
                        }
                    }
                    csvWriterPF.writeRow();
                }
            }
        } catch (
            IOException e) {
            e.printStackTrace();
        }
    }

    private void writeDistanceClassesAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(this.config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = new String[3 + this.distanceClassesValue.size()];
        columns[0] = this.runID;
        columns[1] = this.subpopulation;
        columns[2] = this.mode;
        for (int i = 0; i < this.distanceClassesValue.size(); i++) {
            columns[i + 3] = this.distanceClassesLable.get(i);
        }
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + "distace_classes.csv")) {
            for (String subpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> col : this.modesMap.entrySet()) {
                    csvWriter.set(this.runID, this.config.controler().getRunId());
                    csvWriter.set(this.subpopulation, subpopulation);
                    csvWriter.set(this.mode, col.getKey());
                    for (int i = 0; i < this.distanceClassesValue.size(); i++) {
                        csvWriter.set(this.distanceClassesLable.get(i), Integer.toString((int) (this.subpopulaionDistanceMap.get(subpopulation)[col.getValue()][i] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startAnalyze() {
        for (Entry<Id<Person>, Plan> entry : experiencedPlansService.getExperiencedPlans().entrySet()) {

            //creates analysis for access and egress mode for each train station
            analyzeTrainStations(entry);
            //creates analysis for distance classes
            analyzeDistanceClasses(entry);
            //create analysis for modal split for persons trips
            analyzeModalSplitPF(entry);

        }
    }

    private void analyzeModalSplitPF(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
            String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (tmpMode.equals("walk_main")) {
                tmpMode = "walk";
            }
            int modeId = modesMap.get(tmpMode);
            double distance = 0;
            for (Leg leg : trip.getLegsOnly()) {
                distance += leg.getRoute().getDistance() / 1000;
            }
            int[][] pfArray = subpopulaionMSPFMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            pfArray[modeId][variablesMSMap.get(all)] = pfArray[modeId][variablesMSMap.get(all)] + 1;

            for (String carAva : carAvailable) {
                String attribut = attributes.getAttribute(Variables.CAR_AVAIL).toString();
                if ((Variables.CAR_AVAIL + "_" + attribut).equals(carAva)) {
                    pfArray[modeId][variablesMSMap.get(carAva)] = pfArray[modeId][variablesMSMap.get(carAva)] + 1;
                    break;
                }
            }

        }
    }

    private Map<String, int[][]> createArrayForSubpopulationMap(int modeSize, int varSize) {
        Map<String, int[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new int[modeSize][varSize]);
        }
        return subpopulaionMap;
    }

    private void analyzeDistanceClasses(Entry<Id<Person>, Plan> entry) {
        Attributes attributes = this.population.getPersons().get(entry.getKey()).getAttributes();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
            String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
            if (tmpMode.equals("walk_main")) {
                tmpMode = "walk";
            }
            int modeID = this.modesMap.get(tmpMode);
            double distance = 0;
            for (Leg leg : trip.getLegsOnly()) {
                distance += leg.getRoute().getDistance() / 1000;
            }
            int[][] disArray = this.subpopulaionDistanceMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            for (int disClass : this.distanceClassesValue) {
                if (distance <= disClass) {
                    disArray[modeID][distanceClassesValue.indexOf(disClass)] = disArray[modeID][distanceClassesValue.indexOf(disClass)] + 1;
                    break;
                }
            }
        }
    }

    private void writeStationAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        final String code = "Code";
        final String stopID = "StopID";
        final String trainStationName = "Name";
        final String x = "X";
        final String y = "Y";
        final String zone = "Zone";
        final String einAussteige = "Ein_Ausstiege";
        final String einstiege = "Einstiege";
        final String ausstiege = "Ausstiege";
        final String umstiege = "Umstiege";
        final String zustiege = "Zustiege";
        final String wegstiege = "Wegstiege";
        StringBuilder head = new StringBuilder(
            runID + "," + stopID + "," + code + "," + trainStationName + "," + x + "," + y + "," + zone + "," + einAussteige + "," + einstiege + "," + ausstiege + "," + umstiege + "," + zustiege + ","
                + wegstiege);
        for (String mode : TrainStation.getModes()) {
            head.append(",").append("Einstiege_").append(mode);
            head.append(",").append("Ausstiege_").append(mode);
        }
        String[] columns = head.toString().split(",");
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + "train_staions_count.csv")) {
            for (Entry<Id<TransitStopFacility>, TrainStation> entry : trainStationsMap.entrySet()) {
                csvWriter.set(runID, config.controler().getRunId());
                csvWriter.set(stopID, entry.getValue().getStation().getAttributes().getAttribute("02_Stop_No").toString());
                Id<TransitStopFacility> stopId = Id.create(entry.getKey(), TransitStopFacility.class);
                Object codeAttribute = entry.getValue().getStation().getAttributes().getAttribute("03_Stop_" + code);
                if (codeAttribute == null) {
                    csvWriter.set(code, "NA");
                } else {
                    csvWriter.set(code, codeAttribute.toString());
                }
                String name = transitSchedule.getFacilities().get(stopId).getName();
                csvWriter.set(trainStationName, name);
                csvWriter.set(x, Double.toString(entry.getValue().getStation().getCoord().getX()));
                csvWriter.set(y, Double.toString(entry.getValue().getStation().getCoord().getY()));
                csvWriter.set(zone, entry.getValue().getZoneId());
                csvWriter.set(einAussteige, Integer.toString((int) (entry.getValue().getEnteredAndExited() / sampleSize)));
                csvWriter.set(einstiege, Integer.toString((int) (entry.getValue().getEntered() / sampleSize)));
                csvWriter.set(ausstiege, Integer.toString((int) (entry.getValue().getExited() / sampleSize)));
                for (String mode : TrainStation.getModes()) {
                    csvWriter.set("Einstiege_" + mode, Integer.toString((int) (entry.getValue().getEnteredMode()[TrainStation.getModes().indexOf(mode)] / sampleSize)));
                    csvWriter.set("Ausstiege_" + mode, Integer.toString((int) (entry.getValue().getExitedMode()[TrainStation.getModes().indexOf(mode)] / sampleSize)));
                }
                csvWriter.set(umstiege, Integer.toString((int) (entry.getValue().getUmsteige() / sampleSize)));
                csvWriter.set(zustiege, Integer.toString((int) (entry.getValue().getZustiege() / sampleSize)));
                csvWriter.set(wegstiege, Integer.toString((int) (entry.getValue().getWegstiege() / sampleSize)));
                csvWriter.writeRow();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void analyzeTrainStations(Entry<Id<Person>, Plan> entry) {
        SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals("pt")) {
                List<Leg> legs = trip.getLegsOnly();
                Leg legBefore = null;
                for (Leg leg : legs) {
                    if (leg.getMode().equals("pt")) {
                        Route route = leg.getRoute();
                        TransitStopFacility startTrainStationFacility = getStartTrainFacility(route);
                        TransitStopFacility endTrainStationFacility = getEndTrainFacility(route);
                        if (trainStationsMap.containsKey(startTrainStationFacility.getId())) {
                            TrainStation startTrainStation = trainStationsMap.get(startTrainStationFacility.getId());
                            startTrainStation.addEntred();
                            if (legBefore != null) {
                                startTrainStation.getEnteredMode()[TrainStation.getModes().indexOf(legBefore.getMode())] =
                                    startTrainStation.getEnteredMode()[TrainStation.getModes().indexOf(legBefore.getMode())] + 1;
                                if (legBefore.getMode().equals(SBBModes.PT)) {
                                    String subPTMode = getModeOfTransitRoute(legBefore.getRoute());
                                    startTrainStation.getEnteredMode()[TrainStation.getModes().indexOf(subPTMode)] = startTrainStation.getEnteredMode()[TrainStation.getModes().indexOf(subPTMode)] + 1;
                                    if (subPTMode.equals(PTSubModes.RAIL)) {
                                        if (getEndTrainFacility(legBefore.getRoute()).equals(startTrainStationFacility)) {
                                            startTrainStation.addUmstiege();
                                        } else {
                                            startTrainStation.addZustiege();
                                        }
                                    }
                                }
                            } else {
                                startTrainStation.getEnteredMode()[TrainStation.getModes().indexOf("walk")] = startTrainStation.getEnteredMode()[TrainStation.getModes().indexOf("walk")] + 1;
                            }
                        }
                        if (trainStationsMap.containsKey(endTrainStationFacility.getId())) {
                            TrainStation endTrainStation = trainStationsMap.get(endTrainStationFacility.getId());
                            endTrainStation.addExited();
                            int currentLegIndex = legs.indexOf(leg);
                            Leg legAfter = getLegAfter(legs, currentLegIndex);
                            endTrainStation.getExitedMode()[TrainStation.getModes().indexOf(legAfter.getMode())] =
                                endTrainStation.getExitedMode()[TrainStation.getModes().indexOf(legAfter.getMode())] + 1;
                            if (legAfter.getMode().equals(SBBModes.PT)) {
                                String subPTMode = getModeOfTransitRoute(legAfter.getRoute());
                                endTrainStation.getExitedMode()[TrainStation.getModes().indexOf(subPTMode)] = endTrainStation.getExitedMode()[TrainStation.getModes().indexOf(subPTMode)] + 1;
                                if (subPTMode.equals(PTSubModes.RAIL)) {
                                    if (getStartTrainFacility(legAfter.getRoute()).equals(endTrainStationFacility)) {
                                        endTrainStation.addUmstiege();
                                    } else {
                                        endTrainStation.addWegstiege();
                                    }
                                }
                            }
                        }
                    }
                    if (!leg.getMode().contains("walk")) {
                        legBefore = leg;
                    }
                }
            }
        }

    }

    private Leg getLegAfter(List<Leg> legs, int currentLegIndex) {
        Leg legAfter = legs.get(currentLegIndex + 1);
        for (int i = currentLegIndex + 2; i < legs.size(); i++) {
            if (!legs.get(i).getMode().contains("walk")) {
                return legs.get(i);
            }
        }
        return legAfter;
    }

    public void analyzeAndWriteStats11(String filename) {
        this.filename = filename;
        trainStationsMap = generateTrainStationMap();

        Map<String, Integer> modesMap = getModesMap();
        Map<String, Integer> variables = createVariablesModalSplitMap();
        Map<String, int[][]> pfMap = createPFForSubpopulationMap(modesMap.size(), variables.size());
        Map<String, double[][]> pkmMap = createPKMForSubpopulationMap(modesMap.size(), variables.size());
        analyzeModalSplit(modesMap, variables, pfMap, pkmMap);
        writeModalSplit1(filename, modesMap, variables, pfMap, pkmMap);

        Map<String, Integer> variablesTime = createVariablesTimeStepsMap();
        Map<String, int[][]> timeMap = createTimeStapsForSubpopulaitonMap((int) (config.qsim().getEndTime().seconds() / timeSplit), variablesTime.size());
        Map<String, int[][]> travelTimeMap = createTimeStapsForSubpopulaitonMap((lastTravelTimeValue / travelTimeSplit) + 2, variablesTime.size());
        analyzeTimeSteps(variablesTime, timeMap, travelTimeMap);
        writeTimeSteps(filename, variablesTime, timeMap, travelTimeMap);

        Map<String, int[][]> map = new HashMap<>();
        for (String subp : Variables.SUBPOPULATIONS) {
            map.put(subp, new int[3][6]);
        }
        analyzeChanges(map);
        writeChanges(map);
    }

    private void writeChanges(Map<String, int[][]> map) {
        String[] columns = {"RunID", "Subpopulation", "Type", "0", "1", "2", "3", "4", "5"};
        try (CSVWriter csvWriter = new CSVWriter("", columns, filename + "changes_count.csv")) {
            for (Entry<String, int[][]> entry : map.entrySet()) {
                Map<String, Integer> mapChange = new HashMap<>();
                mapChange.put("changesTrain", 0);
                mapChange.put("changesOPNV", 1);
                mapChange.put("changesOEV", 2);
                for (Entry<String, Integer> change : mapChange.entrySet()) {
                    csvWriter.set("RunID", config.controler().getRunId());
                    csvWriter.set("Subpopulation", entry.getKey());
                    csvWriter.set("Type", change.getKey());
                    for (int i = 0; i < 6; i++) {
                        csvWriter.set(Integer.toString(i), Integer.toString(entry.getValue()[change.getValue()][i]));
                    }
                    csvWriter.writeRow();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void analyzeChanges(Map<String, int[][]> map) {
        for (Entry<Id<Person>, Plan> entry : experiencedPlansService.getExperiencedPlans().entrySet()) {
            Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
            for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
                SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
                if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals("pt")) {
                    List<Leg> legs = trip.getLegsOnly();
                    int changesTrain = 0;
                    int changesOPNV = 0;
                    int changesOEV = 0;
                    boolean inTrain = false;
                    boolean inOPNV = false;
                    for (Leg leg : legs) {
                        if (leg.getMode().equals("pt")) {
                            Route route = leg.getRoute();
                            String desription = route.getRouteDescription();
                            int indexStart = desription.indexOf("accessFacilityId") + "accessFacilityId".length();
                            int indexEnd = desription.indexOf("egressFacilityId");
                            int start = Integer.parseInt(desription.substring(indexStart + 3, indexEnd - 3));
                            if (transitSchedule.getFacilities().get(Id.create(start, TransitStopFacility.class)) == null) {
                                if (inOPNV) {
                                    changesOPNV++;
                                } else if (inTrain) {
                                    changesOEV++;
                                }
                                inOPNV = true;
                                inTrain = false;
                            } else {
                                if (inOPNV) {
                                    changesOEV++;
                                } else if (inTrain) {
                                    changesTrain++;
                                }
                                inOPNV = false;
                                inTrain = true;
                            }
                        }
                    }
                    int[][] array = map.get(attributes.getAttribute(Variables.SUBPOPULATION));
                    array[0][changesTrain] = array[0][changesTrain] + 1;
                    array[1][changesOPNV] = array[1][changesOPNV] + 1;
                    array[2][changesOEV] = array[2][changesOEV] + 1;
                }
            }
        }
    }

    private void analyzeModalSplit(Map<String, Integer> modesMap, Map<String, Integer> variables, Map<String, int[][]> pfMap, Map<String, double[][]> pkmMap) {
        for (Entry<Id<Person>, Plan> entry : experiencedPlansService.getExperiencedPlans().entrySet()) {
            Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
            for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
                SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
                String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (tmpMode.equals("walk_main")) {
                    tmpMode = "walk";
                }
                int id = modesMap.get(tmpMode);
                double distance = 0;
                for (Leg leg : trip.getLegsOnly()) {
                    distance += leg.getRoute().getDistance() / 1000;
                }
                int[][] pfArray = pfMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
                double[][] pkmArray = pkmMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
                pfArray[id][variables.get(all)] = pfArray[id][variables.get(all)] + 1;
                pkmArray[id][variables.get(all)] = pkmArray[id][variables.get(all)] + distance;
                // car available
                switch (attributes.getAttribute(Variables.CAR_AVAIL).toString()) {
                    case Variables.CAR_AVAL_TRUE:
                        pfArray[id][variables.get(carAvailable1)] = pfArray[id][variables.get(carAvailable1)] + 1;
                        pkmArray[id][variables.get(carAvailable1)] = pkmArray[id][variables.get(carAvailable1)] + distance;
                        break;
                    case "0":
                        pfArray[id][variables.get(carAvailable0)] = pfArray[id][variables.get(carAvailable0)] + 1;
                        pkmArray[id][variables.get(carAvailable0)] = pkmArray[id][variables.get(carAvailable0)] + distance;
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + attributes.getAttribute(Variables.CAR_AVAIL) + " of: " + Variables.CAR_AVAIL);
                }
                // pt subscription
                switch (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString()) {
                    case Variables.GA:
                        pfArray[id][variables.get(ptSubGA)] = pfArray[id][variables.get(ptSubGA)] + 1;
                        pkmArray[id][variables.get(ptSubGA)] = pkmArray[id][variables.get(ptSubGA)] + distance;
                        break;
                    case Variables.VA:
                        pfArray[id][variables.get(ptSubVA)] = pfArray[id][variables.get(ptSubVA)] + 1;
                        pkmArray[id][variables.get(ptSubVA)] = pkmArray[id][variables.get(ptSubVA)] + distance;
                        break;
                    case Variables.HTA:
                        pfArray[id][variables.get(ptSubHTA)] = pfArray[id][variables.get(ptSubHTA)] + 1;
                        pkmArray[id][variables.get(ptSubHTA)] = pkmArray[id][variables.get(ptSubHTA)] + distance;
                        break;
                    case Variables.PT_SUBSCRIPTION_NONE:
                        pfArray[id][variables.get(ptSubNone)] = pfArray[id][variables.get(ptSubNone)] + 1;
                        pkmArray[id][variables.get(ptSubNone)] = pkmArray[id][variables.get(ptSubNone)] + distance;
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + attributes.getAttribute(Variables.PT_SUBSCRIPTION) + " of " + Variables.PT_SUBSCRIPTION);
                }
                // combination car and pt
                switch (attributes.getAttribute(Variables.CAR_AVAIL).toString()) {
                    case Variables.CAR_AVAL_TRUE:
                        switch (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString()) {
                            case Variables.GA:
                                pfArray[id][variables.get(carGa)] = pfArray[id][variables.get(carGa)] + 1;
                                pkmArray[id][variables.get(carGa)] = pkmArray[id][variables.get(carGa)] + distance;
                                break;
                            case Variables.VA:
                                pfArray[id][variables.get(carVA)] = pfArray[id][variables.get(carVA)] + 1;
                                pkmArray[id][variables.get(carVA)] = pkmArray[id][variables.get(carVA)] + distance;
                                break;
                            case Variables.HTA:
                                pfArray[id][variables.get(carHTA)] = pfArray[id][variables.get(carHTA)] + 1;
                                pkmArray[id][variables.get(carHTA)] = pkmArray[id][variables.get(carHTA)] + distance;
                                break;
                            case Variables.PT_SUBSCRIPTION_NONE:
                                pfArray[id][variables.get(carNone)] = pfArray[id][variables.get(carNone)] + 1;
                                pkmArray[id][variables.get(carNone)] = pkmArray[id][variables.get(carNone)] + distance;
                                break;
                            default:
                                throw new NoSuchElementException("Unknow value " + attributes.getAttribute(Variables.PT_SUBSCRIPTION) + " of " + Variables.PT_SUBSCRIPTION);
                        }
                        break;
                    case "0":
                        switch (attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString()) {
                            case Variables.GA:
                                pfArray[id][variables.get(nocarGa)] = pfArray[id][variables.get(nocarGa)] + 1;
                                pkmArray[id][variables.get(nocarGa)] = pkmArray[id][variables.get(nocarGa)] + distance;
                                break;
                            case Variables.VA:
                                pfArray[id][variables.get(nocarVA)] = pfArray[id][variables.get(nocarVA)] + 1;
                                pkmArray[id][variables.get(nocarVA)] = pkmArray[id][variables.get(nocarVA)] + distance;
                                break;
                            case Variables.HTA:
                                pfArray[id][variables.get(nocarHTA)] = pfArray[id][variables.get(nocarHTA)] + 1;
                                pkmArray[id][variables.get(nocarHTA)] = pkmArray[id][variables.get(nocarHTA)] + distance;
                                break;
                            case Variables.PT_SUBSCRIPTION_NONE:
                                pfArray[id][variables.get(nocarNone)] = pfArray[id][variables.get(nocarNone)] + 1;
                                pkmArray[id][variables.get(nocarNone)] = pkmArray[id][variables.get(nocarNone)] + distance;
                                break;
                            default:
                                throw new NoSuchElementException("Unknow value " + attributes.getAttribute(Variables.PT_SUBSCRIPTION) + " of " + Variables.PT_SUBSCRIPTION);
                        }
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + attributes.getAttribute(Variables.CAR_AVAIL) + " of: " + Variables.CAR_AVAIL);
                }
                // education
                switch (attributes.getAttribute(Variables.CURRENT_EDUCATION).toString()) {
                    case Variables.NOT_IN_EDUCATION:
                        pfArray[id][variables.get(notInEducation)] = pfArray[id][variables.get(notInEducation)] + 1;
                        pkmArray[id][variables.get(notInEducation)] = pkmArray[id][variables.get(notInEducation)] + distance;
                        break;
                    case Variables.PRIMRAY:
                        pfArray[id][variables.get(primary)] = pfArray[id][variables.get(primary)] + 1;
                        pkmArray[id][variables.get(primary)] = pkmArray[id][variables.get(primary)] + distance;
                        break;
                    case Variables.SECONDARY:
                        pfArray[id][variables.get(secondary)] = pfArray[id][variables.get(secondary)] + 1;
                        pkmArray[id][variables.get(secondary)] = pkmArray[id][variables.get(secondary)] + distance;
                        break;
                    case Variables.STUDENT:
                        pfArray[id][variables.get(student)] = pfArray[id][variables.get(student)] + 1;
                        pkmArray[id][variables.get(student)] = pkmArray[id][variables.get(student)] + distance;
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + attributes.getAttribute(Variables.CURRENT_EDUCATION) + " of " + Variables.CURRENT_EDUCATION);
                }
                // employment level
                switch (attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT).toString()) {
                    case Variables.LEVEL_OF_EMPLOYMENT_CAT_NONE:
                        pfArray[id][variables.get(employment0)] = pfArray[id][variables.get(employment0)] + 1;
                        pkmArray[id][variables.get(employment0)] = pkmArray[id][variables.get(employment0)] + distance;
                        break;
                    case Variables.LEVEL_OF_EMPLOYMENT_CAT_01_to_39:
                        pfArray[id][variables.get(employment39)] = pfArray[id][variables.get(employment39)] + 1;
                        pkmArray[id][variables.get(employment39)] = pkmArray[id][variables.get(employment39)] + distance;
                        break;
                    case Variables.LEVEL_OF_EMPLOYMENT_CAT_40_to_79:
                        pfArray[id][variables.get(employment79)] = pfArray[id][variables.get(employment79)] + 1;
                        pkmArray[id][variables.get(employment79)] = pkmArray[id][variables.get(employment79)] + distance;
                        break;
                    case Variables.LEVEL_OF_EMPLOYMENT_CAT_80_to_100:
                        pfArray[id][variables.get(employment100)] = pfArray[id][variables.get(employment100)] + 1;
                        pkmArray[id][variables.get(employment100)] = pkmArray[id][variables.get(employment100)] + distance;
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT) + " of " + Variables.LEVEL_OF_EMPLOYMENT_CAT);
                }
                // age categorie
                switch (attributes.getAttribute(Variables.AGE_CATEGORIE).toString()) {
                    case Variables.AGE_CATEGORIE_0_17:
                        pfArray[id][variables.get(ageCat17)] = pfArray[id][variables.get(ageCat17)] + 1;
                        pkmArray[id][variables.get(ageCat17)] = pkmArray[id][variables.get(ageCat17)] + distance;
                        break;
                    case Variables.AGE_CATEGORIE_18_24:
                        pfArray[id][variables.get(ageCat24)] = pfArray[id][variables.get(ageCat24)] + 1;
                        pkmArray[id][variables.get(ageCat24)] = pkmArray[id][variables.get(ageCat24)] + distance;
                        break;
                    case Variables.AGE_CATEGORIE_25_44:
                        pfArray[id][variables.get(ageCat44)] = pfArray[id][variables.get(ageCat44)] + 1;
                        pkmArray[id][variables.get(ageCat44)] = pkmArray[id][variables.get(ageCat44)] + distance;
                        break;
                    case Variables.AGE_CATEGORIE_45_64:
                        pfArray[id][variables.get(ageCat64)] = pfArray[id][variables.get(ageCat64)] + 1;
                        pkmArray[id][variables.get(ageCat64)] = pkmArray[id][variables.get(ageCat64)] + distance;
                        break;
                    case Variables.AGE_CATEGORIE_65_74:
                        pfArray[id][variables.get(ageCat74)] = pfArray[id][variables.get(ageCat74)] + 1;
                        pkmArray[id][variables.get(ageCat74)] = pkmArray[id][variables.get(ageCat74)] + distance;
                        break;
                    case Variables.AGE_CATEGORIE_75_XX:
                        pfArray[id][variables.get(ageCatXX)] = pfArray[id][variables.get(ageCatXX)] + 1;
                        pkmArray[id][variables.get(ageCatXX)] = pkmArray[id][variables.get(ageCatXX)] + distance;
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + attributes.getAttribute(Variables.AGE_CATEGORIE) + " of " + Variables.AGE_CATEGORIE);
                }
                // activity
                String toAct = trip.getDestinationActivity().getType().substring(0, trip.getDestinationActivity().getType().indexOf("_"));
                switch (toAct) {
                    case SBBActivities.cbHome:
                        pfArray[id][variables.get(cbhome)] = pfArray[id][variables.get(cbhome)] + 1;
                        pkmArray[id][variables.get(cbhome)] = pkmArray[id][variables.get(cbhome)] + distance;
                        break;
                    case SBBActivities.other:
                        pfArray[id][variables.get(other)] = pfArray[id][variables.get(other)] + 1;
                        pkmArray[id][variables.get(other)] = pkmArray[id][variables.get(other)] + distance;
                        break;
                    case SBBActivities.freight:
                        pfArray[id][variables.get(freight)] = pfArray[id][variables.get(freight)] + 1;
                        pkmArray[id][variables.get(freight)] = pkmArray[id][variables.get(freight)] + distance;
                        break;
                    case SBBActivities.business:
                        pfArray[id][variables.get(business)] = pfArray[id][variables.get(business)] + 1;
                        pkmArray[id][variables.get(business)] = pkmArray[id][variables.get(business)] + distance;
                        break;
                    case SBBActivities.shopping:
                        pfArray[id][variables.get(shopping)] = pfArray[id][variables.get(shopping)] + 1;
                        pkmArray[id][variables.get(shopping)] = pkmArray[id][variables.get(shopping)] + distance;
                        break;
                    case SBBActivities.work:
                        pfArray[id][variables.get(work)] = pfArray[id][variables.get(work)] + 1;
                        pkmArray[id][variables.get(work)] = pkmArray[id][variables.get(work)] + distance;
                        break;
                    case SBBActivities.education:
                        pfArray[id][variables.get(education)] = pfArray[id][variables.get(education)] + 1;
                        pkmArray[id][variables.get(education)] = pkmArray[id][variables.get(education)] + distance;
                        break;
                    case SBBActivities.leisure:
                        pfArray[id][variables.get(leisure)] = pfArray[id][variables.get(leisure)] + 1;
                        pkmArray[id][variables.get(leisure)] = pkmArray[id][variables.get(leisure)] + distance;
                        break;
                    case SBBActivities.home:
                        pfArray[id][variables.get(home)] = pfArray[id][variables.get(home)] + 1;
                        pkmArray[id][variables.get(home)] = pkmArray[id][variables.get(home)] + distance;
                        break;
                    case SBBActivities.exogeneous:
                        pfArray[id][variables.get(exogeneous)] = pfArray[id][variables.get(exogeneous)] + 1;
                        pkmArray[id][variables.get(exogeneous)] = pkmArray[id][variables.get(exogeneous)] + distance;
                        break;
                    case SBBActivities.accompany:
                        pfArray[id][variables.get(accompany)] = pfArray[id][variables.get(accompany)] + 1;
                        pkmArray[id][variables.get(accompany)] = pkmArray[id][variables.get(accompany)] + distance;
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + toAct + " of Activity");
                }
            }
        }
    }

    private void writeModalSplit1(String filename, Map<String, Integer> coding, Map<String, Integer> variables, Map<String, int[][]> pfMap, Map<String, double[][]> pkmMap) {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = {"RunID", "subpopulation", mode, all, carAvailable1, carAvailable0, ptSubNone, ptSubGA, ptSubVA, ptSubHTA, carNone, carGa, carHTA, carVA, nocarNone, nocarGa, nocarHTA,
            nocarVA, notInEducation, primary, secondary, student, employment0, employment39, employment79, employment100, ageCat17, ageCat24, ageCat44, ageCat64, ageCat74, ageCatXX, cbhome, other,
            freight, business, shopping, work, education, leisure, home, exogeneous, accompany};
        try (CSVWriter csvWriterPF = new CSVWriter("", columns, filename + "modal_split_pf.csv")) {
            try (CSVWriter csvWriterPKM = new CSVWriter("", columns, filename + "modal_split_pkm.csv")) {
                for (String subpopulation : Variables.SUBPOPULATIONS) {
                    for (Entry<String, Integer> col : coding.entrySet()) {
                        csvWriterPF.set("RunID", config.controler().getRunId());
                        csvWriterPKM.set("RunID", config.controler().getRunId());
                        csvWriterPF.set("subpopulation", subpopulation);
                        csvWriterPKM.set("subpopulation", subpopulation);
                        for (Entry<String, Integer> entry : variables.entrySet()) {
                            if (entry.getKey().equals(mode)) {
                                csvWriterPF.set(mode, col.getKey());
                                csvWriterPKM.set(mode, col.getKey());
                            } else {
                                String key = entry.getKey();
                                Integer value = entry.getValue();
                                csvWriterPF.set(key, Integer.toString((int) (pfMap.get(subpopulation)[col.getValue()][value] / sampleSize)));
                                csvWriterPKM.set(key, Integer.toString((int) pkmMap.get(subpopulation)[col.getValue()][value]));
                            }
                        }
                        csvWriterPF.writeRow();
                        csvWriterPKM.writeRow();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void analyzeTimeSteps(Map<String, Integer> variablesTime, Map<String, int[][]> timeMap, Map<String, int[][]> travelTimeMap) {
        for (Entry<Id<Person>, Plan> entry : experiencedPlansService.getExperiencedPlans().entrySet()) {
            Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
            for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
                SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
                String tmpMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (tmpMode.equals("walk_main")) {
                    tmpMode = "walk";
                }
                int middle = (int) ((trip.getOriginActivity().getEndTime().seconds() + trip.getDestinationActivity().getStartTime().seconds()) / 2);
                int time = (middle - (middle % timeSplit)) / timeSplit;
                int[][] subpopulationArrray = timeMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
                subpopulationArrray[time][variablesTime.get(all)] = subpopulationArrray[time][variablesTime.get(all)] + 1;
                int travelTime = (int) ((trip.getDestinationActivity().getStartTime().seconds() - trip.getOriginActivity().getEndTime().seconds()));
                int timeArray = (travelTime - (travelTime % travelTimeSplit)) / travelTimeSplit;
                int[][] subpopulationTravelTime = travelTimeMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
                if (timeArray >= subpopulationTravelTime.length) {
                    timeArray = subpopulationTravelTime.length - 1;
                }
                subpopulationTravelTime[timeArray][variablesTime.get(all)] = subpopulationTravelTime[timeArray][variablesTime.get(all)] + 1;
                switch (tmpMode) {
                    case SBBModes.WALK_FOR_ANALYSIS:
                        subpopulationArrray[time][variablesTime.get(walk)] = subpopulationArrray[time][variablesTime.get(walk)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(walk)] = subpopulationTravelTime[timeArray][variablesTime.get(walk)] + 1;
                        break;
                    case SBBModes.RIDE:
                        subpopulationArrray[time][variablesTime.get(ride)] = subpopulationArrray[time][variablesTime.get(ride)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(ride)] = subpopulationTravelTime[timeArray][variablesTime.get(ride)] + 1;
                        break;
                    case SBBModes.CAR:
                        subpopulationArrray[time][variablesTime.get(car)] = subpopulationArrray[time][variablesTime.get(car)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(car)] = subpopulationTravelTime[timeArray][variablesTime.get(car)] + 1;
                        break;
                    case SBBModes.PT:
                        subpopulationArrray[time][variablesTime.get(bike)] = subpopulationArrray[time][variablesTime.get(bike)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(bike)] = subpopulationTravelTime[timeArray][variablesTime.get(bike)] + 1;
                        break;
                    case SBBModes.BIKE:
                        subpopulationArrray[time][variablesTime.get(pt)] = subpopulationArrray[time][variablesTime.get(pt)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(pt)] = subpopulationTravelTime[timeArray][variablesTime.get(pt)] + 1;
                        break;
                    case SBBModes.AVTAXI:
                        subpopulationArrray[time][variablesTime.get(drt)] = subpopulationArrray[time][variablesTime.get(drt)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(drt)] = subpopulationTravelTime[timeArray][variablesTime.get(drt)] + 1;
                        break;
                    case SBBModes.DRT:
                        subpopulationArrray[time][variablesTime.get(avtaxi)] = subpopulationArrray[time][variablesTime.get(avtaxi)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(avtaxi)] = subpopulationTravelTime[timeArray][variablesTime.get(avtaxi)] + 1;
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + tmpMode + " of: mode");
                }
                String toAct = trip.getDestinationActivity().getType().substring(0, trip.getDestinationActivity().getType().indexOf("_"));
                switch (toAct) {
                    case SBBActivities.cbHome:
                        subpopulationArrray[time][variablesTime.get(cbhome)] = subpopulationArrray[time][variablesTime.get(cbhome)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(cbhome)] = subpopulationTravelTime[timeArray][variablesTime.get(cbhome)] + 1;
                        break;
                    case SBBActivities.other:
                        subpopulationArrray[time][variablesTime.get(other)] = subpopulationArrray[time][variablesTime.get(other)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(other)] = subpopulationTravelTime[timeArray][variablesTime.get(other)] + 1;
                        break;
                    case SBBActivities.freight:
                        subpopulationArrray[time][variablesTime.get(freight)] = subpopulationArrray[time][variablesTime.get(freight)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(freight)] = subpopulationTravelTime[timeArray][variablesTime.get(freight)] + 1;
                        break;
                    case SBBActivities.business:
                        subpopulationArrray[time][variablesTime.get(business)] = subpopulationArrray[time][variablesTime.get(business)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(business)] = subpopulationTravelTime[timeArray][variablesTime.get(business)] + 1;
                        break;
                    case SBBActivities.shopping:
                        subpopulationArrray[time][variablesTime.get(shopping)] = subpopulationArrray[time][variablesTime.get(shopping)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(shopping)] = subpopulationTravelTime[timeArray][variablesTime.get(shopping)] + 1;
                        break;
                    case SBBActivities.work:
                        subpopulationArrray[time][variablesTime.get(work)] = subpopulationArrray[time][variablesTime.get(work)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(work)] = subpopulationTravelTime[timeArray][variablesTime.get(work)] + 1;
                        break;
                    case SBBActivities.education:
                        subpopulationArrray[time][variablesTime.get(education)] = subpopulationArrray[time][variablesTime.get(education)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(education)] = subpopulationTravelTime[timeArray][variablesTime.get(education)] + 1;
                        break;
                    case SBBActivities.leisure:
                        subpopulationArrray[time][variablesTime.get(leisure)] = subpopulationArrray[time][variablesTime.get(leisure)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(leisure)] = subpopulationTravelTime[timeArray][variablesTime.get(leisure)] + 1;
                        break;
                    case SBBActivities.home:
                        subpopulationArrray[time][variablesTime.get(home)] = subpopulationArrray[time][variablesTime.get(home)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(home)] = subpopulationTravelTime[timeArray][variablesTime.get(home)] + 1;
                        break;
                    case SBBActivities.exogeneous:
                        subpopulationArrray[time][variablesTime.get(exogeneous)] = subpopulationArrray[time][variablesTime.get(exogeneous)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(exogeneous)] = subpopulationTravelTime[timeArray][variablesTime.get(exogeneous)] + 1;
                        break;
                    case SBBActivities.accompany:
                        subpopulationArrray[time][variablesTime.get(accompany)] = subpopulationArrray[time][variablesTime.get(accompany)] + 1;
                        subpopulationTravelTime[timeArray][variablesTime.get(accompany)] = subpopulationTravelTime[timeArray][variablesTime.get(accompany)] + 1;
                        break;
                    default:
                        throw new NoSuchElementException("Unknow value " + toAct + " of Activity");
                }
            }
        }
    }

    private void writeTimeSteps(String filename, Map<String, Integer> variablesTime, Map<String, int[][]> timeMap, Map<String, int[][]> travelTimeMap) {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = {"RunID", "subpopulation", "time", all, pt, walk, bike, car, ride, avtaxi, drt, cbhome, other, freight, business, shopping, work, education, leisure, home, exogeneous,
            accompany};
        try (CSVWriter csvWriter = new CSVWriter("", columns, filename + "middle_time_distribution.csv")) {
            for (Entry<String, int[][]> entry : timeMap.entrySet()) {
                for (int i = 0; i < config.qsim().getEndTime().seconds() / timeSplit; i++) {
                    csvWriter.set("RunID", config.controler().getRunId());
                    csvWriter.set("subpopulation", entry.getKey());
                    csvWriter.set("time", Integer.toString(i * timeSplit));
                    for (Entry<String, Integer> var : variablesTime.entrySet()) {
                        csvWriter.set(var.getKey(), Integer.toString((int) (entry.getValue()[i][var.getValue()] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] columns2 = {"RunID", "subpopulation", "time", all, pt, walk, bike, car, ride, avtaxi, drt, cbhome, other, freight, business, shopping, work, education, leisure, home, exogeneous,
            accompany};
        try (CSVWriter csvWriter = new CSVWriter("", columns2, filename + "travel_time_distribution.csv")) {
            for (Entry<String, int[][]> entry : travelTimeMap.entrySet()) {
                for (int i = 0; i < (lastTravelTimeValue / travelTimeSplit) + 2; i++) {
                    csvWriter.set("RunID", config.controler().getRunId());
                    csvWriter.set("subpopulation", entry.getKey());
                    csvWriter.set("time", Integer.toString(i * travelTimeSplit));
                    if (i == (lastTravelTimeValue / travelTimeSplit) + 1) {
                        csvWriter.set("time", ">5");
                    }
                    for (Entry<String, Integer> var : variablesTime.entrySet()) {
                        csvWriter.set(var.getKey(), Integer.toString((int) (entry.getValue()[i][var.getValue()] / sampleSize)));
                    }
                    csvWriter.writeRow();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, Integer> createChangesMap() {
        Map<String, Integer> variables = new HashMap<>();
        variables.put("bahn", 0);
        variables.put("opnv", 1);
        variables.put("oev", 2);
        return variables;
    }

    private Map<String, Integer> getModesMap() {
        Map<String, Integer> coding = new HashMap<>();
        List<String> modes = SBBModes.MAIN_MODES;
        for (int i = 0; i < modes.size(); i++) {
            coding.put(modes.get(i), i);
        }
        return coding;
    }

    private Map<String, int[][]> createPFForSubpopulationMap(int modeSize, int varSize) {
        Map<String, int[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new int[modeSize][varSize]);
        }
        return subpopulaionMap;
    }

    private Map<String, double[][]> createPKMForSubpopulationMap(int modeSize, int varSize) {
        Map<String, double[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new double[modeSize][varSize]);
        }
        return subpopulaionMap;
    }

    private Map<String, int[][]> createTimeStapsForSubpopulaitonMap(int timeStepsSize, int varSize) {
        Map<String, int[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new int[timeStepsSize][varSize]);
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
        String description = route.getRouteDescription();
        int starLineIndex = description.indexOf("\"transitLineId\":\"") + "\"transitLineId\":\"".length();
        int endLineIndex = description.indexOf("\",\"accessFacilityId");
        String transitLine = description.substring(starLineIndex, endLineIndex);
        int startRouteIndex = description.indexOf("\"transitRouteId\":\"") + "\"transitRouteId\":\"".length();
        int endRouteIndex = description.indexOf("\",\"boardingTime");
        String routeLine = description.substring(startRouteIndex, endRouteIndex);
        return transitSchedule.getTransitLines().get(Id.create(transitLine, TransitLine.class)).getRoutes().get(Id.create(routeLine, TransitRoute.class)).getTransportMode();
    }

}
