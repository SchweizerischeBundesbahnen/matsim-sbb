package ch.sbb.matsim.analysis.modalsplit;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.*;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.intermodal.analysis.SBBTransferAnalysisListener;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

    Map<Id<TransitStopFacility>, TrainStation> trainStationsMap = new HashMap<>();
    Map<String, Integer> modesMap = new HashMap<>();
    Map<String, Integer> variablesMSMap = new HashMap<>();
    Map<String, double[][]> subpopulaionDistanceMap = new HashMap<>();
    private Map<String, double[][]> subpopulaionMSPFMap = new HashMap<>();
    private Map<String, double[][]> subpopulaionMSPKMMap = new HashMap<>();
    private Map<String, double[][]> subpopulationChangeMap = new HashMap<>();

    private Map<String, Integer> changesMap = new HashMap<>();

    private final List<String> changeOrderList = List.of("train", "opnv", "oev");
    private final List<String> changeLableList = List.of("0", "1", "2", "3", "4", ">5");

    Map<String, int[][]> timeMap = new HashMap<>();
    Map<String, int[][]> travelTimeMap = new HashMap<>();


    // Variables
    private final String runID = "RunID";
    private final String subpopulation = "subpopulation";
    private Map<String, Integer> variablesTimeStepsMap = new HashMap<>();

    private Map<Id<TransitStopFacility>, TrainStation> generateTrainStationMap() {
        assert transitSchedule != null;
        Map<Id<TransitStopFacility>, TrainStation> trainStationsMap = new HashMap<>();
        for (TransitStopFacility transitStopFacility : transitSchedule.getFacilities().values()) {
            trainStationsMap.put(transitStopFacility.getId(), new TrainStation(transitStopFacility, zones.findZone(transitStopFacility.getCoord())));
        }
        return trainStationsMap;
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
        this.outputLocation = outputLocation + "SBB_";
        this.trainStationsMap = generateTrainStationMap();
        this.modesMap = getModesMap();
        this.variablesMSMap = createVariablesModalSplitMap();
        this.variablesTimeStepsMap = createVariablesTimeStepsMap();
        this.subpopulaionDistanceMap = createArrayForSubpopulationMap(this.modesMap.size(), distanceClassesLable.size());
        this.subpopulaionMSPFMap = createArrayForSubpopulationMap(this.modesMap.size(), variablesMSMap.size());
        this.subpopulaionMSPKMMap = createArrayForSubpopulationMap(this.modesMap.size(), variablesMSMap.size());
        this.subpopulationChangeMap = createArrayForSubpopulationMap(3, 6);
        this.changesMap = getChangesMap();
        this.timeMap = createTimeStepsForSubpopulaitonMap((int) (config.qsim().getEndTime().seconds() / timeSplit), variablesTimeStepsMap.size());
        this.travelTimeMap = createTimeStepsForSubpopulaitonMap((lastTravelTimeValue / travelTimeSplit) + 2, variablesTimeStepsMap.size());

        // analyzing
        startAnalyze();

        // writing the different files
        writeStationAnalysis();
        writeDistanceClassesAnalysis();
        writeModalSplit();
        writeChanges();
        writeTimeSteps();

    }

    private void writeTimeSteps() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = {"RunID", "subpopulation", "time", all, pt, walk, bike, car, ride, avtaxi, drt, cbhome, other, freight, business, shopping, work, education, leisure, home, exogeneous,
            accompany};
        try (CSVWriter csvWriter = new CSVWriter("", columns, outputLocation + "middle_time_distribution.csv")) {
            for (Entry<String, int[][]> entry : timeMap.entrySet()) {
                for (int i = 0; i < config.qsim().getEndTime().seconds() / timeSplit; i++) {
                    csvWriter.set("RunID", config.controler().getRunId());
                    csvWriter.set("subpopulation", entry.getKey());
                    csvWriter.set("time", Integer.toString(i * timeSplit));
                    for (Entry<String, Integer> var : variablesTimeStepsMap.entrySet()) {
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
        try (CSVWriter csvWriter = new CSVWriter("", columns2, outputLocation + "travel_time_distribution.csv")) {
            for (Entry<String, int[][]> entry : travelTimeMap.entrySet()) {
                for (int i = 0; i < (lastTravelTimeValue / travelTimeSplit) + 2; i++) {
                    csvWriter.set("RunID", config.controler().getRunId());
                    csvWriter.set("subpopulation", entry.getKey());
                    csvWriter.set("time", Integer.toString(i * travelTimeSplit));
                    if (i == (lastTravelTimeValue / travelTimeSplit) + 1) {
                        csvWriter.set("time", ">5");
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

    private Map<String, Integer> getChangesMap() {
    return null;
    }

    private void writeModalSplit() {
        final double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] colums = new String[2 + variablesMSMap.size()];
        colums[0] = this.runID;
        colums[1] = this.subpopulation;
        int i = 2;
        for (String var : this.variablesMSMap.keySet()) {
            colums[i++] = var;
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
        try (CSVWriter csvWriterPKM = new CSVWriter("", colums, outputLocation + "modal_split_pkm.csv")) {
            for (String subpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> modeEntry : modesMap.entrySet()) {
                    csvWriterPKM.set(this.runID, config.controler().getRunId());
                    csvWriterPKM.set(this.subpopulation, subpopulation);
                    for (Entry<String, Integer> entry : this.variablesMSMap.entrySet()) {
                        if (entry.getKey().equals(mode)) {
                            csvWriterPKM.set(mode, modeEntry.getKey());
                        } else {
                            String key = entry.getKey();
                            Integer value = entry.getValue();
                            csvWriterPKM.set(key, Integer.toString((int) (this.subpopulaionMSPKMMap.get(subpopulation)[modeEntry.getValue()][value])));
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

    private void writeDistanceClassesAnalysis() {
        final double sampleSize = ConfigUtils.addOrGetModule(this.config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        String[] columns = new String[3 + distanceClassesValue.size()];
        columns[0] = runID;
        columns[1] = subpopulation;
        columns[2] = mode;
        for (int i = 0; i < distanceClassesValue.size(); i++) {
            columns[i + 3] = distanceClassesLable.get(i);
        }
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + "distace_classes.csv")) {
            for (String subpopulation : Variables.SUBPOPULATIONS) {
                for (Entry<String, Integer> col : this.modesMap.entrySet()) {
                    csvWriter.set(runID, this.config.controler().getRunId());
                    csvWriter.set(this.subpopulation, subpopulation);
                    csvWriter.set(mode, col.getKey());
                    for (int i = 0; i < distanceClassesValue.size(); i++) {
                        csvWriter.set(distanceClassesLable.get(i), Integer.toString((int) (this.subpopulaionDistanceMap.get(subpopulation)[col.getValue()][i] / sampleSize)));
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

            // analysis for access and egress mode for each train station
            analyzeTrainStations(entry);
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

    private void analyzeTimes(Entry<Id<Person>, Plan> entry) {
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
            subpopulationArrray[time][variablesTimeStepsMap.get(all)] = subpopulationArrray[time][variablesTimeStepsMap.get(all)] + 1;
            int travelTime = (int) ((trip.getDestinationActivity().getStartTime().seconds() - trip.getOriginActivity().getEndTime().seconds()));
            int timeArray = (travelTime - (travelTime % travelTimeSplit)) / travelTimeSplit;
            int[][] subpopulationTravelTime = travelTimeMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            if (timeArray >= subpopulationTravelTime.length) {
                timeArray = subpopulationTravelTime.length - 1;
            }
            subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(all)] = subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(all)] + 1;
            for (String mode : modesMS) {
                if (("mode_" + tmpMode).equals(mode)) {
                    subpopulationArrray[time][variablesTimeStepsMap.get(mode)] = subpopulationArrray[time][variablesTimeStepsMap.get(mode)] + 1;
                    subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(mode)] = subpopulationTravelTime[timeArray][variablesTimeStepsMap.get(mode)] + 1;
                    break;
                }
            }
        }
    }

    private void analyzeChanges(Entry<Id<Person>, Plan> entry) {
        List<String> modes = new ArrayList<>();
        for (Trip trip : TripStructureUtils.getTrips(entry.getValue())) {
            for (Leg leg : trip.getLegsOnly()) {
                if (leg.getMode().equals(SBBModes.PT)) {
                    modes.add(getModeOfTransitRoute(leg.getRoute()));
                }
            }
        }
        if (modes.size() == 0) {
            return;
        }
        int train = 0;
        int opnv = 0;
        int oev = 0;
        Iterator<String> iterator = modes.iterator();
        String firstMode = iterator.next();
        while (iterator.hasNext()) {
            String secondMode = iterator.next();
            if (firstMode.equals(PTSubModes.RAIL) && secondMode.equals(PTSubModes.RAIL)) {
                train++;
            } else if (!firstMode.equals(PTSubModes.RAIL) && !secondMode.equals(PTSubModes.RAIL)) {
                opnv++;
            } else {
                oev++;
            }
        }
        Attributes attributes = population.getPersons().get(entry.getKey()).getAttributes();
        double[][] array = subpopulationChangeMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
        if (train > 5) {
            array[0][5] = array[0][5] + 1;
        } else {
            array[0][train] = array[0][train] + 1;
        }
        if (opnv > 5) {
            array[1][5] = array[1][5] + 1;
        } else {
            array[1][opnv] = array[1][opnv] + 1;
        }
        if (oev > 5) {
            array[2][5] = array[2][5] + 1;
        } else {
            array[2][oev] = array[2][oev] + 1;
        }
    }

    private void analyzeModalSplit(Entry<Id<Person>, Plan> entry) {
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
            double[][] pfArray = subpopulaionMSPFMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            pfArray[modeId][variablesMSMap.get(all)] = pfArray[modeId][variablesMSMap.get(all)] + 1;

            double[][] pkmArray = subpopulaionMSPKMMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            pkmArray[modeId][variablesMSMap.get(all)] = pkmArray[modeId][variablesMSMap.get(all)] + distance;

            // car available
            for (String carAva : carAvailable) {
                String attribut = attributes.getAttribute(Variables.CAR_AVAIL).toString();
                if ((Variables.CAR_AVAIL + "_" + attribut).equals(carAva)) {
                    pfArray[modeId][variablesMSMap.get(carAva)] = pfArray[modeId][variablesMSMap.get(carAva)] + 1;
                    pkmArray[modeId][variablesMSMap.get(carAva)] = pkmArray[modeId][variablesMSMap.get(carAva)] + distance;
                    break;
                }
            }
            // pt subscription
            for (String ptSub : ptSubscription) {
                String attribut = attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString();
                if ((Variables.PT_SUBSCRIPTION + "_" + attribut).equals(ptSub)) {
                    pfArray[modeId][variablesMSMap.get(ptSub)] = pfArray[modeId][variablesMSMap.get(ptSub)] + 1;
                    pkmArray[modeId][variablesMSMap.get(ptSub)] = pkmArray[modeId][variablesMSMap.get(ptSub)] + distance;
                    break;
                }
            }
            // combination car available and pt subscription
            for (String carPT : carAndPt) {
                String attributCar = attributes.getAttribute(Variables.CAR_AVAIL).toString();
                String attributPT = attributes.getAttribute(Variables.PT_SUBSCRIPTION).toString();
                if ((Variables.CAR_AVAIL + "_" + attributCar + "_" + Variables.PT_SUBSCRIPTION + "_" + attributPT).equals(carPT)) {
                    pfArray[modeId][variablesMSMap.get(carPT)] = pfArray[modeId][variablesMSMap.get(carPT)] + 1;
                    pkmArray[modeId][variablesMSMap.get(carPT)] = pkmArray[modeId][variablesMSMap.get(carPT)] + distance;
                    break;
                }
            }
            // kind of education
            for (String edu : educationType) {
                String attribut = attributes.getAttribute(Variables.CURRENT_EDUCATION).toString();
                if ((Variables.CURRENT_EDUCATION + "_" + attribut).equals(edu)) {
                    pfArray[modeId][variablesMSMap.get(edu)] = pfArray[modeId][variablesMSMap.get(edu)] + 1;
                    pkmArray[modeId][variablesMSMap.get(edu)] = pkmArray[modeId][variablesMSMap.get(edu)] + distance;
                    break;
                }
            }
            // employment rate
            for (String empRate : employmentRate) {
                String attribut = attributes.getAttribute(Variables.LEVEL_OF_EMPLOYMENT_CAT).toString();
                if ((Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + attribut).equals(empRate)) {
                    pfArray[modeId][variablesMSMap.get(empRate)] = pfArray[modeId][variablesMSMap.get(empRate)] + 1;
                    pkmArray[modeId][variablesMSMap.get(empRate)] = pkmArray[modeId][variablesMSMap.get(empRate)] + distance;
                    break;
                }
            }
            // age categorie
            for (String age : ageCategorie) {
                String attribut = attributes.getAttribute(Variables.AGE_CATEGORIE).toString();
                if ((Variables.AGE_CATEGORIE + "_" + attribut).equals(age)) {
                    pfArray[modeId][variablesMSMap.get(age)] = pfArray[modeId][variablesMSMap.get(age)] + 1;
                    pkmArray[modeId][variablesMSMap.get(age)] = pkmArray[modeId][variablesMSMap.get(age)] + distance;
                    break;
                }
            }
            // activity type for end activity
            for (String act : toActTypeList) {
                String acttyp = trip.getDestinationActivity().getType().substring(0, trip.getDestinationActivity().getType().indexOf("_"));
                if ((toActType + "_" + acttyp).equals(act)) {
                    pfArray[modeId][variablesMSMap.get(act)] = pfArray[modeId][variablesMSMap.get(act)] + 1;
                    pkmArray[modeId][variablesMSMap.get(act)] = pkmArray[modeId][variablesMSMap.get(act)] + distance;
                    break;
                }
            }
        }
    }

    private Map<String, double[][]> createArrayForSubpopulationMap(int modeSize, int varSize) {
        Map<String, double[][]> subpopulaionMap = new HashMap<>();
        for (String subpopulation : Variables.SUBPOPULATIONS) {
            subpopulaionMap.put(subpopulation, new double[modeSize][varSize]);
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
            double[][] disArray = this.subpopulaionDistanceMap.get(attributes.getAttribute(Variables.SUBPOPULATION));
            for (int disClass : distanceClassesValue) {
                if (distance <= disClass) {
                    disArray[modeID][distanceClassesValue.indexOf(disClass)] = disArray[modeID][distanceClassesValue.indexOf(disClass)] + 1;
                    break;
                }
            }
        }
    }

    private void writeStationAnalysis() {
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
            runID + "," + codeID + "," + stopNumber + "," + code + "," + trainStationName + "," + x + "," + y + "," + zone + "," + "," + einstiege + "," + ausstiege + "," + umstiege + "," + zustiege
                + ","
                + wegstiege);
        for (String mode : TrainStation.getModes()) {
            head.append(",").append("Zielaustieg_").append(mode);
            head.append(",").append("Quelleinstieg_").append(mode);
        }
        String[] columns = head.toString().split(",");
        try (CSVWriter csvWriter = new CSVWriter("", columns, this.outputLocation + "train_staions_count.csv")) {
            for (Entry<Id<TransitStopFacility>, TrainStation> entry : trainStationsMap.entrySet()) {
                csvWriter.set(runID, config.controler().getRunId());
                csvWriter.set(codeID, entry.getValue().getStation().getId().toString());
                csvWriter.set(stopNumber, entry.getValue().getStation().getAttributes().getAttribute("02_Stop_No").toString());
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
                csvWriter.set(einstiege, Integer.toString((int) (entry.getValue().getEntered() / sampleSize)));
                csvWriter.set(ausstiege, Integer.toString((int) (entry.getValue().getExited() / sampleSize)));
                for (String mode : TrainStation.getModes()) {
                    csvWriter.set("Zielaustieg_" + mode, Integer.toString((int) (entry.getValue().getEnteredMode()[TrainStation.getModes().indexOf(mode)] / sampleSize)));
                    csvWriter.set("Quelleinstieg_" + mode, Integer.toString((int) (entry.getValue().getExitedMode()[TrainStation.getModes().indexOf(mode)] / sampleSize)));
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
                                            startTrainStation.addUmstiegeBahnBahn();
                                        } else {
                                            startTrainStation.addUmsteigeAHPBahn();
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
                                        endTrainStation.addUmstiegeBahnBahn();
                                    } else {
                                        endTrainStation.addUmsteigeBahnAHP();
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

    private void writeChanges() {
        String[] columns = {"RunID", "Subpopulation", "Umsteigetyp", "0", "1", "2", "3", "4", ">5"};
        try (CSVWriter csvWriter = new CSVWriter("", columns, outputLocation + "changes_count.csv")) {
            for (Entry<String, double[][]> entry : subpopulationChangeMap.entrySet()) {
                Map<String, Integer> mapChange = new HashMap<>();
                mapChange.put("changesTrain", 0);
                mapChange.put("changesOPNV", 1);
                mapChange.put("changesOEV", 2);
                for (Entry<String, Integer> change : mapChange.entrySet()) {
                    csvWriter.set("RunID", config.controler().getRunId());
                    csvWriter.set("Subpopulation", entry.getKey());
                    csvWriter.set("Umsteigetyp", change.getKey());
                    for (int i = 0; i < 6; i++) {
                            csvWriter.set(changeLableList.get(i), Integer.toString( (int) entry.getValue()[change.getValue()][i]));
                    }
                    csvWriter.writeRow();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
