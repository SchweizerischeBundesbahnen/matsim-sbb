package ch.sbb.matsim.plans.models.resolver;

import org.matsim.utils.objectattributes.attributable.Attributable;

/**
 * Decorates another resolver and scales numeric values by a constant factor.
 */
public class ScaledAttributeResolver implements AttributeResolver {

    private final AttributeResolver delegate;
    private final double factor;

    public ScaledAttributeResolver(AttributeResolver delegate, double factor) {
        this.delegate = delegate;
        this.factor = factor;
    }

    @Override
    public Object resolve(Attributable root, String attributePath) {
        Object rawValue = delegate.resolve(root, attributePath);
        if (rawValue == null || factor == 1.0) {
            return rawValue;
        }
        if (rawValue instanceof Number number) {
            return number.doubleValue() * factor;
        }
        throw new IllegalArgumentException("Cannot scale non-numeric attribute '" + attributePath + "': " + rawValue);
    }
}

