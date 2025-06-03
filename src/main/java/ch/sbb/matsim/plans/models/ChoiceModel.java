package ch.sbb.matsim.plans.models;

import org.matsim.utils.objectattributes.attributable.Attributable;

public interface ChoiceModel {

    boolean calculate(Attributable attributable);
}
