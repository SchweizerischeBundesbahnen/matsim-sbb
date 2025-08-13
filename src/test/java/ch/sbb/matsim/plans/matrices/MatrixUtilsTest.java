package ch.sbb.matsim.plans.matrices;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatrixUtilsTest {
    @Test
    void testMergeSum() {
        Set<String> ids = new HashSet<>();
        ids.add("A");
        ids.add("B");
        FloatMatrix<String> m1 = FloatMatrix.createFloatMatrix(ids, 1.0f);
        FloatMatrix<String> m2 = FloatMatrix.createFloatMatrix(ids, 2.0f);
        BinaryOperator<Float> sum = Float::sum;
        FloatMatrix<String> result = MatrixUtils.merge(m1, m2, sum);
        for (String from : ids) {
            for (String to : ids) {
                assertEquals(3.0f, result.get(from, to), "Sum of matrices should be 3.0f");
            }
        }
    }

    @Test
    void testMergeMax() {
        Set<String> ids = new HashSet<>();
        ids.add("A");
        ids.add("B");
        FloatMatrix<String> m1 = FloatMatrix.createFloatMatrix(ids, 1.0f);
        FloatMatrix<String> m2 = FloatMatrix.createFloatMatrix(ids, 5.0f);
        BinaryOperator<Float> max = Math::max;
        FloatMatrix<String> result = MatrixUtils.merge(m1, m2, max);
        for (String from : ids) {
            for (String to : ids) {
                assertEquals(5.0f, result.get(from, to), "Max of matrices should be 5.0f");
            }
        }
    }

    @Test
    void testMergeDifferentIdentifiersThrows() {
        Set<String> idsA = new HashSet<>();
        idsA.add("A");
        Set<String> idsB = new HashSet<>();
        idsB.add("B");
        FloatMatrix<String> m1 = FloatMatrix.createFloatMatrix(idsA, 1.0f);
        FloatMatrix<String> m2 = FloatMatrix.createFloatMatrix(idsB, 2.0f);
        assertThrows(IllegalArgumentException.class, () -> MatrixUtils.merge(m1, m2, Float::sum));
    }
}

