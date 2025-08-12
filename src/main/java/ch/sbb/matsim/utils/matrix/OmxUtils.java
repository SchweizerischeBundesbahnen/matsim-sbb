package ch.sbb.matsim.utils.matrix;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.zones.Zone;
import io.jhdf.HdfFile;
import io.jhdf.WritableHdfFile;
import io.jhdf.api.*;
import org.matsim.api.core.v01.Id;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        WritableHdfFile hdfFile = null; // Declare outside the try block
        try {
            hdfFile = HdfFile.write(Paths.get(omxFilePath));
            hdfFile.putAttribute("OMX_VERSION", 0.2);
            hdfFile.putAttribute("SHAPE", new int[]{lookupData.length, lookupData.length});

            // Create the required OMX groups
            WritableGroup dataGroup = hdfFile.putGroup("data");
            WritableGroup lookupGroup = hdfFile.putGroup("lookup");
            lookupGroup.putDataset(lookUpName, lookupData);

            // Write each matrix
            for (Map.Entry<String, FloatMatrix<T>> entry : matrices.entrySet()) {
                String matrixName = entry.getKey();
                FloatMatrix<T> matrix = entry.getValue();

                float[][] dataArray = getFloats(lookupData, matrix, lookupMapper);
                WritableDataset createdDataset = dataGroup.putDataset(matrixName, dataArray);
                // a) Add a string attribute to link to the lookup table. This is a common convention.
                createdDataset.putAttribute("lookup", lookUpName);

            }

        } finally {
            if (hdfFile != null) {
                hdfFile.close();
            }
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
     * Reads matrices from an OMX file, creating a FloatMatrix with a custom identifier type <T>.
     * It works by converting all lookup types (long, int, String) to a String representation first,
     * and then applies the provided mapper function.
     *
     * @param <T> The target type for the matrix identifiers.
     * @param omxFilePath Path to the OMX file.
     * @param lookUpName The name of the lookup dataset.
     * @param stringIdentifierMapper A function that converts a String identifier into the target type T.
     * @return A map of matrix names to their corresponding FloatMatrix<T> objects.
     */
    public static <T> Map<String, FloatMatrix<T>> readOmxMatrices(String omxFilePath, String lookUpName, Function<String, T> stringIdentifierMapper) {
        Map<String, FloatMatrix<T>> matrices = new HashMap<>();
        try (HdfFile hdfFile = new HdfFile(Paths.get(omxFilePath))) {
            Group lookups = (Group) hdfFile.getChild("lookup");
            if (lookups == null) throw new RuntimeException("OMX file is missing 'lookup' group.");

            Dataset lookup = (Dataset) lookups.getChild(lookUpName);
            if (lookup == null)
                throw new RuntimeException("Could not find lookup '" + lookUpName + "' in the OMX file.");

            // Step 1: Read all lookup identifiers into a canonical List<String>.
            List<String> stringIds = readLookupIdsAsStrings(lookup);
            // Step 2: Map the string list to the target type T.
            List<T> ids = stringIds.stream().map(stringIdentifierMapper).collect(Collectors.toList());
            int matrixSize = ids.size();

            Group data = (Group) hdfFile.getChild("data");
            if (data == null) throw new RuntimeException("OMX file is missing 'data' group.");

            for (Node node : data) {
                if (node instanceof Dataset dataset) {
                    String matrixName = node.getName();
                    if (isSupportedNumericType(dataset.getJavaType())) {
                        Object rawData = dataset.getData();
                        float[][] matrixData = convertToFloatMatrix(rawData, dataset.getJavaType(), matrixSize);
                        FloatMatrix<T> matrix = FloatMatrix.createFloatMatrix(ids, matrixData);
                        matrices.put(matrixName, matrix);
                    } else {
                        System.err.println("Skipping matrix '" + matrixName + "' due to unsupported data type: " + dataset.getJavaType().getSimpleName());
                    }
                }
            }
        }
        return matrices;
    }

    /**
     * Reads OMX matrices where identifiers are MATSim Id<Zone>.
     */
    public static Map<String, FloatMatrix<Id<Zone>>> readOmxMatrices(String omxFilePath, String lookUpName) {
        // Just provide the function that maps a String to an Id<Zone>
        return readOmxMatrices(omxFilePath, lookUpName, s -> Id.create(s, Zone.class));
    }

    /**
     * Convenience method that reads Id<Zone> matrices using the default lookup name "NO".
     */
    public static Map<String, FloatMatrix<Id<Zone>>> readOmxMatrices(String omxFilePath) {
        return readOmxMatrices(omxFilePath, DEFAULT_LOOKUP);
    }

    /**
     * Reads OMX matrices where identifiers are simple Strings.
     */
    public static Map<String, FloatMatrix<String>> readOmxStringMatrices(String omxFilePath, String lookUpName) {
        // The mapping function is just the identity function (s -> s).
        return readOmxMatrices(omxFilePath, lookUpName, Function.identity());
    }

    /**
     * Helper to read lookup identifiers from the HDF5 dataset and convert them to a List<String>.
     */
    private static List<String> readLookupIdsAsStrings(Dataset lookup) {
        Class<?> type = lookup.getJavaType();
        Object rawData = lookup.getData();

        if (type == long.class) {
            return Arrays.stream((long[]) rawData).mapToObj(String::valueOf).collect(Collectors.toList());
        } else if (type == int.class) {
            return Arrays.stream((int[]) rawData).mapToObj(String::valueOf).collect(Collectors.toList());
        } else if (type == String.class) {
            return Arrays.asList((String[]) rawData);
        } else {
            throw new IllegalArgumentException("Unsupported lookup type for identifiers: " + type.getName());
        }
    }

    private static boolean isSupportedNumericType(Class<?> javaType) {
        return javaType == float.class || javaType == double.class || javaType == int.class || javaType == long.class;
    }

    private static float[][] convertToFloatMatrix(Object rawData, Class<?> javaType, int size) {
        float[][] floatData = new float[size][size];
        if (javaType == float.class) return (float[][]) rawData;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (javaType == double.class) floatData[i][j] = (float) ((double[][]) rawData)[i][j];
                else if (javaType == int.class) floatData[i][j] = ((int[][]) rawData)[i][j];
                else if (javaType == long.class) floatData[i][j] = ((long[][]) rawData)[i][j];
            }
        }
        return floatData;
    }


}