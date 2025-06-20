package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Map;

public class ShadowUtility implements UtilityComponent {
    private static final Logger log = LogManager.getLogger(ShadowUtility.class);
    private final AttributeResolver resolver;
    private final String attributePath;
    Map<String, Double> shadowUtilities;

    public ShadowUtility(String attributePath, Map<String, Double> shadowUtilities, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.shadowUtilities = shadowUtilities;
        this.resolver = resolver;
    }

    public ShadowUtility(String attributePath, Map<String, Double> shadowUtilities) {
        this(attributePath, shadowUtilities, new NestedAttributeResolver());
    }

    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        String aggregate = attribute.toString();
        double result = shadowUtilities.get(aggregate);
//        log.debug("ShadowUtility: attributePath=" + attributePath +" aggregate zone "+aggregate+ ", attribute=" + attribute + ", value=" + result);
        return result;
    }
}
