package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.analysis.skims.CalculateSkimMatrices;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Creates some of the input files required to perform the skim calculation (see {@link CalculateIndicatorMatrices}).
 *
 * @author mrieser
 */
public class CreateSkimCalculationInput {

	public static void main(String[] args) throws IOException {
		System.setProperty("matsim.preferLocalDtds", "true");

		String zonesShapeFilename = args[0];
		String zonesIdAttributeName = args[1];
		String facilitiesFilename = args[2];
		String outputDirectory = args[3];
		int numberOfPointsPerZone = Integer.valueOf(args[4]);
		int numberOfThreads = Integer.valueOf(args[5]);

		Random r = new Random(20180404L);

		CalculateSkimMatrices skims = new CalculateSkimMatrices(outputDirectory, numberOfThreads);
		skims.calculateSamplingPointsPerZoneFromFacilities(facilitiesFilename, numberOfPointsPerZone, zonesShapeFilename, zonesIdAttributeName, r, f -> {
			double weight = 2; // default for households
			String fte = (String) f.getAttributes().getAttribute("fte");
			if (fte != null) {
				weight = Double.parseDouble(fte);
			}
			return weight;
		});
		skims.writeSamplingPointsToFile(new File(outputDirectory, CalculateSkimMatrices.ZONE_LOCATIONS_FILENAME));
	}
}
