package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import java.util.List;

public final class MSVariables {

    static final String carAvailable1 = Variables.CAR_AVAIL + "_1";
    static final String carAvailable0 = Variables.CAR_AVAIL + "_0";

    static final String all = "all";
    static final String ptSubNone = Variables.PT_SUBSCRIPTION + "_" + Variables.PT_SUBSCRIPTION_NONE;
    static final String ptSubGA = Variables.PT_SUBSCRIPTION + "_" + Variables.GA;
    static final String ptSubVA = Variables.PT_SUBSCRIPTION + "_" + Variables.VA;
    static final String ptSubHTA = Variables.PT_SUBSCRIPTION + "_" + Variables.HTA;

    static final String carNone = carAvailable0 + "_" + ptSubNone;
    static final String carGa = carAvailable0 + "_" + ptSubGA;
    static final String carHTA = carAvailable0 + "_" + ptSubVA;
    static final String carVA = carAvailable0 + "_" + ptSubHTA;
    static final String nocarNone = carAvailable1 + "_" + ptSubNone;
    static final String nocarGa = carAvailable1 + "_" + ptSubGA;
    static final String nocarHTA = carAvailable1 + "_" + ptSubVA;
    static final String nocarVA = carAvailable1 + "_" + ptSubHTA;

    static final String notInEducation = Variables.CURRENT_EDUCATION + "_" + Variables.NOT_IN_EDUCATION;
    static final String primary = Variables.CURRENT_EDUCATION + "_" + Variables.PRIMRAY;
    static final String secondary = Variables.CURRENT_EDUCATION + "_" + Variables.SECONDARY;
    static final String student = Variables.CURRENT_EDUCATION + "_" + Variables.STUDENT;

    static final String employment0 = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + Variables.LEVEL_OF_EMPLOYMENT_CAT_NONE;
    static final String employment39 = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + Variables.LEVEL_OF_EMPLOYMENT_CAT_01_to_39;
    static final String employment79 = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + Variables.LEVEL_OF_EMPLOYMENT_CAT_40_to_79;
    static final String employment100 = Variables.LEVEL_OF_EMPLOYMENT_CAT + "_" + Variables.LEVEL_OF_EMPLOYMENT_CAT_80_to_100;

    static final String ageCat17 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_0_17;
    static final String ageCat24 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_18_24;
    static final String ageCat44 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_25_44;
    static final String ageCat64 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_45_64;
    static final String ageCat74 = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_65_74;
    static final String ageCatXX = Variables.AGE_CATEGORIE + "_" + Variables.AGE_CATEGORIE_75_XX;

    static final String toActType = "to_act_type";
    static final String home = toActType + "_" + SBBActivities.home;
    static final String cbhome = toActType + "_" + SBBActivities.cbHome;
    static final String leisure = toActType + "_" + SBBActivities.leisure;
    static final String other = toActType + "_" + SBBActivities.other;
    static final String freight = toActType + "_" + SBBActivities.freight;
    static final String business = toActType + "_" + SBBActivities.business;
    static final String shopping = toActType + "_" + SBBActivities.shopping;
    static final String work = toActType + "_" + SBBActivities.work;
    static final String education = toActType + "_" + SBBActivities.education;
    static final String exogeneous = toActType + "_" + SBBActivities.exogeneous;
    static final String accompany = toActType + "_" + SBBActivities.accompany;
    static final String mode = "mode";

    static final String walk = mode + "_" + SBBModes.WALK_FOR_ANALYSIS;
    static final String ride = mode + "_" + SBBModes.RIDE;
    static final String car = mode + "_" + SBBModes.CAR;
    static final String pt = mode + "_" + SBBModes.PT;
    static final String bike = mode + "_" + SBBModes.BIKE;
    static final String avtaxi = mode + "_" + SBBModes.AVTAXI;
    static final String drt = mode + "_" + SBBModes.DRT;

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
