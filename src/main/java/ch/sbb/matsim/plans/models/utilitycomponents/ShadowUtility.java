package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Map;

public class ShadowUtility implements UtilityComponent {
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
        return shadowUtilities.get(aggregate);


    }


}
