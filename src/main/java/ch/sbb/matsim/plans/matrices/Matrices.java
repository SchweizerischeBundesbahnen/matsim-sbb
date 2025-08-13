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


}
