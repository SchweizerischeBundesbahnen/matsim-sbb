package ch.sbb.matsim.plans.models.utilitycomponents;

import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.ArrayList;
import java.util.List;

public class UtilityCalculator {

    private final List<UtilityComponent> components;

    public UtilityCalculator(List<UtilityComponent> components) {
        this.components = components;
    }

    public UtilityCalculator() {
        this.components = new ArrayList<>();
    }

    public void addComponent(UtilityComponent component) {
        components.add(component);
    }

    public List<UtilityComponent> getComponents() {
        return components;
    }

    public double calculate(Attributable attributes) {
        return components.stream()
                .mapToDouble(component -> component.calculate(attributes))
                .sum();
    }
}
