package ch.sbb.matsim.plans.models.resolver;

import ch.sbb.matsim.zones.Zone;
import org.matsim.utils.objectattributes.attributable.Attributable;

/**
 * A resolver for nested attributes in an Attributable object. This works well if all objects are attributable, e.g.,
 * person->household->attributes, or if the objects have getter methods for their attributes. Special handling for Zones,
 * letting them implement attributable is too awkward.
 */
public class NestedAttributeResolver implements AttributeResolver {

    @Override
    public Object resolve(Attributable root, String attributePath) {
        String[] parts = attributePath.split("\\.");
        Object current = root;

        for (String part : parts) {
            if (current instanceof Attributable a) {
                current = a.getAttributes().getAttribute(part);
            } else if (current instanceof Zone zone) {
                current = zone.getAttribute(part);
            }

            if (current == null) return null;
        }
        return current;
    }

}
