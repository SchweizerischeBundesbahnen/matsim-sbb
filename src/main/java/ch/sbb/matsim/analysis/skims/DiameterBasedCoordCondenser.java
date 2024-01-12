package ch.sbb.matsim.analysis.skims;

import org.matsim.api.core.v01.Coord;
import org.matsim.contrib.common.util.DistanceUtils;

import java.util.*;

public class DiameterBasedCoordCondenser implements PTSkimMatrices.CoordAggregator {
    private final static double MAXDISTANCE = 250.0;

    @Override
    public List<CalculateSkimMatrices.WeightedCoord> aggregateCoords(Coord[] coords) {
        double length = coords.length;
        double maxDistanceSquared = MAXDISTANCE * MAXDISTANCE;
        double averageX = Arrays.stream(coords).mapToDouble(coord -> coord.getX()).average().getAsDouble();
        double averageY = Arrays.stream(coords).mapToDouble(coord -> coord.getY()).average().getAsDouble();
        Coord averageCoord = new Coord(averageX, averageY);
        if (Arrays.stream(coords).allMatch(coord -> DistanceUtils.calculateSquaredDistance(averageCoord, coord) < maxDistanceSquared)) {
            return Collections.singletonList(new CalculateSkimMatrices.WeightedCoord(averageCoord, length));
        } else {
            List<Coord> condensedCoords = new ArrayList<>();
            Map<Coord, Double> coordWeights = new HashMap<>();

            for (Coord current : coords) {
                boolean shouldAdd = true;
                for (var existing : condensedCoords) {
                    double distance = DistanceUtils.calculateSquaredDistance(current, existing);
                    if (distance <= maxDistanceSquared) {
                        shouldAdd = false;
                        // Increase the weight of the existing coordinate
                        double existingWeight = coordWeights.getOrDefault(existing, 1.0);
                        coordWeights.put(existing, existingWeight + 1.0);
                        break;
                    }
                }

                if (shouldAdd) {
                    condensedCoords.add(current);
                    coordWeights.put(current, 1.0);
                }
            }

            List<CalculateSkimMatrices.WeightedCoord> condensedWeightedCoords = new ArrayList<>();
            for (var condensed : condensedCoords) {
                double weight = coordWeights.get(condensed);
                condensedWeightedCoords.add(new CalculateSkimMatrices.WeightedCoord(condensed, weight));
            }

            return condensedWeightedCoords;
        }

    }


}

