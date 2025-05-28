package ch.sbb.matsim.plans.models.utilitycomponents;

import org.matsim.utils.objectattributes.attributable.Attributable;

public class ConstantComponent implements UtilityComponent {

    private final double constant;

    public ConstantComponent(double constant) {
        this.constant = constant;
    }

    @Override
    public double calculate(Attributable attributable) {
        return constant;
    }
}
