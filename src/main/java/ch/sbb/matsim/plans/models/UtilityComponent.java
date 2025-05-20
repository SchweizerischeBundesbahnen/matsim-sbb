package ch.sbb.matsim.plans.models;

import org.matsim.utils.objectattributes.attributable.Attributable;

interface UtilityComponent {
    double calculate(Attributable attributable);
}