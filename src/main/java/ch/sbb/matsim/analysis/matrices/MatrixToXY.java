/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.csv.CSVWriter;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import org.apache.log4j.Logger;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
public class MatrixToXY {

    private final static Logger log = Logger.getLogger(MatrixToXY.class);

    public static void main(String[] args) throws IOException {
        String zonesShapeFilename = "D:\\devsbb\\mrieser\\data\\npvm_2016\\NPVM_OberBez.shp";
        String zonesIdAttributeName = "ID";
        String matricesDirectory = "D:\\devsbb\\mrieser\\data\\indicators";
        String xyCsvOutputFilename = "D:\\devsbb\\mrieser\\data\\indicators\\xy.csv.gz";

        log.info("loading zones from " + zonesShapeFilename);
        Collection<SimpleFeature> zones = new ShapeFileReader().readFileAndInitialize(zonesShapeFilename);
        Map<String, SimpleFeature> zonesById = new HashMap<>();
        for (SimpleFeature zone : zones) {
            String zoneId = zone.getAttribute(zonesIdAttributeName).toString();
            zonesById.put(zoneId, zone);
        }

        log.info("Calc Coord per Zone");
        Map<String, Point> coords = new HashMap<>();
        for (Map.Entry<String, SimpleFeature> e : zonesById.entrySet()) {
            String zoneId = e.getKey();
            SimpleFeature f = e.getValue();
            Geometry g = (Geometry) f.getDefaultGeometry();
            if (g != null) {
                try {
                    Point pt = g.getInteriorPoint();
                    coords.put(zoneId, pt);
                } catch (Exception ex) {
                    log.warn("Problem calculating interior point. Using centroid for zone " + zoneId, ex);

                    Point pt = g.getCentroid();
                    coords.put(zoneId, pt);
                }
            }
        }

        log.info("loading car travel times");
        FloatMatrix<String> carTravelTimes = new FloatMatrix<>(zonesById.keySet(), Float.NaN);
        FloatMatrixIO.readAsCSV(carTravelTimes, new File(matricesDirectory, CalculateIndicatorMatrices.CAR_TRAVELTIMES_FILENAME).getAbsolutePath(), id -> id);

        log.info("loading car distances");
        FloatMatrix<String> carDistances = new FloatMatrix<>(zonesById.keySet(), Float.NaN);
        FloatMatrixIO.readAsCSV(carDistances, new File(matricesDirectory, CalculateIndicatorMatrices.CAR_DISTANCES_FILENAME).getAbsolutePath(), id -> id);

        log.info("loading pt travel times");
        FloatMatrix<String> ptTravelTimes = new FloatMatrix<>(zonesById.keySet(), Float.NaN);
        FloatMatrixIO.readAsCSV(ptTravelTimes, new File(matricesDirectory, CalculateIndicatorMatrices.PT_TRAVELTIMES_FILENAME).getAbsolutePath(), id -> id);

        log.info("loading pt access times");
        FloatMatrix<String> ptAccessTimes = new FloatMatrix<>(zonesById.keySet(), Float.NaN);
        FloatMatrixIO.readAsCSV(ptAccessTimes, new File(matricesDirectory, CalculateIndicatorMatrices.PT_ACCESSTIMES_FILENAME).getAbsolutePath(), id -> id);

        log.info("loading pt egress times");
        FloatMatrix<String> ptEgressTimes = new FloatMatrix<>(zonesById.keySet(), Float.NaN);
        FloatMatrixIO.readAsCSV(ptEgressTimes, new File(matricesDirectory, CalculateIndicatorMatrices.PT_EGRESSTIMES_FILENAME).getAbsolutePath(), id -> id);

        log.info("loading pt transfer counts");
        FloatMatrix<String> ptTransferCounts = new FloatMatrix<>(zonesById.keySet(), Float.NaN);
        FloatMatrixIO.readAsCSV(ptTransferCounts, new File(matricesDirectory, CalculateIndicatorMatrices.PT_TRANSFERCOUNTS_FILENAME).getAbsolutePath(), id -> id);

        log.info("loading beeline distances");
        FloatMatrix<String> beelineDistances = new FloatMatrix<>(zonesById.keySet(), Float.NaN);
        FloatMatrixIO.readAsCSV(beelineDistances, new File(matricesDirectory, CalculateIndicatorMatrices.BEELINE_DISTANCE_FILENAME).getAbsolutePath(), id -> id);

        log.info("Start writing xy csv to " + xyCsvOutputFilename);
        String[] columns = {"FROM", "FROM_X", "FROM_Y", "TO", "TO_X", "TO_Y", "CAR_TRAVELTIME", "CAR_DISTANCE", "PT_TRAVELTIME", "PT_ACCESSTIME", "PT_EGRESSTIME", "PT_TRANSFERCOUNT", "BEELINE_DISTANCE"};
        try (CSVWriter writer = new CSVWriter("", columns, xyCsvOutputFilename)) {
            for (Map.Entry<String, Point> fromE : coords.entrySet()) {
                String fromId = fromE.getKey();
                Point fromPoint = fromE.getValue();
                for (Map.Entry<String, Point> toE : coords.entrySet()) {
                    String toId = toE.getKey();
                    Point toPoint = toE.getValue();

                    float carTravelTime = carTravelTimes.get(fromId, toId);
                    float carDistance = carDistances.get(fromId, toId);
                    float ptTravelTime = ptTravelTimes.get(fromId, toId);
                    float ptAccessTime = ptAccessTimes.get(fromId, toId);
                    float ptEgressTime = ptEgressTimes.get(fromId, toId);
                    float ptTransferCount = ptTransferCounts.get(fromId, toId);
                    float beelineDistance = ptTransferCounts.get(fromId, toId);

                    writer.set("FROM", fromId);
                    writer.set("FROM_X", Double.toString(fromPoint.getX()));
                    writer.set("FROM_Y", Double.toString(fromPoint.getY()));
                    writer.set("TO", toId);
                    writer.set("TO_X", Double.toString(toPoint.getX()));
                    writer.set("TO_Y", Double.toString(toPoint.getY()));
                    writer.set("CAR_TRAVELTIME", Float.toString(carTravelTime));
                    writer.set("CAR_DISTANCE", Float.toString(carDistance));
                    writer.set("PT_TRAVELTIME", Float.toString(ptTravelTime));
                    writer.set("PT_ACCESSTIME", Float.toString(ptAccessTime));
                    writer.set("PT_EGRESSTIME", Float.toString(ptEgressTime));
                    writer.set("PT_TRANSFERCOUNT", Float.toString(ptTransferCount));
                    writer.set("BEELINE_DISTANCE", Float.toString(beelineDistance));
                    writer.writeRow();
                }
            }
        }
        log.info("done.");

    }

}
