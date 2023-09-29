package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import java.util.List;

public final class MSVariables {

    public static final String separator = "_";
    public static final String runID = "runID";
    public static final String subpopulation = "subpopulation";
    public static final String all = "all";
    public static final String carAvailableTrue = Variables.CAR_AVAIL + separator + Variables.CAR_AVAL_TRUE;
    public static final String carAvailableFalse = Variables.CAR_AVAIL + separator + Variables.CAR_AVAL_FALSE;
    public static final String ptSubNone = Variables.PT_SUBSCRIPTION + separator + Variables.PT_SUBSCRIPTION_NONE;
    public static final String ptSubGA = Variables.PT_SUBSCRIPTION + separator + Variables.GA;
    public static final String ptSubVA = Variables.PT_SUBSCRIPTION + separator + Variables.VA;
    public static final String ptSubHTA = Variables.PT_SUBSCRIPTION + separator + Variables.HTA;
    public static final String carNone = carAvailableTrue + separator + ptSubNone;
    public static final String carGa = carAvailableTrue + separator + ptSubGA;
    public static final String carHTA = carAvailableTrue + separator + ptSubVA;
    public static final String carVA = carAvailableTrue + separator + ptSubHTA;
    public static final String nocarNone = carAvailableFalse + separator + ptSubNone;
    public static final String nocarGa = carAvailableFalse + separator + ptSubGA;
    public static final String nocarHTA = carAvailableFalse + separator + ptSubVA;
    public static final String nocarVA = carAvailableFalse + separator + ptSubHTA;
    public static final String notInEducation = Variables.CURRENT_EDUCATION + separator + Variables.NOT_IN_EDUCATION;
    public static final String primary = Variables.CURRENT_EDUCATION + separator + Variables.PRIMARY;
    public static final String secondary = Variables.CURRENT_EDUCATION + separator + Variables.SECONDARY;
    public static final String student = Variables.CURRENT_EDUCATION + separator + Variables.STUDENT;
    public static final String apprentice = Variables.CURRENT_EDUCATION + separator + Variables.APPRENTICE;
    public static final String employment0 = Variables.LEVEL_OF_EMPLOYMENT_CAT + separator + Variables.LEVEL_OF_EMPLOYMENT_CAT_NONE;
    public static final String employment39 = Variables.LEVEL_OF_EMPLOYMENT_CAT + separator + Variables.LEVEL_OF_EMPLOYMENT_CAT_01_to_39;
    public static final String employment79 = Variables.LEVEL_OF_EMPLOYMENT_CAT + separator + Variables.LEVEL_OF_EMPLOYMENT_CAT_40_to_79;
    public static final String employment100 = Variables.LEVEL_OF_EMPLOYMENT_CAT + separator + Variables.LEVEL_OF_EMPLOYMENT_CAT_80_to_100;
    public static final String ageCat17 = Variables.AGE_CATEGORY + separator + Variables.AGE_CATEGORY_0_17;
    public static final String ageCat24 = Variables.AGE_CATEGORY + separator + Variables.AGE_CATEGORY_18_24;
    public static final String ageCat44 = Variables.AGE_CATEGORY + separator + Variables.AGE_CATEGORY_25_44;
    public static final String ageCat64 = Variables.AGE_CATEGORY + separator + Variables.AGE_CATEGORY_45_64;
    public static final String ageCat74 = Variables.AGE_CATEGORY + separator + Variables.AGE_CATEGORY_65_74;
    public static final String ageCatXX = Variables.AGE_CATEGORY + separator + Variables.AGE_CATEGORY_75_XX;
    public static final String toActType = "to_act_type";
    public static final String home = toActType + separator + SBBActivities.home;
    public static final String cbhome = toActType + separator + SBBActivities.cbHome;
    public static final String leisure = toActType + separator + SBBActivities.leisure;
    public static final String other = toActType + separator + SBBActivities.other;
    public static final String freight = toActType + separator + SBBActivities.freight;
    public static final String business = toActType + separator + SBBActivities.business;
    public static final String shopping = toActType + separator + SBBActivities.shopping;
    public static final String work = toActType + separator + SBBActivities.work;
    public static final String education = toActType + separator + SBBActivities.education;
    public static final String exogeneous = toActType + separator + SBBActivities.exogeneous;
    public static final String accompany = toActType + separator + SBBActivities.accompany;
    public static final String mode = "mode";
    public static final String submode = "submode";
    public static final String zone = "zone";
    public static final String accessMode = "access_mode";
    public static final String egressMode = "egress_mode";
    public static final String walk = mode + separator + SBBModes.WALK_FOR_ANALYSIS;
    public static final String ride = mode + separator + SBBModes.RIDE;
    public static final String car = mode + separator + SBBModes.CAR;
    public static final String pt = mode + separator + SBBModes.PT;
    public static final String bike = mode + separator + SBBModes.BIKE;
    public static final String avtaxi = mode + separator + SBBModes.AVTAXI;
    public static final String drt = mode + separator + SBBModes.DRT;
    public static final String rail = submode + separator + SBBModes.RAIL;
    public static final String fqrail = submode + separator + SBBModes.FQRAIL;
    public static final String changeTrain = "train";
    public static final String changeTrainFQ = "trainFQ";
    public static final String changeTrainAll = "trainAll";
    public static final String changeOEV = "oev";
    public static final String changeOPNV = "opnv";
    public static final int timeSplit = 15 * 60;
    public static final int travelTimeSplit = 10 * 60;
    public static final int lastTravelTimeValue = 5 * 60 * 60;
    public static final List<Integer> distanceClassesValue = List.of(2, 4, 6, 8, 10, 15, 20, 25, 30, 40, 50, 100, 150, 200, 250, 300);
    public static final List<String> distanceClassesLable = List.of("0-2", "2-4", "4-6", "6-8", "8-10", "10-15", "15-20", "20-25", "25-30", "30-40", "40-50", "50-100", "100-150", "150-200", "200-250", "250-300", "300+");
    public static final List<Integer> distanceClassesFeederValue = List.of(-1, 0, 200, 400, 600, 800, 1000, 1200, 1400, 1600, 1800, 2000, 2200, 2400, 2600, 2800, 3000, 3200, 3400, 3600, 3800, 4000, 5000, 6000, 7000, 8000, 9000, 10000, 20000, 30000, 9999999);
    public static final List<String> distanceClassesFeederLable = List.of("na", "0", "0-200", "200-400", "400-600", "600-800", "800-1000", "1000-1200", "1200-1400", "1400-1600", "1600-1800",
            "1800-2000", "2000-2200", "2200-2400", "2400-2600", "2600-2800", "2800-3000", "3000-3200", "3200-3400",
            "3400-3600", "3600-3800", "3800-4000", "4000-5000", "5000-6000", "6000-7000", "7000-8000", "8000-9000",
            "9000-10000", "10000-20000", "20000-30000", "30000+");
    public static final List<String> changeOrderList = List.of(changeTrain, changeOPNV, changeOEV, changeTrainFQ, changeTrainAll);
    public static final List<String> changeLableList = List.of("0", "1", "2", "3", "4", ">=5");
    public static final List<String> modesMS = List.of(walk, ride, car, pt, bike, avtaxi, drt);
    public static final List<String> submodes = List.of(rail, fqrail);
    public static final List<String> carAvailable = List.of(carAvailableTrue, carAvailableFalse);
    public static final List<String> ptSubscription = List.of(ptSubNone, ptSubGA, ptSubVA, ptSubHTA);
    public static final List<String> carAndPt = List.of(carNone, carGa, carHTA, carVA, nocarNone, nocarGa, nocarHTA, nocarVA);
    public static final List<String> educationType = List.of(notInEducation, primary, secondary, apprentice, student);
    public static final List<String> employmentRate = List.of(employment0, employment39, employment79, employment100);
    public static final List<String> ageCategory = List.of(ageCat17, ageCat24, ageCat44, ageCat64, ageCat74, ageCatXX);
    public static final List<String> toActTypeList = List.of(home, cbhome, leisure, other, freight, business, shopping, work, education, exogeneous, accompany);
    public static final List<List<String>> varList = List.of(carAvailable, ptSubscription, carAndPt, educationType, employmentRate, ageCategory , toActTypeList, submodes);
    public static final List<List<String>> varListFeeder = List.of(carAvailable, ptSubscription, carAndPt, educationType, employmentRate, ageCategory , toActTypeList, distanceClassesFeederLable);
    public static final List<List<String>> varTimeList = List.of(modesMS, submodes, toActTypeList);
    public static final String oNMiddleTimeSteps = "middle_time_distribution.csv";
    public static final String oNTravelTimeDistribution = "travel_time_distribution.csv";
    public static final String oNModalSplitPF = "modal_split_PF.csv";
    public static final String oNModalSplitPKM = "modal_split_PKM.csv";
    public static final String oNModalSplitFeederPF = "modal_split_feeder_PF.csv";
    public static final String oNModalSplitFeederPKM = "modal_split_feeder_PKM.csv";
    public static final String oNModalSplitZoneFeederPF = "modal_split_feeder_zone_PF.csv";
    public static final String oNDistanceClasses = "distance_classes.csv";
    public static final String oNChangesCount = "changes_count.csv";
    public static final String oNDistanceChangesCount = "distance_changes_count.csv";
    public static final String oNChangesPKM = "changes_pkm.csv";
    public static final String oNStopStationsCount = "stop_stations_count.csv";
    public static final String oNTrainStrationsCount = "train_stations_count.csv";
    private MSVariables() {
    }

}
