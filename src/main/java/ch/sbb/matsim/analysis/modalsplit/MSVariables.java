package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import java.util.List;

public final class MSVariables {

    static final String separator = "_";
    static final String runID = "RunID";
    static final String subpopulation = "subpopulation";
    static final String all = "all";
    static final String carAvailable1 = Variables.CAR_AVAIL + separator + "1";
    static final String carAvailable0 = Variables.CAR_AVAIL + separator + "0";
    static final String ptSubNone = Variables.PT_SUBSCRIPTION + separator + Variables.PT_SUBSCRIPTION_NONE;
    static final String ptSubGA = Variables.PT_SUBSCRIPTION + separator + Variables.GA;
    static final String ptSubVA = Variables.PT_SUBSCRIPTION + separator + Variables.VA;
    static final String ptSubHTA = Variables.PT_SUBSCRIPTION + separator + Variables.HTA;
    static final String carNone = carAvailable0 + separator + ptSubNone;
    static final String carGa = carAvailable0 + separator + ptSubGA;
    static final String carHTA = carAvailable0 + separator + ptSubVA;
    static final String carVA = carAvailable0 + separator + ptSubHTA;
    static final String nocarNone = carAvailable1 + separator + ptSubNone;
    static final String nocarGa = carAvailable1 + separator + ptSubGA;
    static final String nocarHTA = carAvailable1 + separator + ptSubVA;
    static final String nocarVA = carAvailable1 + separator + ptSubHTA;
    static final String notInEducation = Variables.CURRENT_EDUCATION + separator + Variables.NOT_IN_EDUCATION;
    static final String primary = Variables.CURRENT_EDUCATION + separator + Variables.PRIMRAY;
    static final String secondary = Variables.CURRENT_EDUCATION + separator + Variables.SECONDARY;
    static final String student = Variables.CURRENT_EDUCATION + separator + Variables.STUDENT;
    static final String employment0 = Variables.LEVEL_OF_EMPLOYMENT_CAT + separator + Variables.LEVEL_OF_EMPLOYMENT_CAT_NONE;
    static final String employment39 = Variables.LEVEL_OF_EMPLOYMENT_CAT + separator + Variables.LEVEL_OF_EMPLOYMENT_CAT_01_to_39;
    static final String employment79 = Variables.LEVEL_OF_EMPLOYMENT_CAT + separator + Variables.LEVEL_OF_EMPLOYMENT_CAT_40_to_79;
    static final String employment100 = Variables.LEVEL_OF_EMPLOYMENT_CAT + separator + Variables.LEVEL_OF_EMPLOYMENT_CAT_80_to_100;
    static final String ageCat17 = Variables.AGE_CATEGORIE + separator + Variables.AGE_CATEGORIE_0_17;
    static final String ageCat24 = Variables.AGE_CATEGORIE + separator + Variables.AGE_CATEGORIE_18_24;
    static final String ageCat44 = Variables.AGE_CATEGORIE + separator + Variables.AGE_CATEGORIE_25_44;
    static final String ageCat64 = Variables.AGE_CATEGORIE + separator + Variables.AGE_CATEGORIE_45_64;
    static final String ageCat74 = Variables.AGE_CATEGORIE + separator + Variables.AGE_CATEGORIE_65_74;
    static final String ageCatXX = Variables.AGE_CATEGORIE + separator + Variables.AGE_CATEGORIE_75_XX;
    static final String toActType = "to_act_type";
    static final String home = toActType + separator + SBBActivities.home;
    static final String cbhome = toActType + separator + SBBActivities.cbHome;
    static final String leisure = toActType + separator + SBBActivities.leisure;
    static final String other = toActType + separator + SBBActivities.other;
    static final String freight = toActType + separator + SBBActivities.freight;
    static final String business = toActType + separator + SBBActivities.business;
    static final String shopping = toActType + separator + SBBActivities.shopping;
    static final String work = toActType + separator + SBBActivities.work;
    static final String education = toActType + separator + SBBActivities.education;
    static final String exogeneous = toActType + separator + SBBActivities.exogeneous;
    static final String accompany = toActType + separator + SBBActivities.accompany;
    static final String mode = "mode";
    static final String walk = mode + separator + SBBModes.WALK_FOR_ANALYSIS;
    static final String ride = mode + separator + SBBModes.RIDE;
    static final String car = mode + separator + SBBModes.CAR;
    static final String pt = mode + separator + SBBModes.PT;
    static final String bike = mode + separator + SBBModes.BIKE;
    static final String avtaxi = mode + separator + SBBModes.AVTAXI;
    static final String drt = mode + separator + SBBModes.DRT;
    static final int timeSplit = 15 * 60;
    static final int travelTimeSplit = 10 * 60;
    static final int lastTravelTimeValue = 5 * 60 * 60;
    static final List<Integer> distanceClassesValue = List.of(0, 2, 4, 6, 8, 10, 15, 20, 25, 30, 40, 50, 100, 150, 200, 300);
    static final List<String> distanceClassesLable = List.of("0", "0-2", "2-4", "4-6", "6-8", "8-10", "10-15", "15-20", "20-25", "25-30", "30-40", "40-50", "50-100", "100-150", "150-200", "200-300");
    static final List<String> modesMS = List.of(walk, ride, car, pt, bike, avtaxi, drt);
    static final List<String> carAvailable = List.of(carAvailable1, carAvailable0);
    static final List<String> ptSubscription = List.of(ptSubNone, ptSubGA, ptSubVA, ptSubHTA);
    static final List<String> carAndPt = List.of(carNone, carGa, carHTA, carVA, nocarNone, nocarGa, nocarHTA, nocarVA);
    static final List<String> educationType = List.of(notInEducation, primary, secondary, student);
    static final List<String> employmentRate = List.of(employment0, employment39, employment79, employment100);
    static final List<String> ageCategorie = List.of(ageCat17, ageCat24, ageCat44, ageCat64, ageCat74, ageCatXX);
    static final List<String> toActTypeList = List.of(home, cbhome, leisure, other, freight, business, shopping, work, education, exogeneous, accompany);
    static final List<List<String>> varList = List.of(carAvailable, ptSubscription, carAndPt, educationType, employmentRate, ageCategorie, toActTypeList);
    static final List<List<String>> varTimeList = List.of(modesMS, toActTypeList);

    private MSVariables() {
    }

}
