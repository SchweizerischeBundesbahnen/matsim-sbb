package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.zones.Zone;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;

import java.lang.reflect.Array;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OMXODParser {

    private static final Logger logger = LogManager.getLogger(OMXODParser.class);
    private final Map<String, Integer> zonalLookup = new HashMap<>();
    private final Map<String, double[][]> matrices = new HashMap<>();

    public static void main(String[] args) {
        var parser = new OMXODParser();
        parser.openMatrix("/Users/joschka.bischoff/Desktop/LI_LW_LZ_NPVM2023.omx");
        System.out.println(parser.getMatrixValue("531001001", "900099606", "10"));
        System.out.println(Arrays.stream(parser.getMatrixRow("531001001", "10")).sum());
    }


    public void openMatrix(String fileName) {
        logger.info("OMX File: " + fileName);
        try (HdfFile hdfFile = new HdfFile(Paths.get(fileName))) {
            Group data = (Group) hdfFile.getChild("data");
            for (Node node : data) {
                if (node instanceof Dataset matrix) {
                    Class<?> javaType = matrix.getJavaType();
                    if (javaType != double.class) {
                        throw new RuntimeException("Only double[][] matrices are supported");
                    }
                    String name = matrix.getName();
                    double[][] d = (double[][]) matrix.getData();
                    matrices.put(name, d);
                }
            }

            Group lookups = (Group) hdfFile.getChild("lookup");
            Map<String, Node> indexes = lookups.getChildren();
            Dataset lookup = (Dataset) indexes.values().stream().findFirst().orElseThrow();
            Class<?> t = lookup.getJavaType();
            Object lookupData = lookup.getData();

            if (!t.isPrimitive() || !(t == int.class || t == long.class)) {
                throw new IllegalArgumentException("Unsupported lookup type: " + t.getName());
            }

            int length = Array.getLength(lookupData);
            for (int i = 0; i < length; i++) {
                Number value = (Number) Array.get(lookupData, i);
                zonalLookup.put(value.toString(), i);
            }

        }

    }

    public List<Id<Zone>> getAllZoneIdsInLookup() {
        return zonalLookup.keySet().stream().map(s -> Id.create(s, Zone.class)).sorted().collect(Collectors.toList());
    }


    public double getMatrixValue(String fromZoneId, String toZoneId, String matrixName) {
        var matrix = matrices.get(matrixName);
        Integer fromLookup = zonalLookup.get(fromZoneId);
        Integer toLookup = zonalLookup.get(toZoneId);
        return matrix[fromLookup][toLookup];
    }

    public double getMatrixValue(Id<Zone> fromZoneId, Id<Zone> toZoneId, String matrixName) {
        return getMatrixValue(fromZoneId.toString(), toZoneId.toString(), matrixName);
    }

    public double[] getMatrixRow(Id<Zone> fromZoneId, String matrixName) {
        return getMatrixRow(fromZoneId.toString(), matrixName);
    }

    public double[] getMatrixRow(String fromZoneId, String matrixName) {
        var matrix = matrices.get(matrixName);
        Integer fromLookup = zonalLookup.get(fromZoneId);
        return matrix[fromLookup];
    }
}
