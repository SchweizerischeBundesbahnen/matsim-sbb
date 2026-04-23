package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.plans.models.steps.RailaccessChoiceSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class BuildPredicate {
    private static final Logger logger = LogManager.getLogger(RailaccessChoiceSet.class);
    // your regex, for example: (\w+)\s*(<=|>=|<|>|=)\s*([\d.]+)
    private static final java.util.regex.Pattern RULE_PATTERN = java.util.regex.Pattern.compile("(\\w+)\\s*(<=|>=|<|>|=)\\s*([\\d.]+)");

    public static <T extends Attributable> Predicate<T> buildPredicate(String expr, Class<T> clazz) {
        if (expr == null || expr.isEmpty()) return t -> true;

        Predicate<T> combined = t -> true;
        String[] conditions = expr.split(" & ");

        for (String cond : conditions) {
            cond = cond.trim();

            Matcher m = RULE_PATTERN.matcher(cond);
            if (m.matches()) {
                String attrName = m.group(1);
                String operator = m.group(2);
                double targetValue = Double.parseDouble(m.group(3));

                combined = combined.and(createAttributeCondition(attrName, operator, targetValue, clazz));
            } else {
                logger.warn("Could not parse rule condition: '{}'. This condition will be ignored.", cond);
            }
        }
        return combined;
    }

    private static <T extends Attributable> Predicate<T> createAttributeCondition(String attrName, String operator, double targetValue, Class<T> clazz) {
        return t -> {
            Double attrVal = getAttributeValueAsDouble(t, attrName, clazz);
            if (attrVal == null) return false;

            switch (operator) {
                case "<": return attrVal < targetValue;
                case "<=": return attrVal <= targetValue;
                case ">": return attrVal > targetValue;
                case ">=": return attrVal >= targetValue;
                case "=":
                case "==": return Double.compare(attrVal, targetValue) == 0;
                default:
                    logger.warn("Unknown operator '{}' in condition on '{}'", operator, attrName);
                    return false;
            }
        };
    }

    private static <T extends Attributable> Double getAttributeValueAsDouble(T obj, String attrName, Class<T> clazz) {
        try {
            // Try getter methods: getX or isX
            String capitalized = attrName.substring(0, 1).toUpperCase() + attrName.substring(1);
            String[] methodNames = {"get" + capitalized, "is" + capitalized, attrName};
            for (String mName : methodNames) {
                try {
                    Method m = clazz.getMethod(mName);
                    Object val = m.invoke(obj);
                    if (val instanceof Number) return ((Number) val).doubleValue();
                    if (val != null) return Double.parseDouble(val.toString());
                } catch (NoSuchMethodException ignored) {
                    // keep looking
                }
            }

            // Try direct field access
            Field f = clazz.getDeclaredField(attrName);
            f.setAccessible(true);
            Object val = f.get(obj);
            if (val instanceof Number) return ((Number) val).doubleValue();
            if (val != null) return Double.parseDouble(val.toString());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // attribute not found or inaccessible
        } catch (NumberFormatException nfe) {
            logger.warn("Attribute '{}' exists but is not numeric for class {}: {}", attrName, clazz.getName(), nfe.getMessage());
        } catch (Exception ex) {
            logger.warn("Error reading attribute '{}' on {}: {}", attrName, clazz.getName(), ex.toString());
        }
        try {
            Object val = obj.getAttributes().getAttribute(attrName);
            if (val instanceof Number) return ((Number) val).doubleValue();
            if (val != null) return Double.parseDouble(val.toString());
        } catch (Exception ex) {
            logger.warn("Error reading attribute '{}' on {}: {}", attrName, clazz.getName(), ex.toString());
        }
        return null;
    }
}