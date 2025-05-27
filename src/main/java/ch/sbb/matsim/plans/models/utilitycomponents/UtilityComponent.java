package ch.sbb.matsim.plans.models.utilitycomponents;

import org.matsim.utils.objectattributes.attributable.Attributable;

public interface UtilityComponent {
    double calculate(Attributable attributable);
}