package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.visum.VisumDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.collections.Tuple;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports <em>zone → stop-point</em> walk connections from a Visum data
 * source (COM or *.net* file).
 */
public class VisumConnectionsExporter {

    private static final Logger log = LogManager.getLogger(VisumConnectionsExporter.class);

    private final VisumDataSource ds;
    private final Map<String, Set<String>> node2sp;

    public VisumConnectionsExporter(VisumDataSource visum) {
        this.ds = visum;
        this.node2sp = buildNode2StopPointMap();
    }

    /** Writes CSV <code>zone,walk_time,stop_point</code> to {@code path}. */
    public void writeConnections(String path) {
        try (CSVWriter w = new CSVWriter("",
            new String[]{"zone", "walk_time", "stop_point"}, path)) {

            VisumDataSource.DataTable connectors = ds.getTable("Connectors");
            String[][] attr = connectors.attrs("ZoneNo", "NodeNo", "T0_TSys(F)");

            Set<Tuple<String,String>> seen = new HashSet<>();
            int written = 0;

            for (String[] r : attr) {
                String zone = r[0];
                String node = r[1];
                String walkTime = r[2];

                for (String sp : node2sp.getOrDefault(node, Collections.emptySet())) {
                    Tuple<String,String> key = new Tuple<>(zone, sp);
                    if (!seen.add(key)) continue;                 // duplicate

                    w.set("zone",       zone);
                    w.set("walk_time",  walkTime);
                    w.set("stop_point", sp);
                    w.writeRow();
                    written++;
                }
            }
            log.info("Wrote {} unique zone–stop connections to {}", written, path);
        } catch (IOException | NumberFormatException e) {
            log.error("Failed to write connections to {}: {}", path, e.getMessage());
        }
    }

    private Map<String, Set<String>> buildNode2StopPointMap() {
        Map<String, List<String>> node2area =
            ds.getTable("StopAreas").buildMultiMap("NodeNo",     "No");
        Map<String, List<String>> area2sp   =
            ds.getTable("StopPoints").buildMultiMap("StopAreaNo", "No");

        return node2area.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .flatMap(sa -> area2sp.getOrDefault(sa, List.of()).stream())
                                .collect(Collectors.toSet())
                ));
    }

}
