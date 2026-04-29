package ch.sbb.matsim.plans.stats;

import ch.sbb.matsim.zones.Zone;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.sbb.matsim.plans.stats.LookupFormulaParser.parseFormula;

public class LookupAttributeHandler {
    private static final Pattern ATTR_WITH_SLASH = Pattern.compile("([A-Za-z0-9_\\-]+(?:/[A-Za-z0-9_\\-]+)+)");

    public Map<String, Map<String, String>> lookupVariables = new HashMap<>();

    // Liefert Liste aller vorkommenden Attribute, die ein '/' enthalten (ohne Duplikate, in Fundreihenfolge)
    public Map<String, String> findAttributesWithSlash(String expression) {
        Map<String, String> attributes = new HashMap<>();
        if (expression == null) { return attributes; }
        Matcher m = ATTR_WITH_SLASH.matcher(expression);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            String attr = m.group(1);
            if (!found.contains(attr)) {
                String shortAttribute = mapAttribute(attr);
                if (!shortAttribute.equals(attr)) {
                    found.add(attr);
                    attributes.put(attr, shortAttribute);
                }
            }
        }
        return attributes;
    }

    // Ersetzt alle Vorkommen, indem die gegebene Funktion auf jeden gefundenen Attributnamen angewendet wird.
    public String replaceAttributes(String expression) {
        Map<String, String> attributesWithSlash = findAttributesWithSlash(expression);
        for (String attribute : attributesWithSlash.keySet()) {
            expression = expression.replace(attribute, attributesWithSlash.get(attribute));
        }
        return expression;
    }

    public static String mapAttribute(String attribute) {
        return attribute.replace('/', '_');
    }


    public void addLookupVariable(String table, String attribute, String attributeShort) {
        Map<String, String> lookupVariablesForTable = lookupVariables.get(table);
        if (lookupVariablesForTable == null) {
            lookupVariablesForTable = new HashMap<>();
            lookupVariables.put(table, lookupVariablesForTable);
        }
        lookupVariablesForTable.put(attribute, attributeShort);
    }
    public void createLookUpVariables(ReadReportJson.ReportConfig reportConfig) {
        // Zuerst alle Lookup-Variablen identifizieren, in den Formeln ersetzen und in Persons und Zones setzen
        // in Trips erst beim erzeugen des TripAttributes-Objekts setzen
        String table;
        for (ReadReportJson.Sheet s : reportConfig.sheets) {
            for (ReadReportJson.ReportVariable variable : s.variables) {
                Map<String, String> attributeMap = findAttributesWithSlash(variable.formula);
                variable.formula = replaceAttributes(variable.formula);
                LookupFormulaParser.ParseResult parseResult = parseFormula(variable.formula);
                table = s.table_ref;
                if (parseResult.lookupTable != null) {
                    table = parseResult.lookupTable;
                }
                for (String attribute : attributeMap.keySet()) {
                    addLookupVariable(table, attribute, attributeMap.get(attribute));
                }
            }
            for (ReadReportJson.ReportGroup group : s.groups) {
                Map<String, String> attributeMap = findAttributesWithSlash(group.filter_expression);
                for (String attribute : attributeMap.keySet()) {
                    addLookupVariable(s.table_ref, attribute, attributeMap.get(attribute));
                }
                group.filter_expression = replaceAttributes(group.filter_expression);

                attributeMap = findAttributesWithSlash(group.filter_expression_person);
                for (String attribute : attributeMap.keySet()) {
                    addLookupVariable("Person", attribute, attributeMap.get(attribute));
                }
                group.filter_expression_person = replaceAttributes(group.filter_expression_person);
            }
        }
    }

    public void setLookupAttributes(Collection<? extends Person> persons, List<Zone> zones){
        if (lookupVariables.keySet().contains("Persons")) {
            persons.stream().forEach(person -> {
                for (String attr : lookupVariables.get("Persons").keySet()) {
                    person.getAttributes().putAttribute(lookupVariables.get("Persons").get(attr), getFieldValue(person, attr, Person.class));
                }
            });
        }
        if (lookupVariables.keySet().contains("Zones")) {
            zones.stream().forEach(zone -> {
                for (String attr : lookupVariables.get("Zones").keySet()) {
                    zone.getAttributes().putAttribute(lookupVariables.get("Zones").get(attr), getFieldValue(zone, attr, Zone.class));
                }
            });
        }
    }

    public Map<String, Object> getLookupAttributesForTrip(Attributable obj) {
        Map<String, Object> lookupAttributes = new HashMap<>();
        if (lookupVariables.keySet().contains("Trips")) {
            for (String attr : lookupVariables.get("Trips").keySet()) {
                lookupAttributes.put(lookupVariables.get("Trips").get(attr), getFieldValue(obj, attr, obj.getClass()));
            }
        }
        return lookupAttributes;
    }

    public static Object getFieldObject(Attributable obj, String variable, Class<? extends Attributable> clazz) {
        String capitalized = variable.substring(0, 1).toUpperCase() + variable.substring(1);
        String[] methodNames = {"get" + capitalized, "is" + capitalized, variable};
        Object ob = null;
        for (String mName : methodNames) {
            try {
                Method m = clazz.getMethod(mName);
                ob = m.invoke(obj);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ignored) {
                // keep looking
            }
        }
        return ob;
    }

    public static Double getFieldValue(Attributable obj, String variable, Class<? extends Attributable> clazz) {
        String regex = "[//]";
        String[] parts = variable.split(regex);
        Object ob = null;
        for (int i=0; i < parts.length; i++) {
            String part = parts[i];
            ob = getFieldObject(obj, part, clazz);
            if (i==parts.length-1) {
                if (ob instanceof Number) return ((Number) ob).doubleValue();
            }
            if (ob == null) return null;
            obj = (Attributable) ob;
            clazz = (Class<? extends Attributable>) obj.getClass();
        }
        return null;
    }
}
