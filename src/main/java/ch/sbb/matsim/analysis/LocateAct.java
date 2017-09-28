/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;

public class LocateAct {
    Logger log = Logger.getLogger(LocateAct.class);
    Collection<SimpleFeature> features = null;
    GeometryFactory geometryFactory = new GeometryFactory();
    private String attribute = "";

    public LocateAct(String shapefile, String attribute) {

        ShapeFileReader shapeFileReader = new ShapeFileReader();
        shapeFileReader.readFileAndInitialize(shapefile);
        this.features = shapeFileReader.getFeatureSet();
        log.info(shapeFileReader.getSchema().getAttributeDescriptors());
        this.attribute = attribute;
    }

    public SimpleFeature getZone(Coord coord){
        for (SimpleFeature feature : features) {
            MultiPolygon p = (MultiPolygon) feature.getDefaultGeometry();

            Point point = geometryFactory.createPoint( new Coordinate(coord.getX(), coord.getY()));
            if(p.contains(point)){
                return feature;
            }
        }

        return null;
    }

    public String getZoneAttribute(Coord coord){
        SimpleFeature zone = getZone(coord);
        if(zone == null){
            return "undefined";
        }
        return zone.getAttribute(attribute).toString();
    }

    public static void main(String[] args) {
        new LocateAct("\\\\V00925\\Simba\\10_Daten\\70_Geodaten\\400_Geodaten\\Raumgliederung_CH\\BFS_CH14\\BFS_CH14_Gemeinden.shp", "GMDNR");
    }
}
