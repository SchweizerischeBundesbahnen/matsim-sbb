package ch.sbb.matsim.plans.synpop;

import ch.sbb.matsim.plans.constants.ZonalAttributes;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum DecisionStrata {
    // Enum constants based on your unique DSTRAT list
    PRIMLOC_EMPL_NOABO_CAR("PrimLoc_Empl_NoAbo_Car"),
    PRIMLOC_STUD_SEC_MOBTOOL("PrimLoc_Stud_Sec_MobTool"),
    PRIMLOC_STUD_APPR_MOBTOOL("PrimLoc_Stud_Appr_MobTool"),
    PRIMLOC_STUD_TERT_MOBTOOL("PrimLoc_Stud_Tert_MobTool"),
    PRIMLOC_STUD_TERT_NOMOBTOOL("PrimLoc_Stud_Tert_NoMobTool"),
    PRIMLOC_EMPL_GA_CAR("PrimLoc_Empl_GA_Car"),
    PRIMLOC_STUD_PRIM("PrimLoc_Stud_Prim"),
    PRIMLOC_EMPL_VA_NOCAR("PrimLoc_Empl_VA_NoCar"),
    PRIMLOC_EMPL_NOABO_NOCAR("PrimLoc_Empl_NoAbo_NoCar"),
    PRIMLOC_EMPL_GA_NOCAR("PrimLoc_Empl_GA_NoCar"),
    PRIMLOC_STUD_SEC_NOMOBTOOL("PrimLoc_Stud_Sec_NoMobTool"),
    PRIMLOC_EMPL_VA_CAR("PrimLoc_Empl_VA_Car"),
    PRIMLOC_STUD_APPR_NOMOBTOOL("PrimLoc_Stud_Appr_NoMobTool"),
    SECDEST_AGE_1865_NOABO_NOCAR("SecDest_Age_1865_NoAbo_NoCar"),
    SECDEST_AGE_1865_ABO_NOCAR("SecDest_Age_1865_Abo_NoCar"),
    SECDEST_AGE_O75("SecDest_Age_o75"),
    SECDEST_AGE_U18("SecDest_Age_u18"),
    SECDEST_AGE_1865_ABO_CAR("SecDest_Age_1865_Abo_Car"),
    SECDEST_AGE_6575("SecDest_Age_6575"),
    SECDEST_AGE_1865_NOABO_CAR("SecDest_Age_1865_NoAbo_Car"),
    PRIMLOC_EMPL_NOABO_CAR_GG("PrimLoc_Empl_NoAbo_Car_gg"),
    PRIMLOC_EMPL_GA_CAR_GG("PrimLoc_Empl_GA_Car_gg"),
    PRIMLOC_EMPL_VA_NOCAR_GG("PrimLoc_Empl_VA_NoCar_gg"),
    PRIMLOC_EMPL_NOABO_NOCAR_GG("PrimLoc_Empl_NoAbo_NoCar_gg"),
    PRIMLOC_EMPL_GA_NOCAR_GG("PrimLoc_Empl_GA_NoCar_gg"),
    PRIMLOC_EMPL_VA_CAR_GG("PrimLoc_Empl_VA_Car_gg"),
    ACCSIB_CAR(ZonalAttributes.ACCSIB_CAR),
    ACCSIB_PT(ZonalAttributes.ACCSIB_PT),
    ACCSIB_MUL(ZonalAttributes.ACCSIB_MUL),;

    // A static map for efficient lookup from a string
    private static final Map<String, DecisionStrata> NAME_MAP =
            Arrays.stream(DecisionStrata.values())
                    .collect(Collectors.toMap(DecisionStrata::getStrataName, Function.identity()));
    private final String strataName;

    DecisionStrata(String strataName) {
        this.strataName = strataName;
    }

    public static Set<DecisionStrata> getSecondaryDestinationStratas() {
        return Set.of(
                SECDEST_AGE_1865_NOABO_NOCAR,
                SECDEST_AGE_1865_ABO_NOCAR,
                SECDEST_AGE_O75,
                SECDEST_AGE_U18,
                SECDEST_AGE_1865_ABO_CAR,
                SECDEST_AGE_6575,
                SECDEST_AGE_1865_NOABO_CAR
        );
    }
    /**
     * @param name The exact string from your data file.
     * @return The corresponding DecisionStrata enum.
     * @throws IllegalArgumentException if the string does not match any known strata.
     */
    public static DecisionStrata fromStrataName(String name) {
        DecisionStrata result = NAME_MAP.get(name);
        if (result == null) {
            throw new IllegalArgumentException("No DecisionStrata enum constant for name: " + name);
        }
        return result;
    }

    /**
     * @return The original string representation of the strata name (e.g., "PrimLoc_Empl_NoAbo_Car").
     */
    public String getStrataName() {
        return strataName;
    }
}