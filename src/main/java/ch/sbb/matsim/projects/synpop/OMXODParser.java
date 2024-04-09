package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.zones.Zone;
import omx.OmxFile;
import omx.OmxLookup;
import omx.OmxMatrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OMXODParser {

    private static final Logger logger = LogManager.getLogger(OMXODParser.class);
    private final Map<String, Integer> zonalLookup = new HashMap<>();
    private final Map<String, OmxMatrix<?, ?>> matrices = new HashMap<>();

    public static void main(String[] args) {
        var parser = new OMXODParser();
        parser.openMatrix("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\MIV international\\input\\NPVM_2017_7_QZD.omx");
        System.out.println(parser.getMatrixValue("531001001", "900099606", "7"));
        System.out.println(Arrays.stream(parser.getMatrixRow("531001001", "7")).sum());
    }

    public void openMatrix(String fileName) {
        openMatrix(fileName, "NO");
    }

    public void openMatrix(String fileName, String lookupTableName) {
        logger.info("OMX File: " + fileName);
        OmxFile omxFile = new OmxFile(fileName);
        omxFile.openReadOnly();
        logger.info("Found the following matrices: " + omxFile.getMatrixNames());
        for (String m : omxFile.getMatrixNames()) {
            OmxMatrix<?, ?> omxMatrix = omxFile.getMatrix(m);
            matrices.put(m, omxMatrix);

        }
        logger.info("Found the following lookups: " + omxFile.getLookupNames());
        OmxLookup.OmxIntLookup lookup = (OmxLookup.OmxIntLookup) omxFile.getLookup(lookupTableName);
        int[] lookupValues = lookup.getLookup();
        logger.info("Preparing Lookup");
        for (int i = 0; i < lookupValues.length; i++) {
            zonalLookup.put(String.valueOf(lookupValues[i]), i);
        }

    }

    public List<String> getMatrixNames() {
        return matrices.keySet().stream().toList();
    }

    public List<Id<Zone>> getAllZoneIdsInLookup() {
        return zonalLookup.keySet().stream().map(s -> Id.create(s, Zone.class)).sorted().toList();
    }

    public List<String> getAllLookupValues() {
        return zonalLookup.keySet().stream().toList();
    }

    public double getMatrixValue(String fromZoneId, String toZoneId, String matrixName) {
        var matrix = matrices.get(matrixName);
        double[][] data = (double[][]) matrix.getData();
        Integer fromLookup = zonalLookup.get(fromZoneId);
        Integer toLookup = zonalLookup.get(toZoneId);
        return data[fromLookup][toLookup];
    }

    public double getMatrixValue(Id<Zone> fromZoneId, Id<Zone> toZoneId, String matrixName) {
        return getMatrixValue(fromZoneId.toString(), toZoneId.toString(), matrixName);
    }

    public double[] getMatrixRow(Id<Zone> fromZoneId, String matrixName) {
        return getMatrixRow(fromZoneId.toString(), matrixName);
    }

    public double[] getMatrixRow(String fromZoneId, String matrixName) {
        var matrix = matrices.get(matrixName);
        double[][] data = (double[][]) matrix.getData();
        Integer fromLookup = zonalLookup.get(fromZoneId);
        return data[fromLookup];
    }
}
