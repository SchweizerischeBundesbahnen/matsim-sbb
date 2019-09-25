package ch.sbb.matsim.accessibility;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.BufferedWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AccessibilityUtils {

    public static Set<Node> getNodesInRange(Network network, double minX, double maxX, double minY, double maxY) {
        Set<Node> nodes = new HashSet<>();
        for (Node node : network.getNodes().values()) {
            double x = node.getCoord().getX();
            double y = node.getCoord().getY();
            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    public static Set<Coord> extractCoords(Collection<Node> nodes) {
        Set<Coord> coords = new HashSet<>();
        nodes.forEach(n -> coords.add(n.getCoord()));
        return coords;
    }

    public static Set<Coord> getGridCoordinates(Collection<Coord> coords, double gridSize, int radius) {
        Set<Coord> gridCoords = new HashSet<>();

        for (Coord c : coords) {
            double x = c.getX();
            double y = c.getY();

            gridCoords.add(getGridCoordinate(x, y, gridSize));

            int extentCount = (int) Math.ceil(radius/gridSize);
            double extent = extentCount * gridSize;
            for (double xx = x - extent; xx <= x + extent; xx += gridSize) {
                for (double yy = y - extent; yy <= y + extent; yy += gridSize) {
                    Coord coord = getGridCoordinate(xx, yy, gridSize);
                    if (CoordUtils.calcEuclideanDistance(coord, c) <= radius) {
                        gridCoords.add(coord);
                    }
                }
            }
        }

        return gridCoords;
    }

    public static Coord getGridCoordinate(double x, double y, double gridSize) {
        return new Coord(((int) (x / gridSize)) * gridSize + gridSize/2, ((int) (y / gridSize)) * gridSize + gridSize/2);
    }

    public static void main(String[] args) {
        String networkFilename = "C:\\devsbb\\codes\\_data\\CH2016_1.2.17\\CH.10pct.2016.output_network.xml.gz";
        String coordsFilename = "C:\\devsbb\\codes\\_data\\skims\\gridCoords250.csv";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);

        Collection<Node> nodes = AccessibilityUtils.getNodesInRange(scenario.getNetwork(), 580_000, 620_000, 180_000, 220_000);
        Collection<Coord> nodeCoords = AccessibilityUtils.extractCoords(nodes);
        Collection<Coord> gridCoords = AccessibilityUtils.getGridCoordinates(nodeCoords, 250, 400);
        try (BufferedWriter writer = org.matsim.core.utils.io.IOUtils.getBufferedWriter(coordsFilename)) {
            writer.write("X,Y\n");
            for (Coord c : gridCoords) {
                writer.write(Double.toString(c.getX()));
                writer.write(",");
                writer.write(Double.toString(c.getY()));
                writer.write("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
