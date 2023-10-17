package ch.sbb.matsim.projects.synpop.liechtenstein;


import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Id;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*
    A generic class to use OSM building Data as the source for businesses
 */
public class GenerateBusinessesPerZone {


    private final Map<Id<Zone>, JobsPerSectorAndZone> jobsPerSectorAndZoneMap = new HashMap<>();
    private final Map<String, String> osmBuildingTypeToSectorMap = new HashMap<>();
    private final Map<String, Integer> osmBuildingTypeToNOGAMap = new HashMap<>();
    private final Zones zones;

    public GenerateBusinessesPerZone(String osmFile, String osmBuildingTypeMapping, String jobsFile, Zones zones) {
        this.zones = zones;
        parseJobsData(jobsFile);
        parseOSMTypeMapping(osmBuildingTypeMapping);
    }

    public static void main(String[] args) {
        String osmFile = args[0];
        String osmBuildingTypeMapping = args[1];
        String jobsFile = args[2];
        String zonesFile = args[3];
        String outputBusinessesFile = args[4];
        var zones = ZonesLoader.loadZones("zones", zonesFile);
        GenerateBusinessesPerZone businessesPerZone = new GenerateBusinessesPerZone(osmFile, osmBuildingTypeMapping, jobsFile, zones);

    }

    private void parseOSMTypeMapping(String osmBuildingTypeMapping) {
        try (CSVReader reader = new CSVReader(osmBuildingTypeMapping, ";")) {
            var line = reader.readLine();
            while (line != null) {
                String osmType = line.get("type");
                String sector = line.get("sector");
                int noga = Integer.parseInt(line.get("noga"));
                osmBuildingTypeToSectorMap.put(osmType, sector);
                osmBuildingTypeToNOGAMap.put(osmType, noga);
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseJobsData(String jobsFile) {
        try (CSVReader reader = new CSVReader(jobsFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                Id<Zone> zoneId = Id.create(line.get("zone_id"), Zone.class);
                int primary = Integer.parseInt(line.get("agriculture"));
                int secondary = Integer.parseInt(line.get("industry"));
                int tertiary = Integer.parseInt(line.get("services"));
                double exoShare = Double.parseDouble(line.get("exo_share"));
                JobsPerSectorAndZone jobsPerSectorAndZone = new JobsPerSectorAndZone(zoneId, primary, secondary, tertiary, exoShare);
                jobsPerSectorAndZoneMap.put(zoneId, jobsPerSectorAndZone);
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    record JobsPerSectorAndZone(Id<Zone> zoneId, int primary, int secondary, int tertiary, double exoShare) {
    }
}
