package ch.sbb.matsim.mavi.visum;

import java.nio.charset.Charset;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Drop‑in replacement for {@link Visum} when only a <i>plain</i> *.net* file
 * is available (no COM, no installed Visum).  It mimics the tiny subset of the
 * API that higher layers rely on: {@link #getTable(String)} and
 * {@link NetfileTable#attrs(String...)}.
 * <p>
 * Usage:
 * <pre>{@code
 * VisumNetFile vis = VisumNetFile.open(Paths.get("network.net"));
 * String[][] nodes = vis.table("Nodes").attrs("No", "XCoord");
 * }</pre>
 * <p>
 * Keeping the method names identical to the COM wrapper allows a trivial
 * <strong>strategy</strong> interface later — the caller won't notice which
 * implementation is returned at runtime.
 */
public class VisumNetFile implements VisumDataSource {

    private static final Logger log = LogManager.getLogger(VisumNetFile.class);

    // ------------------------------------------------------------------
    // Factory
    // ------------------------------------------------------------------

    public static VisumNetFile open(Path netFile) throws IOException {
        return new VisumNetFile(netFile, Charset.defaultCharset());
    }

    public static VisumNetFile open(Path netFile, Charset encoding) throws IOException {
        return new VisumNetFile(netFile, encoding);
    }

    private VisumNetFile(Path net, Charset encoding) throws IOException {
        parse(net, encoding);
    }

    @Override public void close() { /* nothing to clean up */ }

    // ------------------------------------------------------------------
    // Public API – mirrors Visum.java (COM) subset
    // ------------------------------------------------------------------

    @Override
    public NetfileTable getTable(String name) {
        String netFileName = netObjectName2netFileName(name);
        NetfileTable t = tables.get(netFileName);
        if (t == null) throw new IllegalArgumentException("Table " + name + " not found in .net file (searched as " + netFileName + ")");
        return t;
    }

    private String netObjectName2netFileName(String name) {
        Map<String, String> knownAliases = Map.of(
            "VehicleJourneys", "VEHJOURNEY",
            "TSystems", "TSYS",
            "ChainedUpVehicleJourneySections", "CHAINEDUPVEHJOURNEYSECTION"
        );
        if (knownAliases.containsKey(name)) {
            return knownAliases.get(name);
        }
        String upperName = name.toUpperCase(Locale.ROOT);
        if (upperName.endsWith("S")) {
            return upperName.substring(0, upperName.length() - 1);
        } else {
            return upperName;
        }
    }

    // ------------------------------------------------------------------
    // Internal representation
    // ------------------------------------------------------------------

    private final Map<String, NetfileTable> tables = new HashMap<>();

    private void parse(Path p, Charset encoding) throws IOException {
        log.info("[VisumNetFile] parsing {} …", p);
        try (BufferedReader in = Files.newBufferedReader(p, encoding)) {
            String line; NetfileTable current = null;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("*")) continue;

                if (line.startsWith("$")) {
                    int colon = line.indexOf(':');
                    if (colon < 0) { current = null; continue; }
                    String tblName = line.substring(1, colon).toUpperCase(Locale.ROOT);
                    String[] header = line.substring(colon + 1).split(";", -1);
                    current = new NetfileTable(tblName, header);
                    tables.put(tblName, current);
                    continue;
                }

                if (current != null) current.addRow(line.split(";", -1));
            }
        }
        log.info("[VisumNetFile] loaded {} tables", tables.size());
    }

    // ------------------------------------------------------------------
    // Table impl (identical interface to COM wrapper)
    // ------------------------------------------------------------------

    public static final class NetfileTable implements DataTable {
        private final String name;
        private final String[] header;
        private final Map<String, Integer> colIdx = new HashMap<>();
        private final List<String[]> rows = new ArrayList<>();

        NetfileTable(String name, String[] h) {
            this.name = name;
            this.header = new String[h.length];
            for (int i=0;i<h.length;i++) {
                String col = h[i].trim().toUpperCase(Locale.ROOT);
                this.header[i] = col;
                colIdx.put(col, i);
            }
        }

        void addRow(String[] values) {
            rows.add(values);
        }

        @Override
        public int size() {
            return rows.size();
        }

        @Override
        public String[][] attrs(String... names) {
            String[][] result = new String[rows.size()][names.length];
            for (int i = 0; i < names.length; i++) {
                String colName = names[i].trim().toUpperCase(Locale.ROOT);
                if (colName.equals("TIME(F)")) {
                    // workaround for special case TRANSFERWALKTIMESTOPAREA
                    colName = "TIME";
                }
                Integer colPos = colIdx.get(colName);
                for (int j = 0; j < rows.size(); j++) {
                    String[] row = rows.get(j);
                    result[j][i] = colPos != null && colPos < row.length ? row[colPos] : "";
                }
            }
            return result;
        }
        @Override public String toString() { return name + "(" + size() + ")"; }

    }
}
