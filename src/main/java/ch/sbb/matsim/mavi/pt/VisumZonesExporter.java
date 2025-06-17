package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.visum.VisumDataSource;
import ch.sbb.matsim.mavi.visum.VisumNetFile;
import ch.sbb.matsim.mavi.visum.VisumDataSource.DataTable;

import java.io.IOException;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VisumZonesExporter {

    private static final Logger log = LogManager.getLogger(VisumZonesExporter.class);
    private final VisumDataSource visum;
    
    public VisumZonesExporter(VisumDataSource visum) {
        this.visum = visum;
    }
    
    public void exportZonesToCsv(String outputPath) throws IOException {
        // Get the Zones table from the VisumNetFile
        DataTable zonesTable = visum.getTable("Zones");
        
        if (zonesTable == null || zonesTable.size() == 0) {
            log.warn("No zones found in the Visum network file.");
            return;
        }
        
        // Get the data for the required columns
        String[][] zoneData = zonesTable.attrs("NO", "NAME", "CODE", "MARKTGEBIETVARELAST");
        
        try (CSVWriter writer = new CSVWriter("",
            new String[]{"NO", "NAME", "CODE", "MARKTGEBIETVARELAST"}, outputPath)) {
            
            for (String[] row : zoneData) {
                writer.set("NO", row[0]);
                writer.set("NAME", row[1]);
                writer.set("CODE", row[2]);
                writer.set("MARKTGEBIETVARELAST", row[3]);
                writer.writeRow();
            }
            log.info("Exported {} zones to {}", zoneData.length, outputPath);
        } catch (IOException e) {
            log.error("Failed to export zones to {}: {}", outputPath, e.getMessage());
        }
    }
}
