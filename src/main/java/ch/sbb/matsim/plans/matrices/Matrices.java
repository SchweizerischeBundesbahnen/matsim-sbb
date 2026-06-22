package ch.sbb.matsim.plans.matrices;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.zones.Zone;
import org.matsim.api.core.v01.Id;

import java.util.Map;

public interface Matrices {

    FloatMatrix<Id<Zone>> getMatrix(String matrixName);

    void addMatrix(String matrixName, FloatMatrix<Id<Zone>> matrix);

    void addMatrices(Map<String, FloatMatrix<Id<Zone>>> matrices);

    void removeMatrix(String matrixName);

    FloatMatrix<Id<Zone>> getKalifMatrix(MatrixNames.KalifMatrices matrixName);

    FloatMatrix<Id<Zone>> getTravelMetricsMatrix(MatrixNames.TravelMetricsMatrices matrixName);

    /**
     * Retrieves all travel metric values for a specific origin-destination pair.
     *
     * @param fromZoneId The ID of the origin zone.
     * @param toZoneId   The ID of the destination zone.
     * @return A map where keys are the travel metric names (e.g., "CAR_TRAVEL_TIMES")
     * and values are the corresponding floating-point values for the given OD pair.
     */
    Map<String, Double> getTravelMetrics(Id<Zone> fromZoneId, Id<Zone> toZoneId);

    /**
     * Retrieves all travel metric values for a specific origin-destination pair.
     *
     * @param fromZone The origin zone.
     * @param toZone   The destination zone.
     * @return A map where keys are the travel metric names (e.g., "CAR_TRAVEL_TIMES")
     * and values are the corresponding floating-point values for the given OD pair.
     */
    Map<String, Double> getTravelAndAccessibilityMetrics(Zone fromZone, Zone toZone);

    /**
     * Retrieves all Kalif values for a specific origin-destination pair.
     *
     * @param fromZoneId The ID of the origin zone.
     * @param toZoneId The ID of the destination zone.
     * @return A map where keys are the Kalif matrix names (e.g., "KALIF_WORK")
     *         and values are the corresponding floating-point values for the given OD pair.
     */
    Map<String, Double> getKalifMetrics(Id<Zone> fromZoneId, Id<Zone> toZoneId);
}