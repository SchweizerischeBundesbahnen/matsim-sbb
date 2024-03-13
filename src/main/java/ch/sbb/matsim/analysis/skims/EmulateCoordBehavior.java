package ch.sbb.matsim.analysis.skims;

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmulateCoordBehavior {

    public static void main(String[] args) throws IOException {
        String expectedHeader = "ZONE;POINT_INDEX;X;Y";
        Map<String, Coord[]> coordsPerZone = new HashMap<>();
        Map<String, List<CalculateSkimMatrices.WeightedCoord>> condensedCoordsPerZone = new HashMap<>();
        DiameterBasedCoordCondenser condenser = new DiameterBasedCoordCondenser();
        try (BufferedReader reader = IOUtils.getBufferedReader(args[0])) {
            String header = reader.readLine();
            if (!expectedHeader.equals(header)) {
                throw new RuntimeException("Bad header, expected '" + expectedHeader + "', got: '" + header + "'.");
            }
            String line;
            int maxIdx = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = StringUtils.explode(line, ';');
                String zoneId = parts[0];
                int idx = Integer.parseInt(parts[1]);
                double x = Double.parseDouble(parts[2]);
                double y = Double.parseDouble(parts[3]);
                final int length = Math.max(idx, maxIdx);
                Coord[] coords = coordsPerZone.computeIfAbsent(zoneId, k -> new Coord[length + 1]);
                if (coords.length < (idx + 1)) {
                    Coord[] tmp = new Coord[idx + 1];
                    System.arraycopy(coords, 0, tmp, 0, coords.length);
                    coords = tmp;
                    coordsPerZone.put(zoneId, coords);
                }
                coords[idx] = new Coord(x, y);
                if (idx > maxIdx) {
                    maxIdx = idx;
                }
            }
        }
        int i = 0;
        int bef = 0;
        for (var cL : coordsPerZone.entrySet()) {
            var condensed = condenser.aggregateCoords(cL.getValue());
            bef += cL.getValue().length;
            i += condensed.size();
            condensedCoordsPerZone.put(cL.getKey(), condensed);
        }
        System.out.println("before " + bef);
        System.out.println("after " + i);
        try (
                BufferedWriter writer = IOUtils.getBufferedWriter(args[1])) {
            writer.write("ZONE;POINT_INDEX;X;Y;weight\n");
            for (var e : condensedCoordsPerZone.entrySet()) {
                String zoneId = e.getKey();
                var coords = e.getValue();
                int z = 0;
                for (CalculateSkimMatrices.WeightedCoord wcoord : coords) {
                    Coord coord = wcoord.coord();
                    writer.write(zoneId);
                    writer.write(";");
                    writer.write(Integer.toString(z));
                    writer.write(";");
                    writer.write(Double.toString(coord.getX()));
                    writer.write(";");
                    writer.write(Double.toString(coord.getY()));
                    writer.write(";");
                    writer.write(Double.toString(wcoord.weight()));
                    writer.write("\n");
                    z++;
                }
            }
        }
    }

}
