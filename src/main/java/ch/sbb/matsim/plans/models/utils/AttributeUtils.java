package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.SynpopConstants;
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

    public static boolean hasEbike45(Attributable attributable) {
        return hasSomething(attributable, SynpopConstants.HAS_EBIKE45);
    }
    public static boolean hasDrivingLicense(Attributable attributable) {
        return hasSomething(attributable, SynpopConstants.HAS_DRIVING_LICENSE);
    }
    public static boolean hasCarAvailable(Attributable attributable) {
        return hasSomething(attributable, SynpopConstants.CAR_AVAILABLE);
    }

    public static boolean hasSomething(Attributable attributable, String attributeName) {
        Object val = attributable.getAttributes().getAttribute(attributeName);
        if (val == null) {
            return false;
        }
        if (val instanceof Boolean b) {
            return b.booleanValue(); }
        if (val instanceof Number) {
            return ((Number) val).intValue() != 0;
        }
        if (val instanceof String) {
            if ("1".equals(val.toString())) {
                return true;
            }
            else if ("0".equals(val.toString())) {
                return false; }
            else {
                return Boolean.parseBoolean(val.toString()); }
        }
        return false;
    }
}