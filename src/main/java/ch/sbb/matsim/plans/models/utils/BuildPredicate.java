package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.plans.models.steps.StatsWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class BuildPredicate {
    private static final Logger logger = LogManager.getLogger(BuildPredicate.class);
    private static final char quote = '\'';
    // your regex, for example: (\w+)\s*(<=|>=|<|>|=)\s*([\d.]+)
    private static final java.util.regex.Pattern RULE_PATTERN = java.util.regex.Pattern.compile("(\\w+)\\s*(<=|>=|<|>|=|!=)\\s*('?)([\\w?.]+)('?)");

    // Reflection caches keyed by (class, attributeName). We cache both successful Method lookups
    // and a "no-method" sentinel to avoid repeated NoSuchMethodException throwing in the hot path.
    private static final Method NO_METHOD;
    private static final Field NO_FIELD;
    static {
        Method m;
        Field f;
        try {
            m = BuildPredicate.class.getDeclaredMethod("noMethodSentinel");
            f = BuildPredicate.class.getDeclaredField("NO_METHOD");
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        NO_METHOD = m;
        NO_FIELD = f;
    }
    @SuppressWarnings("unused")
    private static void noMethodSentinel() {}

    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();


    public static <T extends Attributable> Predicate<T> buildPredicate(String expr, Class<T> clazz) {
        if (expr == null || expr.isEmpty()) return t -> true;

        Predicate<T> combined = null;
        String[] conditionsOr  = expr.split("\\|");
        for (String condOr : conditionsOr) {
            String[] conditionsAnd = condOr.split("&");
            Predicate<T> combinedOr = null;
            for (String condAnd : conditionsAnd) {

                if (combinedOr == null) {
                    combinedOr = getCondition(condAnd, clazz);
                } else {
                    combinedOr = combinedOr.and(getCondition(condAnd, clazz));
                }
            }
            if (combined == null) {
                combined = combinedOr;
            } else {
                combined = combined.or(combinedOr);
            }
        }
        return combined;
    }

    private static <T extends Attributable> Predicate<T> getCondition(String cond, Class<T> clazz) {
        cond = cond.trim();

        Matcher m = RULE_PATTERN.matcher(cond);
        if (m.matches()) {
            String attrName = m.group(1);
            String operator = m.group(2);
            Boolean isString = m.group(3).equals("'") & m.group(5).equals("'");
            String right = m.group(4);
            Predicate<T> attCondition;
            switch (right){
                case "true": {
                    attCondition = createBooleanAttributeCondition(attrName, true, clazz);
                    break; }
                case "false": {
                    attCondition = createBooleanAttributeCondition(attrName, false, clazz);
                    break; }
                default: {
                    try {
                        double targetValue = Double.parseDouble(right);
                        attCondition = createAttributeCondition(attrName, operator, targetValue, clazz);
                    } catch (Exception e) {
                        String targetValue = right.toString();
                        if (isString){
                            attCondition = createStringAttributeCondition(attrName, operator, targetValue, clazz);
                        } else {
                            attCondition = createCompAttributesCondition(attrName, operator, targetValue, clazz);
                        }
                    }

                }
            }
            return attCondition;
            
        } else {
            logger.warn("Could not parse rule condition: '{}'. This condition will be ignored.", cond);
        }
        return null;
    }


    private static <T extends Attributable> Predicate<T> createStringAttributeCondition(String attrName, String operator, String targetValue, Class<T> clazz) {
        return t -> {
            String attrVal = getAttributeValueAsString(t, attrName, clazz);
            if (attrVal == null) return false;

            switch (operator) {
                case "=":
                case "==": return Objects.equals(attrVal, targetValue);
                case "!=": return !Objects.equals(attrVal, targetValue);
                default:
                    logger.warn("Unknown operator '{}' in condition on '{}'", operator, attrName);
                    return false;
            }
        };
    }

    private static <T extends Attributable> Predicate<T> createCompAttributesCondition(String attr1Name, String operator, String attr2Name, Class<T> clazz) {
        return t -> {
            String attr1Val = getAttributeValueAsString(t, attr1Name, clazz);
            String attr2Val = getAttributeValueAsString(t, attr2Name, clazz);
            if ((attr1Val == null)|(attr2Val == null)) return false;

            switch (operator) {
                case "=":
                case "==": return Objects.equals(attr1Val, attr2Val);
                case "!=": return !Objects.equals(attr1Val, attr2Val);
                default:
                    logger.warn("Unknown operator '{}' in condition on '{} and {}", operator, attr1Name, attr2Name);
                    return false;
            }
        };
    }

    private static <T extends Attributable> Predicate<T> createBooleanAttributeCondition(String attrName, Boolean targetValue, Class<T> clazz) {
        return t -> {
            return Objects.equals(getAttributeValueAsBoolean(t, attrName, clazz), targetValue);
        };
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
        Object o = getAttributeValue(obj, attrName, clazz);
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o != null) return Double.parseDouble(o.toString());
        return null;
    }

    private static <T extends Attributable> Boolean getAttributeValueAsBoolean(T obj, String attrName, Class<T> clazz) {
        Object o = getAttributeValue(obj, attrName, clazz);
        if (o.toString().equals("true")) return true;
        if (o.toString().equals("false")) return false;
        return null;
    }

    private static <T extends Attributable> String getAttributeValueAsString(T obj, String attrName, Class<T> clazz) {
        Object o = getAttributeValue(obj, attrName, clazz);
        if (o != null) return o.toString();
        return null;
    }

    private static <T extends Attributable> Object getAttributeValue(T obj, String attrName, Class<T> clazz) {
        // Fast path: cached Method lookup
        ConcurrentHashMap<String, Method> methodMap = METHOD_CACHE.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
        Method cachedMethod = methodMap.get(attrName);
        if (cachedMethod == null) {
            String capitalized = attrName.substring(0, 1).toUpperCase() + attrName.substring(1);
            String[] methodNames = {"get" + capitalized, "is" + capitalized, attrName};
            Method found = null;
            for (String mName : methodNames) {
                try {
                    found = clazz.getMethod(mName);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // keep looking
                }
            }
            cachedMethod = (found != null) ? found : NO_METHOD;
            methodMap.put(attrName, cachedMethod);
        }
        if (cachedMethod != NO_METHOD) {
            try {
                return cachedMethod.invoke(obj);
            } catch (Exception ex) {
                logger.warn("Error invoking '{}' on {}: {}", cachedMethod.getName(), clazz.getName(), ex.toString());
            }
        }

        // Fast path: cached Field lookup
        ConcurrentHashMap<String, Field> fieldMap = FIELD_CACHE.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
        Field cachedField = fieldMap.get(attrName);
        if (cachedField == null) {
            Field found = null;
            try {
                found = clazz.getDeclaredField(attrName);
                found.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
                // not found
            }
            cachedField = (found != null) ? found : NO_FIELD;
            fieldMap.put(attrName, cachedField);
        }
        if (cachedField != NO_FIELD) {
            try {
                return cachedField.get(obj);
            } catch (IllegalAccessException ignored) {
                // fall through
            } catch (Exception ex) {
                logger.warn("Error reading attribute '{}' on {}: {}", attrName, clazz.getName(), ex.toString());
            }
        }

        try {
            return obj.getAttributes().getAttribute(attrName);
        } catch (Exception ex) {
            logger.warn("Error reading attribute '{}' on {}: {}", attrName, clazz.getName(), ex.toString());
        }
        return null;
    }

    public void main() {
        BuildPredicate.buildPredicate("zone_id<700000000", Person.class);
        BuildPredicate.buildPredicate("[originActivity/type]='shopping' & [destinationActivity/type]='home' | [destinationActivity/type]='shopping'".replace("/","_").replace("[", "").replace("]",""), Person.class);
        BuildPredicate.buildPredicate("[originActivity/type]=[destinationActivity/type]".replace("/","_").replace("[", "").replace("]",""), Person.class);
        BuildPredicate.buildPredicate("[originActivity/type]=1.254".replace("/","_").replace("[", "").replace("]",""), Person.class);
    }
}