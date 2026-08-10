package ch.sbb.matsim.plans.constants;


import java.util.List;

public final class ZonalAttributes {
    private ZonalAttributes() {
    }

    public static final String ACCSIB_CAR = "accsib_car";
    public static final String ACCSIB_MUL = "accsib_mul";
    public static final String ACCSIB_PT = "accsib_pt";
    public static final String ACC_MUL_SC = "acc_mul_sc";


    public static final String AGGLO_ID = "agglo_id";
    public static final String AGGLO_NAME = "agglo_name";
    public static final String AMGR_ID = "amgr_id";
    public static final String AMGR_NAME = "amgr_name";
    public static final String AMR_ID = "amr_id";
    public static final String AMR_NAME = "amr_name";
    public static final String AREA_LAND = "area_land";
    public static final String AT_CAR = "at_car";
    public static final String AT_RIDE = "at_ride";
    public static final String AT_AVTAXI = "at_avtaxi";
    public static final String AT_AVFEED = "at_avfeed";
    public static final String AVFEED_DT = "avfeed_dt";
//    public static final String CAR_TTIMES = "car_ttimes";
    public static final String DENSITY = "density";
    public static final String FTE_ENDO = "fte_endo";
    public static final String FTE_TOTAL = "fte_total";
    public static final String JOBS_ENDO = "jobs_endo";
    public static final String JOBS_EXO = "jobs_exo";
    public static final String JOBS_TOTAL = "jobs_total";
    public static final String KT_ID = "kt_id";
    public static final String KT_NAME = "kt_name";
    public static final String MSR_ID = "msr_id";
    public static final String MSR_NAME = "msr_name";
    public static final String MUN_ID = "mun_id";
    public static final String MUN_NAME = "mun_name";
    public static final String NUTS2 = "nuts2";
    public static final String NUTS3 = "nuts3";
    public static final String PC_CAR = "pc_car";
    public static final String PC_RIDE = "pc_ride";
    public static final String POP_0017 = "pop_0017";
    public static final String POP_1824 = "pop_1824";
    public static final String POP_2544 = "pop_2544";
    public static final String POP_4564 = "pop_4564";
    public static final String POP_6574 = "pop_6574";
    public static final String POP_75XX = "pop_75xx";
    public static final String POP_APPR = "pop_appr";
    public static final String POP_CARAVL = "pop_caravl";
    public static final String POP_CARS = "pop_cars";
    public static final String POP_DL = "pop_dl";
    public static final String POP_EMPL = "pop_empl";
    public static final String POP_EBIKE45 = "pop_ebike45";
    public static final String POP_GA = "pop_ga";
    public static final String POP_HT = "pop_ht";
    public static final String POP_PUP_1 = "pop_pup_1";
    public static final String POP_PUP_2 = "pop_pup_2";
    public static final String POP_STUD_3 = "pop_stud_3";
    public static final String POP_TOTAL = "pop_total";
    public static final String POP_VA = "pop_va";
    public static final String POP_VA_HT = "pop_va_ht";
//    public static final String PT_FREQNCY = "pt_freqncy";
//    public static final String PT_TRSFERS = "pt_trsfers";
//    public static final String PT_TTIMES = "pt_ttimes";
    public static final String SCHL_APPR = "schl_appr";
    public static final String SCHL_ENR_1 = "schl_enr_1";
    public static final String SCHL_ENR_2 = "schl_enr_2";
    public static final String SCHL_ENR_3 = "schl_enr_3";
    public static final String SL3_ID = "sl3_id";
    public static final String SL3_NAME = "sl3_name";
    public static final String VISIT_L = "visit_L";
    public static final String VISIT_S = "visit_S";
    public static final String VISIT_S_LT = "visit_S_lt";
    public static final String VISIT_S_ST = "visit_S_st";
    public static final String ZONE_ID = "zone_id";
    public static final String ZONE_INDEX = "zone_index";
    public static final String ZONE_NAME = "zone_name";

    public static final String ATTR_A = "Attr_A";
    public static final String ATTR_B = "Attr_B";
    public static final String ATTR_O = "Attr_O";
    public static final String ATTR_EC = "Attr_EC";
    public static final String ATTR_L = "Attr_L";
    public static final String ATTR_S = "Attr_S";


    public static final String MOTORISATION_LEVEL = "motorisation_level" ;
    public static final String LANGUAGE = "language";

    public static List<String> attributesForExport = List.of(
            ZONE_ID,
            ATTR_A,
            ATTR_B,
            ATTR_EC,
            ATTR_L,
            ATTR_O,
            ATTR_S,
            ACCSIB_CAR,
            ACCSIB_MUL,
            ACCSIB_PT,
            AGGLO_ID,
            AGGLO_NAME,
            AMGR_ID,
            AMGR_NAME,
            AMR_ID,
            AMR_NAME,
            AREA_LAND,
            AT_CAR,
            AT_RIDE,
            AT_AVFEED,
            AT_AVTAXI,
            AVFEED_DT,
            DENSITY,
            FTE_ENDO,
            FTE_TOTAL,
            JOBS_ENDO,
            JOBS_EXO,
            JOBS_TOTAL,
            KT_ID,
            KT_NAME,
            LANGUAGE,
            MOTORISATION_LEVEL,
            MSR_ID,
            MSR_NAME,
            MUN_ID,
            MUN_NAME,
            NUTS2,
            NUTS3,
            PC_CAR,
            PC_RIDE,
            POP_0017,
            POP_1824,
            POP_2544,
            POP_4564,
            POP_6574,
            POP_75XX,
            POP_APPR,
            POP_CARAVL,
            POP_CARS,
            POP_DL,
            POP_EBIKE45,
            POP_EMPL,
            POP_GA,
            POP_HT,
            POP_PUP_1,
            POP_PUP_2,
            POP_STUD_3,
            POP_TOTAL,
            POP_VA,
            POP_VA_HT,
            SCHL_APPR,
            SCHL_ENR_1,
            SCHL_ENR_2,
            SCHL_ENR_3,
            SL3_ID,
            SL3_NAME,
            VISIT_L,
            VISIT_S,
            VISIT_S_LT,
            VISIT_S_ST,
            ZONE_INDEX,
            ZONE_NAME
    );
}
