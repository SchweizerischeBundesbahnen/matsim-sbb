package ch.sbb.matsim.utils.matrix;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.zones.Zone;
import io.jhdf.HdfFile;
import io.jhdf.WritableHdfFile;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import io.jhdf.api.WritableGroup;
import org.matsim.api.core.v01.Id;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OmxUtils {

    public static final String DEFAULT_LOOKUP = "NO";

    /**
     * Writes a map of FloatMatrix objects to an OMX file. This method is generic and can handle any identifier type.
     *
     * @param <T>          The type of the identifiers used in the FloatMatrix.
     * @param matrices     The map of matrix names to FloatMatrix objects to be written.
     * @param omxFilePath  The path to the output OMX file.
     * @param lookUpName   The name for the lookup dataset in the OMX file.
     * @param lookupData   The raw integer array of zone/location identifiers to write into the lookup table.
     * @param lookupMapper A function that converts an integer from lookupData into an identifier of type T.
     */
    public static <T> void writeOmxMatrices(Map<String, FloatMatrix<T>> matrices, String omxFilePath, String lookUpName, int[] lookupData, Function<Integer, T> lookupMapper) {
        try (WritableHdfFile hdfFile = HdfFile.write(Paths.get(omxFilePath))) {
            // Create the required OMX groups
            hdfFile.putGroup("data");
            hdfFile.putGroup("lookup");

            // Write the lookup table
            ((WritableGroup) hdfFile.getChild("lookup")).putDataset(lookUpName, lookupData);

            // Write each matrix
            WritableGroup dataGroup = (WritableGroup) hdfFile.getChild("data");
            for (Map.Entry<String, FloatMatrix<T>> entry : matrices.entrySet()) {
                String matrixName = entry.getKey();
                FloatMatrix<T> matrix = entry.getValue();

                // Extract the data into a 2D float array using the generic helper
                float[][] dataArray = getFloats(lookupData, matrix, lookupMapper);
                dataGroup.putDataset(matrixName, dataArray);
            }

            // Add OMX version attribute
            hdfFile.putAttribute("OMX_VERSION", "0.2");
        }
    }

    public static void writeOmxMatrices(Map<String, FloatMatrix<Id<Zone>>> matrices, String omxFilePath, String lookUpName, int[] lookupData) {
        writeOmxMatrices(matrices, omxFilePath, lookUpName, lookupData, i -> Id.create(i, Zone.class));
    }


    /**
     * A helper method to extract matrix data into a 2D float array based on an ordered lookup array and a mapping function.
     *
     * @param lookupData The ordered integer array of identifiers.
     * @param matrix     The FloatMatrix to extract data from.
     * @param mapper     A function to convert an int from lookupData to a type T identifier.
     * @return A 2D float array of the matrix data.
     */
    private static <T> float[][] getFloats(int[] lookupData, FloatMatrix<T> matrix, Function<Integer, T> mapper) {
        int size = lookupData.length;
        float[][] result = new float[size][size];
        for (int i = 0; i < size; i++) {
            T fromIdentifier = mapper.apply(lookupData[i]);
            for (int j = 0; j < size; j++) {
                T toIdentifier = mapper.apply(lookupData[j]);
                result[i][j] = matrix.get(fromIdentifier, toIdentifier);
            }
        }
        return result;
    }


    /**
     * Reads all compatible matrices from an OMX file and returns them as a map of FloatMatrix objects.
     * This method can process matrices with data types of float, double, int, and long, and will convert them all to float.
     *
     * @param omxFilePath Path to the OMX file.
     * @return A map where keys are the matrix names and values are the corresponding FloatMatrix objects.
     */
    public static Map<String, FloatMatrix<Id<Zone>>> readOmxMatrices(String omxFilePath) {
        return readOmxMatrices(omxFilePath, DEFAULT_LOOKUP);
    }

    /**
     * Reads all compatible matrices from an OMX file and returns them as a map of FloatMatrix objects.
     * This method can process matrices with data types of float, double, int, and long, and will convert them all to float.
     *
     * @param omxFilePath Path to the OMX file.
     * @param lookUpName  The name of the lookup dataset (within the /lookup group) that contains the matrix identifiers.
     * @return A map where keys are the matrix names and values are the corresponding FloatMatrix objects.
     */
    public static Map<String, FloatMatrix<Id<Zone>>> readOmxMatrices(String omxFilePath, String lookUpName) {
        Map<String, FloatMatrix<Id<Zone>>> matrices = new HashMap<>();
        try (HdfFile hdfFile = new HdfFile(Paths.get(omxFilePath))) {
            // Read the zone identifiers from the lookup table
            Group lookups = (Group) hdfFile.getChild("lookup");
            if (lookups == null) {
                throw new RuntimeException("OMX file is missing 'lookup' group.");
            }
            Dataset lookup = (Dataset) lookups.getChild(lookUpName);
            if (lookup == null) {
                throw new RuntimeException("Could not find lookup '" + lookUpName + "' in the OMX file.");
            }


            List<Id<Zone>> ids = readLookupIds(lookup);
            int matrixSize = ids.size();

            // Process the matrices in the data group
            Group data = (Group) hdfFile.getChild("data");
            if (data == null) {
                throw new RuntimeException("OMX file is missing 'data' group.");
            }

            for (Node node : data) {
                if (node instanceof Dataset dataset) {
                    String matrixName = node.getName();
                    Class<?> javaType = dataset.getJavaType();

                    // Check if the data type is one of the supported numeric types
                    if (isSupportedNumericType(javaType)) {
                        Object rawData = dataset.getData();
                        float[][] matrixData = convertToFloatMatrix(rawData, javaType, matrixSize);
                        FloatMatrix<Id<Zone>> matrix = FloatMatrix.createFloatMatrix(ids, matrixData);
                        matrices.put(matrixName, matrix);
                    } else {
                        System.out.println("Skipping matrix '" + matrixName + "' due to unsupported data type: " + javaType.getSimpleName());
                    }
                } else if (node instanceof Group) {
                    // It is possible for the data group to contain other groups. We will ignore them.
                    System.out.println("Skipping node '" + node.getName() + "' of unsupported type: " + node.getClass().getSimpleName());
                }
            }
        }
        return matrices;
    }

    /**
     * Reads the identifiers from a lookup dataset, supporting long, int, and String types.
     */
    private static List<Id<Zone>> readLookupIds(Dataset lookup) {
        Class<?> type = lookup.getJavaType();
        Object rawData = lookup.getData();

        if (type == long.class) {
            return Arrays.stream((long[]) rawData).mapToObj(v -> Id.create(v, Zone.class)).toList();
        } else if (type == int.class) {
            return Arrays.stream((int[]) rawData).mapToObj(v -> Id.create(v, Zone.class)).toList();
        } else if (type == String.class) {
            return Arrays.stream((String[]) rawData).map(v -> Id.create(v, Zone.class)).toList();
        } else {
            throw new IllegalArgumentException("Unsupported lookup type: " + type.getName());
        }
    }

    private static boolean isSupportedNumericType(Class<?> javaType) {
        return javaType == float.class || javaType == double.class || javaType == int.class || javaType == long.class;
    }

    /**
     * Converts a raw 2D array of a supported numeric type into a float[][].
     */
    private static float[][] convertToFloatMatrix(Object rawData, Class<?> javaType, int size) {
        float[][] floatData = new float[size][size];

        if (javaType == float.class) {
            return (float[][]) rawData; // No conversion needed
        }

        if (javaType == double.class) {
            double[][] sourceData = (double[][]) rawData;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    floatData[i][j] = (float) sourceData[i][j];
                }
            }
            return floatData;
        }

        if (javaType == int.class) {
            int[][] sourceData = (int[][]) rawData;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    floatData[i][j] = (float) sourceData[i][j];
                }
            }
            return floatData;
        }

        if (javaType == long.class) {
            long[][] sourceData = (long[][]) rawData;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    floatData[i][j] = sourceData[i][j];
                }
            }
            return floatData;
        }

        // This path should not be reached if called after isSupportedNumericType check
        throw new IllegalArgumentException("Cannot convert unsupported data type: " + javaType.getName());
    }


}