package ch.sbb.matsim.plans.stats;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LookupFormulaParser {

    public static class ParseResult {
        public final String aggregator;         // z.B. "Sum" oder null
        public final String lookupTable;        // z.B. "Trips" oder null
        public final String lookupField;        // z.B. "weight" oder "OriginActivity" or null
        public final String innerExpression;    // innerer Inhalt der Aggregation oder die ganze Formel, falls keine Aggregation
        public final String filterExpression;   // falls vorhanden (nach dem top-level Komma)
        public final List<String> attributes;   // alle [..] tokens (inkl. Slashes)
        public ParseResult(String aggregator, String lookupTable, String lookupField, String innerExpression, String filterExpression, List<String> attributes) {
            this.aggregator = aggregator;
            this.lookupTable = lookupTable;
            this.lookupField = lookupField;
            this.innerExpression = innerExpression;
            this.filterExpression = filterExpression;
            this.attributes = attributes;
        }
        @Override
        public String toString() {
            return "ParseResult{" +
                    "aggregator='" + aggregator + '\'' +
                    ", lookupTable='" + lookupTable + '\'' +
                    ", lookupField='" + lookupField + '\'' +
                    ", innerExpression='" + innerExpression + '\'' +
                    ", filterExpression='" + filterExpression + '\'' +
                    ", attributes=" + attributes +
                    '}';
        }
    }

    // finds top-level aggregator like Name(inner)
    private static Pattern AGG_PATTERN = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*$", Pattern.DOTALL);

    // finds tokens in square brackets, e.g. [fromActivity/Type]
    private static Pattern BRACKET_ATTR = Pattern.compile("\\[([^\\]]+)\\]");

    public static ParseResult parseFormula(String formula) {
        if (formula == null) throw new IllegalArgumentException("formula is null");
        String trimmed = formula.trim();

        String aggregator = null;
        String inner = trimmed;

        // 1) detect aggregator at top-level: Name(something)
        Matcher mAgg = AGG_PATTERN.matcher(trimmed);
        if (mAgg.matches()) {
            aggregator = mAgg.group(1);
            inner = mAgg.group(2).trim();
        }

        // 2) split inner into (lookup/expression) and optional filter part by top-level comma
        // We need to find a comma that is not inside brackets [] or parentheses ()
        int commaIndex = indexOfTopLevelComma(inner);
        String leftPart;
        String filterPart = null;
        if (commaIndex >= 0) {
            leftPart = inner.substring(0, commaIndex).trim();
            filterPart = inner.substring(commaIndex + 1).trim();
        } else {
            leftPart = inner.trim();
        }

        // 3) attempt to extract lookup: pattern "Table->Field" where Field may be [name] or bare token
        String lookupTable = null;
        String lookupField = null;

        // look for -> at top-level (again not inside brackets)
        int arrowIndex = indexOfTopLevelArrow(leftPart);
        if (arrowIndex >= 0) {
            lookupTable = leftPart.substring(0, arrowIndex).trim();
            lookupField = leftPart.substring(arrowIndex + 2).trim();
            // strip surrounding brackets for lookupField if present
            if (lookupField.startsWith("[") && lookupField.endsWith("]") && lookupField.length() >= 2) {
                lookupField = lookupField.substring(1, lookupField.length() - 1);
            }
        }

        // 4) collect all attributes in square brackets (could be in leftPart or filterPart or anywhere)
        List<String> attrs = new ArrayList<>();
        Matcher mAttr = BRACKET_ATTR.matcher(trimmed); // search whole formula to capture all occurrences
        while (mAttr.find()) {
            String attr = mAttr.group(1);
            if (!attrs.contains(attr)) attrs.add(attr);
        }

        return new ParseResult(aggregator, lookupTable, lookupField, inner, filterPart, attrs);
    }

    // finds comma at top level (not inside [], (), or quotes)
    private static int indexOfTopLevelComma(String s) {
        int levelParen = 0;
        int levelBracket = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
            else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') levelParen++;
                else if (c == ')') if (levelParen > 0) levelParen--;
                else if (c == '[') levelBracket++;
                else if (c == ']') if (levelBracket > 0) levelBracket--;
                else if (c == ',' && levelParen == 0 && levelBracket == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    // find top-level "->" (not within brackets or quotes)
    private static int indexOfTopLevelArrow(String s) {
        int levelParen = 0;
        int levelBracket = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
            else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') levelParen++;
                else if (c == ')') if (levelParen > 0) levelParen--;
                else if (c == '[') levelBracket++;
                else if (c == ']') if (levelBracket > 0) levelBracket--;
                else if (c == '-' && s.charAt(i + 1) == '>' && levelParen == 0 && levelBracket == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    // small test
    public static void main(String[] args) {
        List<String> inputs = List.of(
                "Sum(Trips->[weight], [fromActivity/Type]='work'&[toActivity/type]='home' | [fromActivity/Type]='home'&[toActivity/Type]='work')",
                "Count(Trips->OriginActivity)",
                "100 * [pop_ga]",
                "[fromActivity/Type] + [toActivity/Type]"
        );

        for (String f : inputs) {
            System.out.println("Input: " + f);
            ParseResult r = parseFormula(f);
            System.out.println(r);
            System.out.println();
        }
    }
}