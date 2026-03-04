package ch.sbb.matsim.plans.matrices;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.zones.Zone;
import org.matsim.api.core.v01.Id;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultMatrices implements Matrices {
    private final Map<String, FloatMatrix<Id<Zone>>> matrices = new HashMap<>();

    @Override
    public FloatMatrix<Id<Zone>> getMatrix(String matrixName) {
        return matrices.get(matrixName.toLowerCase());
    }

    @Override
    public void addMatrix(String matrixName, FloatMatrix<Id<Zone>> matrix) {
        matrices.put(matrixName, matrix);
    }

    @Override
    public void addMatrices(Map<String, FloatMatrix<Id<Zone>>> matrices) {
        this.matrices.putAll(matrices);
    }

    @Override
    public void removeMatrix(String matrixName) {
        matrices.remove(matrixName);
    }


    @Override
    public FloatMatrix<Id<Zone>> getKalifMatrix(MatrixNames.KalifMatrices matrixName) {
        return matrices.get(matrixName.name().toLowerCase());
    }


    @Override
    public FloatMatrix<Id<Zone>> getTravelMetricsMatrix(MatrixNames.TravelMetricsMatrices matrixName) {
        return matrices.get(matrixName.name());
    }

    @Override
    public Map<String, Double> getTravelMetrics(Id<Zone> fromZoneId, Id<Zone> toZoneId) {
        return getMetricsForPair(fromZoneId, toZoneId, MatrixNames.TravelMetricsMatrices.values());
    }

    @Override
    public Map<String, Double> getTravelAndAccessibilityMetrics(Zone origin, Zone destination) {
        var travelMetrics = getTravelMetrics(origin.getId(), destination.getId());

        travelMetrics.put("va_impedances", toDouble(getKalifMatrix(MatrixNames.KalifMatrices.VA_IMPEDANCES)
                .get(origin.getId(), destination.getId())));

        travelMetrics.put("from_acc_car", toDouble(origin.getAttribute(ZonalAttributes.AT_CAR)));
        travelMetrics.put("to_acc_car", toDouble(destination.getAttribute(ZonalAttributes.AT_CAR)));
        travelMetrics.put("to_pc_car", toDouble(destination.getAttribute(ZonalAttributes.PC_CAR)));

        travelMetrics.put("from_acc_ride", toDouble(origin.getAttribute(ZonalAttributes.AT_RIDE)));
        travelMetrics.put("to_acc_ride", toDouble(destination.getAttribute(ZonalAttributes.AT_RIDE)));
        travelMetrics.put("to_pc_ride", toDouble(destination.getAttribute(ZonalAttributes.PC_RIDE)));

        return travelMetrics;
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Cannot convert value to Double: " + value + " (" + value.getClass() + ")");
    }


    @Override
    public Map<String, Double> getKalifMetrics(Id<Zone> fromZoneId, Id<Zone> toZoneId) {
        return getMetricsForPair(fromZoneId, toZoneId, MatrixNames.KalifMatrices.values());
    }


    private Map<String, Double> getMetricsForPair(Id<Zone> from, Id<Zone> to, Enum<?>[] matrixEnums) {
        Map<String, Double> results = new LinkedHashMap<>();
        for (Enum<?> matrixEnum : matrixEnums) {
            String matrixName = matrixEnum.name().toLowerCase();
            FloatMatrix<Id<Zone>> matrix = this.matrices.get(matrixName);
            if (matrix != null) {
                double value = matrix.get(from, to);
                results.put(matrixName, value);
            }
        }
        return results;
    }
}