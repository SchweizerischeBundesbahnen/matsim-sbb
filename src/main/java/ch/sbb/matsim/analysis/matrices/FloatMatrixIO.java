/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;

import java.io.IOException;
import java.util.Map;

/**
 * Helper methods to write and read matrices.
 *
 * @author mrieser / SBB
 */
public class FloatMatrixIO {

    private final static String COL_FROM = "FROM";
    private final static String COL_TO = "TO";
    private final static String COL_VALUE = "VALUE";
    private final static String[] COLUMNS = {COL_FROM, COL_TO, COL_VALUE};

    public static <T> void writeAsCSV(FloatMatrix<T> matrix, String filename) throws IOException {
        try (CSVWriter writer = new CSVWriter("", COLUMNS, filename)) {
            T[] zoneIds = getSortedIds(matrix);
            for (T fromZoneId : zoneIds) {
                for (T toZoneId : zoneIds) {
                    writer.set(COL_FROM, fromZoneId.toString());
                    writer.set(COL_TO, toZoneId.toString());
                    writer.set(COL_VALUE, Float.toString(matrix.get(fromZoneId, toZoneId)));
                    writer.writeRow();
                }
            }
        }
    }

    public static <T> void readAsCSV(FloatMatrix<T> matrix, String filename, IdConverter<T> idConverter) throws IOException {
        try (CSVReader reader = new CSVReader(COLUMNS, filename, ";")) {
            Map<String, String> row = reader.readLine(); // header
            while ((row = reader.readLine()) != null) {
                T fromZoneId = idConverter.parse(row.get(COL_FROM));
                T toZoneId = idConverter.parse(row.get(COL_TO));
                float value = Float.parseFloat(row.get(COL_VALUE));
                matrix.set(fromZoneId, toZoneId, value);
            }
        }
    }

    private static <T> T[] getSortedIds(FloatMatrix<T> matrix) {
        // the array-creation is only safe as long as the generated array is only within this class!
        @SuppressWarnings("unchecked")
        T[] ids = (T[]) (new Object[matrix.id2index.size()]);
        for (Map.Entry<T, Integer> e : matrix.id2index.entrySet()) {
            ids[e.getValue()] = e.getKey();
        }
        return ids;
    }

    @FunctionalInterface
    public interface IdConverter<T> {
        T parse(String id);
    }
}
