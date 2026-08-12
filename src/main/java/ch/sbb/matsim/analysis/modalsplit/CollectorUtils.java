package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.Variables;
import org.matsim.utils.objectattributes.attributable.Attributes;

/** Shared static helpers used by multiple collectors. */
final class CollectorUtils {

    private CollectorUtils() {}

    /** Returns the analysis-subpopulation string for a person's attributes. */
    static String getSubpopulation(Attributes attributes) {
        Object analysisSubpopulation = attributes.getAttribute(Variables.ANALYSIS_SUBPOPULATION);
        if (analysisSubpopulation != null) return analysisSubpopulation.toString();
        return attributes.getAttribute(Variables.SUBPOPULATION).toString();
    }
}

