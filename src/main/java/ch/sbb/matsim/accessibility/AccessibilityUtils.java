package ch.sbb.matsim.accessibility;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.BufferedWriter;
import java.util.*;

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

    public static Collection<Coord> getHomeCoordinatesFromPopulation(String populationFilename) {
        List<Coord> coords = new ArrayList<>();
        StreamingPopulationReader popReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        popReader.addAlgorithm(p -> {
            Plan plan = p.getSelectedPlan();
            Activity homeAct = (Activity) plan.getPlanElements().get(0);
            if (homeAct.getType().startsWith("home")) {
                coords.add(homeAct.getCoord());
            }
        });
        popReader.readFile(populationFilename);
        return coords;
    }

    private static void writeCoords(Collection<Coord> coords, String filename) {
        try (BufferedWriter writer = org.matsim.core.utils.io.IOUtils.getBufferedWriter(filename)) {
            writer.write("X,Y\n");
            for (Coord c : coords) {
                writer.write(Double.toString(c.getX()));
                writer.write(",");
                writer.write(Double.toString(c.getY()));
                writer.write("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String networkFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\network.xml.gz";
        String coordsNetworkFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\gridCoords250network400.csv";

        String populationFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\MOBi21.10pct.output_plans.xml.gz";
        String coordsPopulation1Filename = "C:\\devsbb\\codes\\_data\\skims2.1\\gridCoords250plans0.csv";
        String coordsPopulation2Filename = "C:\\devsbb\\codes\\_data\\skims2.1\\gridCoords250plans500.csv";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        // Network

        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);

        Collection<Node> nodes = AccessibilityUtils.getNodesInRange(scenario.getNetwork(), 2_550_000, 2_650_000, 1_150_000, 1_250_000);
        Collection<Coord> nodeCoords = AccessibilityUtils.extractCoords(nodes);
        Collection<Coord> gridCoords = AccessibilityUtils.getGridCoordinates(nodeCoords, 250, 400);
        writeCoords(gridCoords, coordsNetworkFilename);

        // Population

        Collection<Coord> homeCoords = AccessibilityUtils.getHomeCoordinatesFromPopulation(populationFilename);
        Collection<Coord> gridCoords1 = AccessibilityUtils.getGridCoordinates(homeCoords, 250, 0);
        Collection<Coord> gridCoords2 = AccessibilityUtils.getGridCoordinates(homeCoords, 250, 500);
        writeCoords(gridCoords1, coordsPopulation1Filename);
        writeCoords(gridCoords2, coordsPopulation2Filename);
    }
}
