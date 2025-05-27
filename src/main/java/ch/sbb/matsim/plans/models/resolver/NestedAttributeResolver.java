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
            } else {
                try {
                    current = current.getClass()
                            .getMethod("get" + capitalize(part))
                            .invoke(current);
                } catch (Exception e) {
                    throw new RuntimeException("Cannot resolve attribute: " + part, e);
                }
            }

            if (current == null) return null;
        }
        return current;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
