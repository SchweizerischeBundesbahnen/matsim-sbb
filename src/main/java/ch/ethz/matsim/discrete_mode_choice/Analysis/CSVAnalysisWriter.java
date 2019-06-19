package ch.ethz.matsim.discrete_mode_choice.Analysis;


import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CSVAnalysisWriter {

    public static final String LINK_IDS_TO_NAMES = "idToName";
    public static final String DELIMITER = ";";

    public static void write(String path, List<Object> tuples) {
        Class<?> type;
        if (tuples.isEmpty()) {
            return;
        } else {
            type = tuples.get(0).getClass();
        }
        BufferedWriter writer = null;
        try {
            path += ".csv";
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path)));
            writer.write(String.join(DELIMITER, Arrays.stream(type.getDeclaredFields()).map(Field::getName).toArray(String[]::new)) + "\n");
            for (Object o : tuples) {
                writer.write(String.join(DELIMITER, Arrays.stream(type.getDeclaredFields()).map(field -> {
                    try {
                        return String.valueOf(field.get(o));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        throw new IllegalStateException(e);
                    }
                }).toArray(String[]::new)) + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(writer);
        }
    }

//    /*
//     * LINESTRING(-77.0444 38.9101,-77.03818 38.91554)
//     */
//    public static void writeQGis(String path, Collection<CoordsTuple> values) {
//        BufferedWriter writer = null;
//        try {
//            path += ".csv";
//            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path)));
//
//            writer.write(String.join(DELIMITER, new String[]{"id;WKT"}) + "\n");
//
//            int i = 0;
//            for (CoordsTuple line : values) {
//                writer.write(String.valueOf(i++) + ";LINESTRING(" + String.valueOf(line.xOrigin) + " " + String.valueOf(line.xDestination) + ", " + String.valueOf(line.yOrigin) + " " + String.valueOf(line.yDestination) + ")\n");
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            close(writer);
//        }
//    }

    public static void write(String path, String name, Collection<TransitStopFacility> values) {
        BufferedWriter writer = null;
        try {
            path += ".csv";
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path)));

            writer.write(String.join(DELIMITER, new String[]{"link", "name", "xCoord", "yCoord"}) + "\n");

            for (TransitStopFacility stop : values) {
                writer.write(String.join(DELIMITER, new String[]{
                        String.valueOf(stop.getLinkId()),
                        String.valueOf(stop.getName()),
                        String.valueOf(stop.getCoord().getX()),
                        String.valueOf(stop.getCoord().getY()),
                }) + "\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(writer);
        }
    }

    public static void close(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void write(List<Tuple<String, List<? extends Object>>> nameAndLenghts, String path) {
        BufferedWriter writer = null;
        try {
            path += ".csv";
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path)));

            for (Tuple<String, List<? extends Object>> tuple : nameAndLenghts) {
                writer.write(tuple.getFirst() + DELIMITER + String.join(DELIMITER, tuple.getSecond().stream().map(String::valueOf).collect(Collectors.toList())) + "\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(writer);
        }
    }
}
