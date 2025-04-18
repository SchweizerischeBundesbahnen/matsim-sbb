package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.visum.Visum;
import ch.sbb.matsim.mavi.visum.Visum.ComObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Exports zone-to-stop-point connections from Visum into a CSV.
 * The output columns are: zone,walk_time,stop_point
 */
public class VisumConnectionsExporter {
    private static final Logger log = LogManager.getLogger(VisumConnectionsExporter.class);
    private final Visum visum;

    public VisumConnectionsExporter(Visum visum) {
        this.visum = visum;
    }

    /**
     * Writes the connections to the given file path.
     * @param path full path to the output CSV file
     */
    public void writeConnections(String path) {
        try {
            // Retrieve the net object containing connector info
            ComObject connections = visum.getNetObject("Connections");
            int count = connections.countActive();
            // Load attributes: zone, stop points (possibly comma-separated), walk time
            String[][] data = Visum.getArrayFromAttributeList(count,
                connections,
                "ZoneNo",
                "Node\\Distinct:StopAreas\\Distinct:StopPoints\\No",
                "T0_TSys(F)");

            // Prepare CSV writer with headers
            try (CSVWriter writer = new CSVWriter("",
                new String[]{"zone", "walk_time", "stop_point"},
                path)) {
                for (String[] row : data) {
                    String zone = row[0];
                    String stopPoints = row[1];
                    String walkTime = row[2];
                    if (stopPoints == null || stopPoints.isBlank()) {
                        continue;
                    }
                    // Split comma-separated stop points and write a row per stop point
                    String[] parts = stopPoints.split(",");
                    for (String sp : parts) {
                        sp = sp.trim();
                        if (sp.isEmpty()) continue;
                        writer.set("zone", zone);
                        writer.set("walk_time", walkTime);
                        writer.set("stop_point", sp);
                        writer.writeRow();
                    }
                }
            }

            log.info("Zone connections successfully exported to {}", path);
        } catch (IOException e) {
            log.error("Could not write connections file: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error exporting zone connections", e);
        }
    }
}
