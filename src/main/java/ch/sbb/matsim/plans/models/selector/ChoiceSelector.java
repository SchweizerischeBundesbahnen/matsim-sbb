package ch.sbb.matsim.plans.models.selector;

import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Map;

public interface ChoiceSelector {

    /**
     * returns the selected alternative based on the attributes provided.
     */
    String select(Attributable attributes);

    /**
     * Returns the probabilities of each alternative based on the attributes provided.
     */
    Map<String, Double> getProbabilities(Attributable attributes);
}
