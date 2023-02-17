package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.Coord;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AssignZoneCoords {
    public static void main(String[] args) {

        Zones zones = ZonesLoader.loadZones("zones", "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\skims\\zonierung_frankreich\\bordering_comm.shp", "insee");
        String[] col = {"X", "Y", "id"};
        String[] coln = {"ZONE", "POINT_INDEX", "X", "Y"};
        Map<String, MutableInt> pointIndex = new HashMap<>();
        Map<String, Coord> lastCoord = new HashMap<>();
        try (CSVReader reader = new CSVReader("\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\skims\\zonierung_frankreich\\coords.csv", ";")) {
            try (CSVWriter writer = new CSVWriter(null, coln, "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\skims\\zonierung_frankreich\\zoneCoords.csv")) {


                var line = reader.readLine();
                while (line != null) {
                    String x = line.get("X");
                    String y = line.get("Y");
                    Coord coord = new Coord(Double.parseDouble(x), Double.parseDouble(y));
                    Zone zone = zones.findZone(coord);
                    if (zone != null) {
                        String zoneId = zone.getId().toString();
                        int index = pointIndex.computeIfAbsent(zoneId, z -> new MutableInt()).getAndIncrement();
                        writer.set("X", x);
                        writer.set("Y", y);
                        writer.set("ZONE", zoneId);
                        writer.set("POINT_INDEX", Integer.toString(index));
                        writer.writeRow();
                        lastCoord.put(zoneId, coord);

                    }
                    line = reader.readLine();
                }
                pointIndex.entrySet().stream().filter(stringMutableIntEntry -> stringMutableIntEntry.getValue().intValue() < 4).forEach(stringMutableIntEntry -> {
                    for (int i = stringMutableIntEntry.getValue().intValue() + 1; i < 5; i++) {
                        String zone = stringMutableIntEntry.getKey();
                        writer.set("X", Double.toString(lastCoord.get(zone).getX()));
                        writer.set("Y", Double.toString(lastCoord.get(zone).getY()));
                        writer.set("ZONE", zone);
                        writer.set("POINT_INDEX", Integer.toString(i));
                        writer.writeRow();
                    }

                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
