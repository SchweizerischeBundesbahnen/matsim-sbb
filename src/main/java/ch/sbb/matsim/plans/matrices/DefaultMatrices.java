package ch.sbb.matsim.plans.matrices;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.zones.Zone;
import org.matsim.api.core.v01.Id;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultMatrices implements Matrices {
    private final Map<String, FloatMatrix<Id<Zone>>> matrices = new HashMap<>();

    @Override
    public FloatMatrix<Id<Zone>> getMatrix(String matrixName) {
        return matrices.get(matrixName);
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
        return matrices.get(matrixName.name());
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
    public Map<String, Double> getKalifMetrics(Id<Zone> fromZoneId, Id<Zone> toZoneId) {
        return getMetricsForPair(fromZoneId, toZoneId, MatrixNames.KalifMatrices.values());
    }


    private Map<String, Double> getMetricsForPair(Id<Zone> from, Id<Zone> to, Enum<?>[] matrixEnums) {
        Map<String, Double> results = new LinkedHashMap<>();
        for (Enum<?> matrixEnum : matrixEnums) {
            String matrixName = matrixEnum.name();
            FloatMatrix<Id<Zone>> matrix = this.matrices.get(matrixName);
            if (matrix != null) {
                double value = matrix.get(from, to);
                results.put(matrixName, value);
            }
        }
        return results;
    }
}