package ch.sbb.matsim.mavi.visum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h2>Strategy interface for Visum data access</h2>
 * Implemented by the COM‐based {@link Visum} class <em>and</em> the
 * plain‑file {@link VisumNetFile} parser so that higher‑level code can remain
 * agnostic about the actual data source.
 *
 * <p>Only the <i>minimal</i> surface required by MATSim conversion tools is
 * exposed: table lookup and multi‑column attribute fetch.  If you need more
 * Visum functionality later, extend the interface — both back‑ends can be
 * evolved in lock‑step.</p>
 */
public interface VisumDataSource extends AutoCloseable {

    /** Return a handle to a Visum table (Nodes, Links, StopAreas …). */
    DataTable getTable(String name);

    /** Convenience bridge for legacy static calls. */
    default String[][] getArrayFromAttributeList(DataTable tbl, String... attrs) {
        return tbl.attrs(attrs);
    }

    /**
     * Unified abstraction of a Visum *table* or *view* (for COM).  Attribute
     * names are case‑insensitive.
     */
    interface DataTable {
        /** Row count (respects any active filter for COM). */
        int size();
        /** Pull multiple columns at once; missing cells → empty string. */
        String[][] attrs(String... names);

        /** Builds a multimap from two integer columns (keyAttr, valAttr). */
        default Map<String, List<String>> buildMultiMap(
            String keyAttr, String valAttr) {

            String[][] rows = this.attrs(keyAttr, valAttr);
            Map<String, List<String>> map = new HashMap<>();

            for (String[] r : rows) {
                map.computeIfAbsent(r[0], x -> new ArrayList<>()).add(r[1]);
            }
            return map;
        }
    }

}
