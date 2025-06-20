package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.plans.models.selector.ChoiceSelector;
import ch.sbb.matsim.plans.models.selector.LogitSelector;
import ch.sbb.matsim.plans.models.utilitycomponents.*;
import ch.sbb.matsim.plans.synpop.SynpopConstants;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DrivingLicenseModel implements ChoiceModel {
    private final UtilityCalculator hasDrivingLicenseCalculator;
    private final UtilityCalculator noDrivingLicenseCalculator;
    private final ChoiceSelector selector;


    public DrivingLicenseModel() {
        List<UtilityComponent> utilityComponentsHasLicense = new java.util.ArrayList<>();
        utilityComponentsHasLicense.add(new ConstantComponent(-8.43067994216887));
        //Bern shadow utility
        utilityComponentsHasLicense.add(new ConstantComponent(-0.5805659299290205));

        utilityComponentsHasLicense.add(new PiecewiseComponent(SynpopConstants.AGE, new double[]{22, 26, 69, 89}, new double[]{0.398353560040895, 0.0270923010714936, 0.0200244008308715, -0.118033381290315, -0.189461164263502}));
        utilityComponentsHasLicense.add(new LinearComponent(SynpopConstants.IS_SWISS, 0.635113367488652));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition(SynpopConstants.LANGUAGE, "==", "2", 0.156660708026873));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition(SynpopConstants.LEVEL_OF_EMPLOYMENT, ">=", "90", 1.53373469463505));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCombinedCondition(List.of(SynpopConstants.LEVEL_OF_EMPLOYMENT), List.of("<", ">"),
                List.of("90", "0"),
                0.822138563820571));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition(SynpopConstants.CURRENT_EDU, "==", "4", 0.0481));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("household.couple_type", "==", "1", 0.57060133563417));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("household.couple_type", "==", "2", 0.550927139150936));
        utilityComponentsHasLicense.add(new BoxCoxComponent("zone.acc_pt_sc", -0.891463554538977, 0.1774));
        utilityComponentsHasLicense.add(new BoxCoxComponent("zone.acc_mul_sc", 0.661152797863397, 0.1448));
        utilityComponentsHasLicense.add(new LinearComponent("zone.pc_car", -0.784943541308737));
        utilityComponentsHasLicense.add(new LogarithmicComponent("zone.pc_car", 0.373113360753188));
        utilityComponentsHasLicense.add(UtilityComponentFactory.fromCondition("zone.pc_car", "==", "0.0", -0.597932611178397));
        this.hasDrivingLicenseCalculator = new UtilityCalculator(
                utilityComponentsHasLicense);
        List<UtilityComponent> utilityComponentsHasNoLicense = Collections.emptyList();
        //all betas are zero, so no need to add any components
        this.noDrivingLicenseCalculator = new UtilityCalculator(utilityComponentsHasNoLicense);
        this.selector = new LogitSelector(Map.of("true", hasDrivingLicenseCalculator, "false", noDrivingLicenseCalculator), 1.0, getClass().getName());
    }

    public Boolean calculate(Attributable person) {
        return Boolean.parseBoolean(selector.select(person));
    }


}
