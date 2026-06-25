package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

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
        switch (val) {
            case null -> {
                return defaultValue;
            }
            case Number number -> {
                return number.doubleValue();
            }
            case String s -> {
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException ignored) {
                    // Fall through to return defaultValue
                }
            }
            default -> {
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
        switch (val) {
            case null -> {
                return defaultValue;
            }
            case Number number -> {
                return number.intValue();
            }
            case String s -> {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                }
            }
            default -> {
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
        Attributes attributes = attributable.getAttributes();
        return hasSomething(attributeName, attributes);
    }

    public static boolean hasSomething(String attributeName, Attributes attributes) {
        Object val = attributes.getAttribute(attributeName);
        switch (val) {
            case null -> {
                return false;
            }
            case Boolean b -> {
                return b;
            }
            case Number number -> {
                return number.intValue() == 1;
            }
            case String s -> {
                if ("1".equals(s)) {
                    return true;
                } else if ("0".equals(s)) {
                    return false;
                } else {
                    return Boolean.parseBoolean(s);
                }
            }
            default -> {
            }
        }
        return false;
    }
}