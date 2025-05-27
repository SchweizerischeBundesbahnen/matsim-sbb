package ch.sbb.matsim.plans.models.utilitycomponents;

import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.List;

public class UtilityCalculator {

    private final List<UtilityComponent> components;

    public UtilityCalculator(List<UtilityComponent> components) {
        this.components = components;
    }

    public double calculateUtility(Attributable attributes) {
        return components.stream()
                .mapToDouble(component -> component.calculate(attributes))
                .sum();
    }
}
