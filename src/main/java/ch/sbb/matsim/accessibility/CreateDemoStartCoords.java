package ch.sbb.matsim.accessibility;

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CreateDemoStartCoords {

    public static void main(String[] args) {
//        regionBern(300);
        regionLausanneBernAlps(300);
    }

    public static void regionBern(int gridSize) {
        try (BufferedWriter writer = IOUtils.getBufferedWriter("C:\\devsbb\\codes\\_data\\skims2.1\\startCoordsBern" + gridSize + ".csv")) {
            writer.write("X,Y\n");
            for (double x = 2_580_000; x <= 2_603_000; x += gridSize) {
                for (double y = 1_180_000; y <= 1_203_000; y += gridSize) {
                    writer.write(x + "," + y + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void regionLausanneBernAlps(int gridSize) {
        Collection<Coord> homeCoords = AccessibilityUtils.getHomeCoordinatesFromPopulation("C:\\devsbb\\codes\\_data\\skims2.1\\plans.xml.gz");
        Collection<Coord> filteredCoords = AccessibilityUtils.filterCoordinatesInArea(homeCoords, 2_520_000, 1_140_000, 2_610_000, 1_205_000);
        Collection<Coord> extendedCoords = AccessibilityUtils.getGridCoordinates(filteredCoords, gridSize, 500);
        Collection<Coord> fullHeightStripe = AccessibilityUtils.getGridCoordinates(gridSize, 2_610_000 + gridSize, 1_110_000, 2_625_000, 1_240_000);
        Set<Coord> allCoords = new HashSet<>();
        allCoords.addAll(extendedCoords);
        allCoords.addAll(fullHeightStripe);

        try (BufferedWriter writer = IOUtils.getBufferedWriter("C:\\devsbb\\codes\\_data\\skims2.1\\startCoordsLauBeAlps" + gridSize + ".csv")) {
            writer.write("X,Y\n");
            for (Coord c : allCoords) {
                writer.write(c.getX() + "," + c.getY() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
