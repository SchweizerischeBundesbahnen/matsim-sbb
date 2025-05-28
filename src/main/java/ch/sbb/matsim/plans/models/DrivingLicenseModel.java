package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.utilitycomponents.*;
import ch.sbb.matsim.plans.synpop.SynpopConstants;
import org.matsim.core.gbl.MatsimRandom;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DrivingLicenseModel {
    private final UtilityCalculator hasDrivingLicenseCalculator;
    private final UtilityCalculator noDrivingLicenseCalculator;
    private final ChoiceSelector selector;


    public DrivingLicenseModel() {
        List<UtilityComponent> utilityComponentsHasLicense = new java.util.ArrayList<>();
        utilityComponentsHasLicense.add(new PiecewiseCalculator(SynpopConstants.AGE, new double[]{22, 26, 69, 89}, new double[]{0.398, 0.0301, 0.0201, -0.118, -0.19}));
        utilityComponentsHasLicense.add(new LinearCalculator(SynpopConstants.IS_SWISS, 0.635));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition(SynpopConstants.LANGUAGE, "==", "2", 0.156));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition(SynpopConstants.LEVEL_OF_EMPLOYMENT, ">=", "90", 1.54));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCombinedCondition(SynpopConstants.LEVEL_OF_EMPLOYMENT, List.of("<", ">"),
                List.of("90", "0"),
                0.827));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition(SynpopConstants.CURRENT_EDU, "==", "4", 0.0481));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("household.couple_type", "==", "1", 0.57));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("household.couple_type", "==", "2", 0.551));
        utilityComponentsHasLicense.add(new BoxCoxCalculator("zone.acc_pt_sc", -0.892, 0.1774));
        utilityComponentsHasLicense.add(new BoxCoxCalculator("zone.acc_mul_sc", 0.662, 0.1448));
        utilityComponentsHasLicense.add(new LinearCalculator("zone.pc_car", -0.785));
        utilityComponentsHasLicense.add(new LogarithmicCalculator("zone.pc_car", 0.373));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("zone.pc_car", "==", "0.0", -0.598));
        this.hasDrivingLicenseCalculator = new UtilityCalculator(
                utilityComponentsHasLicense);
        List<UtilityComponent> utilityComponentsHasNoLicense = Collections.emptyList();
        //all betas are zero, so no need to add any components
        this.noDrivingLicenseCalculator = new UtilityCalculator(utilityComponentsHasNoLicense);
        this.selector = new LogitSelector(Map.of("true", hasDrivingLicenseCalculator, "false", noDrivingLicenseCalculator), 1.0, MatsimRandom.getRandom());
    }

    public boolean calculate(org.matsim.api.core.v01.population.Person person) {
        return Boolean.parseBoolean(selector.select(person));
    }


}
