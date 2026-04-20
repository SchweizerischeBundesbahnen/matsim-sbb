package ch.sbb.matsim.plans.models.utils;

import org.matsim.utils.objectattributes.attributable.Attributable;

public final class AttributeUtils {

    // Private constructor to prevent instantiation of utility class
    private AttributeUtils() {}

    /**
     * Safely extracts a double value from any MATSim Attributable object (Zone, Person, Facility, etc.).
     * Handles Numbers, Strings, and missing values gracefully.
     *
     * @param attributable The MATSim object (e.g., zone, person, facility)
     * @param attributeName The name of the attribute
     * @param defaultValue The value to return if the attribute is missing or malformed
     * @return The parsed double, or the default value
     */
    public static double getAsDouble(Attributable attributable, String attributeName, double defaultValue) {
        if (attributable == null || attributable.getAttributes() == null) {
            return defaultValue;
        }

        Object val = attributable.getAttributes().getAttribute(attributeName);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        if (val instanceof String) {
            try {
                return Double.parseDouble((String) val);
            } catch (NumberFormatException ignored) {
                // Fall through to return defaultValue
            }
        }
        return defaultValue;
    }
    
    /**
     * Safely extracts an integer value from any MATSim Attributable object.
     */
    public static int getAsInteger(Attributable attributable, String attributeName, int defaultValue) {
        if (attributable == null || attributable.getAttributes() == null) {
            return defaultValue;
        }

        Object val = attributable.getAttributes().getAttribute(attributeName);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}