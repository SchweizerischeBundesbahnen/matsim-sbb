package ch.sbb.matsim.plans.matrices;

import ch.sbb.matsim.analysis.skims.FloatMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * A utility class for performing common operations on FloatMatrix objects.
 */
public final class MatrixUtils {

    public static final float DEFAULT_SKIM_ENTRY = 9999999.0f;

    private MatrixUtils() {
    }


    /**
     * Creates a new matrix by applying a binary operation element-wise to two input matrices.
     * The identifiers of both matrices must be identical.
     *
     * @param a             The first matrix.
     * @param b             The second matrix.
     * @param mergeOperator The operation to apply to each pair of elements (e.g., Math::max, (v1, v2) -> v1 + v2).
     * @param <T>           The identifier type of the matrices.
     * @return A new FloatMatrix containing the results of the operation.
     * @throws IllegalArgumentException if the identifier sets of the two matrices are not identical.
     */
    public static <T> FloatMatrix<T> merge(FloatMatrix<T> a, FloatMatrix<T> b, BinaryOperator<Float> mergeOperator) {
        Set<T> idsA = a.getIdentifiers();
        Set<T> idsB = b.getIdentifiers();
        if (!idsA.equals(idsB)) {
            throw new IllegalArgumentException("Cannot merge matrices with different identifier sets.");
        }

        FloatMatrix<T> result = FloatMatrix.createFloatMatrix(idsA, 0.0f);
        for (T from : idsA) {
            for (T to : idsA) {
                float valA = a.get(from, to);
                float valB = b.get(from, to);
                result.set(from, to, mergeOperator.apply(valA, valB));
            }
        }
        return result;
    }

    /**
     * Creates a new matrix by applying a unary transformation to each element of an input matrix.
     * This is useful for operations like applying a floor, ceiling, or a conditional replacement.
     *
     * @param matrix            The input matrix.
     * @param transformOperator The operation to apply to each element (e.g., v -> Math.max(300f, v)).
     * @param <T>               The identifier type of the matrix.
     * @return A new FloatMatrix containing the transformed values.
     */
    public static <T> FloatMatrix<T> transform(FloatMatrix<T> matrix, UnaryOperator<Float> transformOperator) {
        Set<T> ids = matrix.getIdentifiers();
        FloatMatrix<T> result = FloatMatrix.createFloatMatrix(ids, 0.0f);
        for (T from : ids) {
            for (T to : ids) {
                float val = matrix.get(from, to);
                result.set(from, to, transformOperator.apply(val));
            }
        }
        return result;
    }


    // --- Convenience Methods to Replicate Python Script ---

    /**
     * Creates a new matrix by taking the element-wise maximum of two matrices.
     * Shortcut for {@code merge(a, b, Math::max)}.
     */
    public static <T> FloatMatrix<T> mergeByMaximum(FloatMatrix<T> a, FloatMatrix<T> b) {
        return merge(a, b, Math::max);
    }

    /**
     * Creates a new matrix by taking the element-wise average of two matrices.
     */
    public static <T> FloatMatrix<T> mergeByAverage(FloatMatrix<T> a, FloatMatrix<T> b) {
        return merge(a, b, (v1, v2) -> 0.5f * (v1 + v2));
    }

    /**
     * Creates a new matrix where any value below the specified lower bound is replaced by that bound.
     * This directly mimics numpy's {@code np.where(mat < lowerBound, lowerBound, mat)}.
     *
     * @param matrix     The input matrix.
     * @param lowerBound The minimum value to enforce.
     * @return A new matrix with the lower bound applied.
     */
    public static <T> FloatMatrix<T> applyLowerBound(FloatMatrix<T> matrix, float lowerBound) {
        return transform(matrix, v -> Math.max(v, lowerBound));
    }


    public static <T> FloatMatrix<T> getSymmetricMatrix(FloatMatrix<T> matrix, float defaultValue) {
        Set<T> identifierSet = matrix.getIdentifiers();
        FloatMatrix<T> symmetricMatrix = FloatMatrix.createFloatMatrix(identifierSet, 0.0f);
        List<T> ids = new ArrayList<>(identifierSet);
        int count = ids.size();

        for (int i = 0; i < count; i++) {
            for (int j = i; j < count; j++) {
                T from = ids.get(i);
                T to = ids.get(j);
                float valFromTo = matrix.get(from, to);
                float valToFrom = matrix.get(to, from);
                float newSymmetricValue;
                if (valFromTo == defaultValue || valToFrom == defaultValue) {
                    newSymmetricValue = defaultValue;
                } else {
                    newSymmetricValue = 0.5f * (valFromTo + valToFrom);
                }
                symmetricMatrix.set(from, to, newSymmetricValue);
                symmetricMatrix.set(to, from, newSymmetricValue);
            }
        }
        return symmetricMatrix;
    }

    public static <T> FloatMatrix<T> getSymmetricMatrix(FloatMatrix<T> matrix) {
        return getSymmetricMatrix(matrix, DEFAULT_SKIM_ENTRY);
    }

    /**
     * Creates and returns a new matrix that is the transpose of the input matrix.
     * The value at (i, j) in the new matrix will be the value at (j, i) in the original.
     *
     * @param matrix The matrix to transpose.
     * @param <T>    The identifier type of the matrix.
     * @return A new, transposed FloatMatrix.
     */
    public static <T> FloatMatrix<T> transpose(FloatMatrix<T> matrix) {
        Set<T> ids = matrix.getIdentifiers();
        FloatMatrix<T> transposed = FloatMatrix.createFloatMatrix(ids, 0.0f);
        for (T from : ids) {
            for (T to : ids) {
                transposed.set(to, from, matrix.get(from, to));
            }
        }
        return transposed;
    }
}