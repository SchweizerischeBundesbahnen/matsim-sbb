package ch.sbb.matsim.plans.synpop;

import java.util.Arrays;
import java.util.List;

public class SynpopConstants {
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
    public static final String HAS_DRIVING_LICENSE = "has_driving_license";
    public static final String ANALYSIS_SUBPOPULATION = "analysis_subpopulation";
    public static final String IS_EMPLOYED = "is_employed";
    public static final String LOE_GROUP = "loe_group";
    public static final String CURRENT_EDU = "current_edu";
    public static final String CURRENT_JOB_RANK = "current_job_rank";
    public static final String IS_APPRENTICE = "is_apprentice";
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
    public static final String NET_DIST_SCHOOL_PTSUBSCR = "net_dist_school_ptsubscr";
    public static final String NET_DIST_SCHOOL = "net_dist_school";


    public static final List<String> RAW_SYNPOP_ATTRIBUTES = Arrays.asList(
            PERSON_ID, HOUSEHOLD_ID, POSITION_IN_BUS, HIGHEST_EDUCATION, POSITION_IN_EDU,
            NATION, LANGUAGE, LEVEL_OF_EMPLOYMENT, AGE, IS_SWISS,
            ANALYSIS_SUBPOPULATION, IS_EMPLOYED, LOE_GROUP, CURRENT_EDU,
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
}
