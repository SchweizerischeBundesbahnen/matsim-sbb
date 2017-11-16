/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import javafx.util.Pair;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class LocateAct {
    Logger log = Logger.getLogger(LocateAct.class);
    Collection<SimpleFeature> features = null;
    GeometryFactory geometryFactory = new GeometryFactory();
    private String attribute = "";
    private final Map<Coord, SimpleFeature> coordCache = new HashMap();


    public LocateAct(String shapefile) {
        this.readShapeFile(shapefile);
    }


    public LocateAct(String shapefile, String attribute) {
        this.readShapeFile(shapefile);
        this.attribute = attribute;
    }

    private void readShapeFile(String shapefile){
        ShapeFileReader shapeFileReader = new ShapeFileReader();
        shapeFileReader.readFileAndInitialize(shapefile);
        this.features = shapeFileReader.getFeatureSet();
    }

    public SimpleFeature getZone(Coord coord){

        if (coordCache.containsKey(coord)) {
            return coordCache.get(coord);
        } else {

            for (SimpleFeature feature : features) {
                MultiPolygon p = (MultiPolygon) feature.getDefaultGeometry();

                Point point = geometryFactory.createPoint(new Coordinate(coord.getX(), coord.getY()));
                if (p.contains(point)) {
                    coordCache.put(coord, feature);
                    return feature;
                }
            }

            return null;
        }

    }

    public SimpleFeature getNearestZone(Coord coord) {
        SimpleFeature nearestFeature = null;
        double nearestDistance = Double.MAX_VALUE;
        for (SimpleFeature feature: features) {
            MultiPolygon mp = (MultiPolygon) feature.getDefaultGeometry();
            Point point = geometryFactory.createPoint(new Coordinate(coord.getX(), coord.getY()));
            if (nearestFeature == null) {
                nearestFeature = feature;
                nearestDistance = mp.distance(point);
            }
            else {
                double actDistance = mp.distance(point);
                if (actDistance < nearestDistance) {
                    nearestFeature = feature;
                    nearestDistance = mp.distance(point);
                }
                if (nearestDistance == 0.0) return nearestFeature;
            }
        }
        return nearestFeature;
    }

    public String getZoneAttribute(Coord coord){
        SimpleFeature zone = getZone(coord);
        if(zone == null){
            return "undefined";
        }
        return zone.getAttribute(attribute).toString();
    }

    public String getNearestZoneAttribute(Coord coord) {
        SimpleFeature zone = getNearestZone(coord);
        if (zone == null) {
            return "undefined";
        }
        return zone.getAttribute(attribute).toString();
    }

    public static void main(String[] args) {
        new LocateAct("\\\\V00925\\Simba\\10_Daten\\70_Geodaten\\400_Geodaten\\Raumgliederung_CH\\BFS_CH14\\BFS_CH14_Gemeinden.shp", "GMDNR");
    }
}
