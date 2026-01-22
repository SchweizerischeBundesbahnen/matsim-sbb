package ch.sbb.matsim.plans.models.steps;

import org.matsim.utils.objectattributes.attributable.Attributable;

public interface ChoiceModel {

    Object calculate(Attributable attributable);
}
