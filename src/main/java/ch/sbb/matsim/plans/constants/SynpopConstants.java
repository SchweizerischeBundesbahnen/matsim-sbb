package ch.sbb.matsim.plans.constants;

import ch.sbb.matsim.config.variables.Variables;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class SynpopConstants {
    private SynpopConstants() {
    }

    public static final String PERSON_ID = "person_id";
    public static final String HOUSEHOLD_ID = "household_id";
    public static final String HOUSEHOLD = "household";
    public static final String POSITION_IN_BUS = "position_in_bus";
    public static final String HIGHEST_EDUCATION = "highest_education";
    public static final String POSITION_IN_EDU = "position_in_edu";
    public static final String NATION = "nation";
    public static final String LANGUAGE = "language";
    public static final String LEVEL_OF_EMPLOYMENT = "level_of_employment";
    public static final String AGE = "age";
    public static final String IS_SWISS = "is_swiss";
    public static final String HAS_DRIVING_LICENSE = Variables.HAS_DRIVING_LICENSE;
    public static final String ANALYSIS_SUBPOPULATION = "analysis_subpopulation";
    public static final String IS_EMPLOYED = "is_employed";
    public static final String LOE_GROUP = "loe_group";
    public static final String CURRENT_EDU = "current_edu";
    public static final String CURRENT_JOB_RANK = "current_job_rank";
    public static final String IS_APPRENTICE = "is_apprentice";
    public static final String HAS_EBIKE45 = Variables.HAS_EBIKE_45;
    public static final String CAR_AVAILABLE = Variables.CAR_AVAIL;
    public static final String NO_WORK_TOURS = "no_work_tours";
    public static final String NO_EDUCATION_TOURS = "no_education_tours";
    public static final String NO_PRIM_TOURS= "no_prim_tours";
    public static final String NO_BUSINESS_TOURS = "no_business_tours";
    public static final String IS_WORKING_FROM_HOME = "is_working_from_home";
    public static final String HAS_FLEXIBLE_WORK_HOURS = "has_flexible_work_hours";
    public static final String NO_OTHER_TOURS = "no_other_tours";
    public static final String NO_TOT_TOURS = "no_tot_tours";
    public static final String SCHOOL_ZONE = "school_zone";
    public static final String WORK_ZONE = "work_zone";
    public static final String ORIGINAL_WORK_ZONE = "original_work_zone";
    public static final String ACTIVE_IN_MODEL = "active_in_model";
    public static final String RESIDENCE_AMR_ID = "residence_amr_id";
    public static final String RESIDENCE_ZONE_ID = "residence_zone_id";
    public static final String AGE_CAT = "age_cat";
    public static final String XCOORD = "xcoord";
    public static final String YCOORD = "ycoord";
    public static final String COORD = "coord";
    public static final String ZONE_ID = "zone_id";
    public static final String ZONE = "zone";
    public static final String BUSINESS_ID = "business_id";
    public static final String JOBS_ENDO = "jobs_endo";
    public static final String JOBS_EXO = "jobs_exo";
    public static final String FTE_EXO = "fte_exo";
    public static final String FTE_ENDO = "fte_endo";
    public static final String SCHOOL_TYPE = "school_type";
    public static final String NOGA_CODE = "noga_code";
    public static final String SECTOR = "sector";
    public static final String RELATIVE_ATTRACTION = "relative_attraction";
    public static final String NET_DIST_SCHOOL = "network_distance_school";
    public static final String NET_DIST_WORK = "network_distance_work";
    public static final String LEVEL_OF_EMPLOYMENT_CAT = "level_of_employment_cat";
    public static final String PEDESTRIAN_SPEED = "pedestrian_speed";
    public static final String PT_TRAVEL_TIME_SCHOOL = "pt_travel_time_school";
    public static final String PT_ACCESS_TIME_SCHOOL = "pt_access_time_school";
    public static final String PT_TRANSFERS_SCHOOL = "pt_transfers_school";
    public static final String PT_TRAVEL_TIME_WORK = "pt_travel_time_work";
    public static final String PT_ACCESS_TIME_WORK = "pt_access_time_work";
    public static final String PT_TRANSFERS_WORK = "pt_transfers_work";


    public static final List<String> RAW_SYNPOP_ATTRIBUTES = Arrays.asList(
            PERSON_ID, HOUSEHOLD_ID, POSITION_IN_BUS, HIGHEST_EDUCATION, POSITION_IN_EDU,
            NATION, LANGUAGE, LEVEL_OF_EMPLOYMENT, AGE, IS_SWISS,
            ANALYSIS_SUBPOPULATION, IS_EMPLOYED, CURRENT_EDU,
            CURRENT_JOB_RANK, IS_APPRENTICE);
    public static final List<String> RAW_BUSINESSES_ATTRIBUTES = Arrays.asList(
            ZONE_ID, JOBS_ENDO, FTE_ENDO, JOBS_EXO, FTE_EXO,
            SCHOOL_TYPE, XCOORD, YCOORD, NOGA_CODE, SECTOR);
    public static final List<String> RAW_HOUSEHOLD_ATTRIBUTES = Arrays.asList(
            HOUSEHOLD_ID, ZONE_ID, XCOORD, YCOORD);


    public static final String NB_OF_KIDS = "nb_of_kids";
    public static final String NB_OF_ADULTS = "nb_of_adults";
    public static final String HAS_CHILD_IN_HH = "has_child_in_hh";
    public static final String COUPLE_TYPE = "couple_type";
    public static final String SINGLE_PARENT = "single_parent";
    public static final String NB_OF_DRIVING_LICENSES = "nb_of_driving_licenses";
    public static final String NB_OF_DRIVING_LICENSES_PER_ADULT = "nb_of_driving_licenses_per_adult";
    public static final String SIZE = "size";
    public static final String NB_OF_CARS = "nb_of_cars";
    public static final String NB_OF_CARS_PER_ADULT = "nb_of_cars_per_adult";
    // ----------------------------
    // Age category
    // ----------------------------
    public static final Map<Integer, String> AGE_CATEGORIES = Map.of(
            1, "0_to_17",
            2, "18_to_24",
            3, "25_to_44",
            4, "45_to_64",
            5, "65_to_74",
            6, "75_to_XX"
    );
    public static final Map<String, Integer> AGE_CATEGORY_REVERSE = Map.of(
            "0_to_17", 1,
            "18_to_24", 2,
            "25_to_44", 3,
            "45_to_64", 4,
            "65_to_74", 5,
            "75_to_XX", 6
    );
    // ----------------------------
    // Language
    // ----------------------------
    public static final Map<Integer, String> LANGUAGE_CATEGORIES = Map.of(
            1, "german",
            2, "french",
            3, "italian",
            4, "romansh",
            5, "other"
    );
    public static final Map<String, Integer> LANGUAGE_REVERSE = Map.of(
            "german", 1,
            "french", 2,
            "italian", 3,
            "romansh", 4,
            "other", 5
    );
    // ----------------------------
    // Level of employment category
    // ----------------------------
    public static final Map<Integer, String> LEVEL_OF_EMPLOYMENT_CAT_CATEGORIES = Map.of(
            0, "0",
            1, "1_to_39",
            2, "40_to_79",
            3, "80_to_100"
    );
    public static final Map<Integer, Integer> LEVEL_OF_EMPLOYMENT_CAT_BOUNDS = Map.of(
            0, 0,
            20, 1,
            40, 2,
            80, 3
    );
    // ----------------------------
    // Highest education
    // ----------------------------
    public static final Map<Integer, String> HIGHEST_EDUCATION_CATEGORIES = Map.of(
            1, "primary",
            2, "secondary",
            3, "tertiary_college",
            4, "tertiary_university"
    );
    public static final Map<String, Integer> HIGHEST_EDUCATION_REVERSE = Map.of(
            "primary", 1,
            "secondary", 2,
            "tertiary_college", 3,
            "tertiary_university", 4,
            "tertiary",4 //für Prognose
    );
    public static final Map<String, Integer> SECTORS_REVERSE = Map.ofEntries(
            Map.entry("agriculture", 1),
            Map.entry("production", 2),
            Map.entry("wholesale", 3),
            Map.entry("retail", 4),
            Map.entry("gastronomy", 5),
            Map.entry("finance", 6),
            Map.entry("services fC", 7),
            Map.entry("other services", 8),
            Map.entry("other", 9),
            Map.entry("non movers", 10),
            Map.entry("null", 99)
    );

    // ----------------------------
    // Current education
    // ----------------------------
    public static final Map<Integer, String> CURRENT_EDU_CATEGORIES = Map.of(
            0, "null",
            1, "kindergarten",
            2, "pupil_primary",
            3, "pupil_secondary",
            4, "student",
            5, "apprentice"
    );
    public static final Map<String, Integer> CURRENT_EDU_REVERSE = Map.of(
            "null", 0,
            "kindergarten", 1,
            "pupil_primary", 2,
            "pupil_secondary", 3,
            "student", 4,
            "apprentice", 5
    );
    // ----------------------------
    // Current job rank
    // ----------------------------
    public static final Map<Integer, String> CURRENT_JOB_RANK_CATEGORIES = Map.of(
            0, "null",
            1, "apprentice",
            2, "employee",
            3, "management"
    );
    public static final Map<String, Integer> CURRENT_JOB_RANK_REVERSE = Map.of(
            "null", 0,
            "apprentice", 1,
            "employee", 2,
            "management", 3
    );
    public static final String TOURS = "tours";
    public static final String WORK_FACILITY = "work_facility";
    public static final String ORIGINAL_WORK_FACILITY = "original_work_facility";
    public static final String EDUCATION_FACILITY = "education_facility";
    public static final String FACILITY = "facility";

    public static Integer getCodeOrFail(Map<String, Integer> reverseMap, String label, String categoryName) {
        Integer code = reverseMap.get(label);
        if (code != null)
            return code;
        else if (reverseMap.containsValue(Integer.parseInt(label)))
            return Integer.parseInt(label);
        else
            throw new IllegalArgumentException("Unknown value for category '" + categoryName + "': " + label);
    }

}
