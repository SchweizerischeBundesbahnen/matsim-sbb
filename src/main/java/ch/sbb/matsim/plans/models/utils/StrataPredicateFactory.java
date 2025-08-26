package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import org.matsim.api.core.v01.population.Person;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class StrataPredicateFactory {

    private final Map<DecisionStrata, Predicate<Person>> predicateMap = new ConcurrentHashMap<>();

    public StrataPredicateFactory() {
        initializePredicates();
    }

    private void initializePredicates() {


        Predicate<Person> isEmployed = p -> ((Number) p.getAttributes().getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT)).intValue() > 0;

        Predicate<Person> hasGA = person -> person.getAttributes().getAttribute(Variables.PT_SUBSCRIPTION).equals(Variables.PTSubscriptionType.GA.getValue());
        Predicate<Person> hasVA = person -> person.getAttributes().getAttribute(Variables.PT_SUBSCRIPTION).equals(Variables.PTSubscriptionType.VA.getValue());
        Predicate<Person> hasAbo = hasGA.or(hasVA);
        Predicate<Person> hasNoAbo = Predicate.not(hasAbo);
        Predicate<Person> hasCar = p -> p.getAttributes().getAttribute(SynpopConstants.CAR_AVAILABLE) != null && (Boolean) p.getAttributes().getAttribute(SynpopConstants.CAR_AVAILABLE);
        Predicate<Person> hasMobilityTool = hasCar.or(hasAbo); // Key reusable concept
        Predicate<Person> hasNoMobilityTool = Predicate.not(hasMobilityTool);

        Predicate<Person> isCrossBorderCommuter = person -> person.getAttributes().getAttribute(Variables.ANALYSIS_SUBPOPULATION).equals(Variables.CB_COMMUTER);
        Predicate<Person> isNotCrossBorderCommuter = Predicate.not(isCrossBorderCommuter);
        Predicate<Person> isStudentPrimary = person -> (Integer) person.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU) == 2;
        Predicate<Person> isStudentSecondary = person -> (Integer) person.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU) == 3;
        Predicate<Person> isStudentApprentice = person -> (Integer) person.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU) == 5;
        Predicate<Person> isStudentTertiary = person -> (Integer) person.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU) == 4;

        Predicate<Person> isAgeUnder18 = person -> (Integer) person.getAttributes().getAttribute(SynpopConstants.AGE) < 18;
        Predicate<Person> isAgeUnder65 = person -> (Integer) person.getAttributes().getAttribute(SynpopConstants.AGE) < 65;
        Predicate<Person> isAgeUnder75 = person -> (Integer) person.getAttributes().getAttribute(SynpopConstants.AGE) < 75;
        Predicate<Person> isAgeOver75 = Predicate.not(isAgeUnder75);
        Predicate<Person> isAge18to65 = Predicate.not(isAgeUnder18).and(isAgeUnder65);
        Predicate<Person> isAge65to75 = Predicate.not(isAgeUnder65).and(isAgeUnder75);

        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_NOABO_CAR, isNotCrossBorderCommuter.and(isEmployed).and(hasCar).and(hasNoAbo));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_GA_CAR, isNotCrossBorderCommuter.and(isEmployed).and(hasCar).and(hasGA));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_VA_CAR, isNotCrossBorderCommuter.and(isEmployed).and(hasCar).and(hasVA));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_NOABO_NOCAR, isNotCrossBorderCommuter.and(isEmployed).and(Predicate.not(hasCar)).and(hasNoAbo));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_GA_NOCAR, isNotCrossBorderCommuter.and(isEmployed).and(Predicate.not(hasCar)).and(hasGA));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_VA_NOCAR, isNotCrossBorderCommuter.and(isEmployed).and(Predicate.not(hasCar)).and(hasVA));

        predicateMap.put(DecisionStrata.PRIMLOC_STUD_PRIM, isNotCrossBorderCommuter.and(isStudentPrimary));
        predicateMap.put(DecisionStrata.PRIMLOC_STUD_SEC_MOBTOOL, isNotCrossBorderCommuter.and(isStudentSecondary).and(hasMobilityTool));
        predicateMap.put(DecisionStrata.PRIMLOC_STUD_SEC_NOMOBTOOL, isNotCrossBorderCommuter.and(isStudentSecondary).and(hasNoMobilityTool));
        predicateMap.put(DecisionStrata.PRIMLOC_STUD_APPR_MOBTOOL, isNotCrossBorderCommuter.and(isStudentApprentice).and(hasMobilityTool));
        predicateMap.put(DecisionStrata.PRIMLOC_STUD_APPR_NOMOBTOOL, isNotCrossBorderCommuter.and(isStudentApprentice).and(hasNoMobilityTool));
        predicateMap.put(DecisionStrata.PRIMLOC_STUD_TERT_MOBTOOL, isNotCrossBorderCommuter.and(isStudentTertiary).and(hasMobilityTool));
        predicateMap.put(DecisionStrata.PRIMLOC_STUD_TERT_NOMOBTOOL, isNotCrossBorderCommuter.and(isStudentTertiary).and(hasNoMobilityTool));

        // -- PrimLoc Strata (Cross-Border Commuters - "_gg" suffix) --
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_NOABO_CAR_GG, isCrossBorderCommuter.and(isEmployed).and(hasCar).and(hasNoAbo));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_GA_CAR_GG, isCrossBorderCommuter.and(isEmployed).and(hasCar).and(hasGA));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_VA_CAR_GG, isCrossBorderCommuter.and(isEmployed).and(hasCar).and(hasVA));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_NOABO_NOCAR_GG, isCrossBorderCommuter.and(isEmployed).and(Predicate.not(hasCar)).and(hasNoAbo));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_GA_NOCAR_GG, isCrossBorderCommuter.and(isEmployed).and(Predicate.not(hasCar)).and(hasGA));
        predicateMap.put(DecisionStrata.PRIMLOC_EMPL_VA_NOCAR_GG, isCrossBorderCommuter.and(isEmployed).and(Predicate.not(hasCar)).and(hasVA));

        // -- SecDest Strata (Based on Age) --
        predicateMap.put(DecisionStrata.SECDEST_AGE_U18, isAgeUnder18);
        predicateMap.put(DecisionStrata.SECDEST_AGE_1865_NOABO_NOCAR, isAge18to65.and(Predicate.not(hasCar)).and(hasNoAbo));
        predicateMap.put(DecisionStrata.SECDEST_AGE_1865_ABO_NOCAR, isAge18to65.and(Predicate.not(hasCar)).and(hasAbo));
        predicateMap.put(DecisionStrata.SECDEST_AGE_1865_NOABO_CAR, isAge18to65.and(hasCar).and(hasNoAbo));
        predicateMap.put(DecisionStrata.SECDEST_AGE_1865_ABO_CAR, isAge18to65.and(hasCar).and(hasAbo));
        predicateMap.put(DecisionStrata.SECDEST_AGE_6575, isAge65to75);
        predicateMap.put(DecisionStrata.SECDEST_AGE_O75, isAgeOver75);

        System.out.println("Initialized " + predicateMap.size() + " strata predicates.");
    }

    /**
     * Retrieves the filtering predicate for a given Strata enum.
     * This method is now 100% type-safe.
     *
     * @param strata The enum constant representing the desired Strata.
     * @return The corresponding Predicate<Person>.
     */
    public Predicate<Person> getPredicate(DecisionStrata strata) {
        Predicate<Person> predicate = predicateMap.get(strata);
        // The check is still good practice, though less likely to be needed
        if (predicate == null) {
            throw new IllegalStateException("No predicate implementation for Strata: " + strata);
        }
        return predicate;
    }
}