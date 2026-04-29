package ch.sbb.matsim.plans.stats;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

/*
  Demo: parse config JSON into POJOs and print a summary table.
  Usage: java ConfigSummaryDemo <path-to-json-file>
*/

public class ReadReportJson {

    // Root object
    public static class ReportConfig {
        public String name;
        public List<Sheet> sheets = new ArrayList<>();
    }

    // Sheet object
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sheet {
        public String name;
        public String table_ref;
        public Boolean scale; // optional
        public List<ReportGroup> groups = Collections.emptyList();
        public List<ReportVariable> variables = Collections.emptyList();
        public Map<String, Object> pivots; // present in some sheets
        // allow unknowns if any
        @JsonAnySetter
        public void ignore(String key, Object value) {
            // noop - keep ignoring unknowns
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReportGroup {
        public String name;
        public String filter_expression;
        public String filter_expression_person;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReportVariable {
        public String name;
        public String formula;
        public String aggregation;
        public String normalize;
        // other possible fields ignored
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java ConfigSummaryDemo <path-to-json-file>");
            System.exit(2);
        }

        File jsonFile = new File(args[0]);
        if (!jsonFile.exists()) {
            System.err.println("File not found: " + jsonFile.getAbsolutePath());
            System.exit(2);
        }

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(jsonFile, ReportConfig.class);

        printSummary(config);
    }

    private static void printSummary(ReportConfig config) {
        // header
        String fmtHeader = "%-25s %-12s %6s %9s %7s%n";
        String fmtRow = "%-25s %-12s %6d %9d %7s%n";
        System.out.printf(fmtHeader, "Sheet", "TableRef", "#Groups", "#Variables", "HasPivots");
        System.out.printf(fmtHeader, "-----", "--------", "-------", "---------", "---------");

        for (Sheet s : config.sheets) {
            int groups = s.groups == null ? 0 : s.groups.size();
            int vars = s.variables == null ? 0 : s.variables.size();
            String hasPivots = (s.pivots != null && !s.pivots.isEmpty()) ? "yes" : "no";
            System.out.printf(fmtRow, s.name, safe(s.table_ref), groups, vars, hasPivots);
        }

        System.out.println();
        System.out.println("Details per sheet (variables and groups):");
        for (Sheet s : config.sheets) {
            System.out.println("------------------------------------------------------------");
            System.out.println("Sheet: " + s.name + " (table_ref=" + safe(s.table_ref) + ")");
            if (s.pivots != null && !s.pivots.isEmpty()) {
                System.out.println("  Pivots present: keys = " + s.pivots.keySet());
            }
            if (s.groups != null && !s.groups.isEmpty()) {
                System.out.println("  Groups:");
                for (ReportGroup g : s.groups) {
                    System.out.printf("    - %s : %s%n", g.name, safe(g.filter_expression));
                }
            } else {
                System.out.println("  Groups: none");
            }
            if (s.variables != null && !s.variables.isEmpty()) {
                System.out.println("  Variables:");
                for (ReportVariable v : s.variables) {
                    System.out.printf("    - %s | agg=%s | normalize=%s%n      expr=%s%n",
                            v.name, safe(v.aggregation), safe(v.normalize), safe(v.formula));
                }
            } else {
                System.out.println("  Variables: none");
            }
        }
    }

    private static String safe(String s) {
        return s == null ? "<null>" : s;
    }
}
