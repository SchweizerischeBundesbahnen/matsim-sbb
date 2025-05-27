package ch.sbb.matsim.plans.models.resolver;

import org.matsim.utils.objectattributes.attributable.Attributable;

public interface AttributeResolver {
    Object resolve(Attributable root, String attributePath);
}
