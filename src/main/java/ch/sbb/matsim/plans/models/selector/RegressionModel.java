package ch.sbb.matsim.plans.models.selector;

import org.matsim.utils.objectattributes.attributable.Attributable;

public interface RegressionModel {

    double calculate(Attributable attributable);
}
