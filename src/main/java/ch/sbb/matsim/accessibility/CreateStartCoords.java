package ch.sbb.matsim.accessibility;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

public class CreateStartCoords {

	private final static Logger LOG = Logger.getLogger(CreateStartCoords.class);

	public static void main(String[] args) {
		System.setProperty("matsim.preferLocalDtds", "true");
		//        regionBern(300);
		//        regionLausanneBernAlps(300);
		switzerland(new int[]{100, 150, 200, 250, 300, 500, 1000});
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
		Collection<Coord> fullHeightStripe = AccessibilityUtils.getGridCoordinates(gridSize, 2_610_000d + gridSize, 1_110_000d, 2_625_000d, 1_240_000d);
		Set<Coord> allCoords = new HashSet<>();
		allCoords.addAll(extendedCoords);
		allCoords.addAll(fullHeightStripe);

		LOG.info("# home coordinates: " + homeCoords.size());
		LOG.info("# filtered home coordinates: " + filteredCoords.size());
		LOG.info("# gridded, extended home coordinates: " + extendedCoords.size());
		LOG.info("# coordinates in stripe: " + fullHeightStripe.size());
		LOG.info("# coordinates total: " + allCoords.size());

		try (BufferedWriter writer = IOUtils.getBufferedWriter("C:\\devsbb\\codes\\_data\\skims2.1\\startCoordsLauBeAlps" + gridSize + ".csv")) {
			writer.write("X,Y\n");
			for (Coord c : allCoords) {
				writer.write(c.getX() + "," + c.getY() + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void switzerland(int[] gridSizes) {
		Collection<Coord> homeCoords = AccessibilityUtils.getHomeCoordinatesFromPopulation("C:\\devsbb\\codes\\_data\\skims2.1\\plans.xml.gz");
		Collection<Coord> facilitiesCoords = AccessibilityUtils.getFacilityCoordinates("C:\\devsbb\\codes\\_data\\skims2.1\\facilities.xml.gz");

		List<Coord> allCoords = new ArrayList<>();
		allCoords.addAll(homeCoords);
		allCoords.addAll(facilitiesCoords);

		LOG.info("# home coordinates: " + homeCoords.size());
		LOG.info("# facility coordinates: " + facilitiesCoords.size());

		for (int gridSize : gridSizes) {
			LOG.info("grid size = " + gridSize);

			Collection<Coord> griddedCoords = AccessibilityUtils.getGridCoordinates(allCoords, gridSize, 500);
			LOG.info("# gridded, extended coordinates: " + griddedCoords.size());

			try (BufferedWriter writer = IOUtils.getBufferedWriter("C:\\devsbb\\codes\\_data\\skims2.1\\startCoordsCH_" + gridSize + ".csv")) {
				writer.write("X,Y\n");
				for (Coord c : griddedCoords) {
					writer.write(c.getX() + "," + c.getY() + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
