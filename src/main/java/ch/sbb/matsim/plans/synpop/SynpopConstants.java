package ch.sbb.matsim.plans.synpop;

import java.util.Arrays;
import java.util.List;

public class SynpopConstants {
    public static final String PERSON_ID = "person_id";
    public static final String HOUSEHOLD_ID = "household_id";
    public static final String POSITION_IN_BUS = "position_in_bus";
    public static final String HIGHEST_EDUCATION = "highest_education";
    public static final String POSITION_IN_EDU = "position_in_edu";
    public static final String NATION = "nation";
    public static final String LANGUAGE = "language";
    public static final String LEVEL_OF_EMPLOYMENT = "level_of_employment";
    public static final String AGE = "age";
    public static final String IS_SWISS = "is_swiss";
    public static final String ANALYSIS_SUBPOPULATION = "analysis_subpopulation";
    public static final String IS_EMPLOYED = "is_employed";
    public static final String LOE_GROUP = "loe_group";
    public static final String CURRENT_EDU = "current_edu";
    public static final String CURRENT_JOB_RANK = "current_job_rank";
    public static final String IS_APPRENTICE = "is_apprentice";

    public static final List<String> RAW_SYNPOP_ATTRIBUTES = Arrays.asList(
            PERSON_ID, HOUSEHOLD_ID, POSITION_IN_BUS, HIGHEST_EDUCATION, POSITION_IN_EDU,
            NATION, LANGUAGE, LEVEL_OF_EMPLOYMENT, AGE, IS_SWISS,
            ANALYSIS_SUBPOPULATION, IS_EMPLOYED, LOE_GROUP, CURRENT_EDU,
            CURRENT_JOB_RANK, IS_APPRENTICE);


}
