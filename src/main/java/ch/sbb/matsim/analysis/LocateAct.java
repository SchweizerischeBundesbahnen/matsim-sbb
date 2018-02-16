/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.vividsolutions.jts.geom.*;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

public class LocateAct {
    private final static Logger log = Logger.getLogger(LocateAct.class);

    public static String UNDEFINED = "undefined";

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

    public void fillCache(Population population) {

        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            for (PlanElement pe : plan.getPlanElements()) {
                if (pe instanceof Activity) {
                    Coord coord = ((Activity) pe).getCoord();
                    getZone(coord);

                }
            }
        }
    }

    private void readShapeFile(String shapefile) {
        ShapeFileReader shapeFileReader = new ShapeFileReader();
        shapeFileReader.readFileAndInitialize(shapefile);
        this.features = shapeFileReader.getFeatureSet();
    }

    public SimpleFeature getZone(Coord coord) {

        if (coordCache.containsKey(coord)) {
            return coordCache.get(coord);
        } else {

            for (SimpleFeature feature : features) {
                Geometry geometry = (Geometry) feature.getDefaultGeometry();

                Point point = geometryFactory.createPoint(new Coordinate(coord.getX(), coord.getY()));
                if(geometry != null){
                    if (geometry.contains(point)) {
                        coordCache.put(coord, feature);
                        return feature;
                    }
                }
            }

            coordCache.put(coord, null);
            return null;
        }
    }

    public SimpleFeature getNearestZone(Coord coord, double acceptance) {
        SimpleFeature nearestFeature = null;
        double nearestDistance = Double.MAX_VALUE;
        for (SimpleFeature feature: features) {
            MultiPolygon mp = (MultiPolygon) feature.getDefaultGeometry();
            Point point = geometryFactory.createPoint(new Coordinate(coord.getX(), coord.getY()));

            if(mp != null) {
                if (nearestFeature == null) {
                    Double actDistance = mp.distance(point);
                    if (actDistance <= acceptance) {
                        nearestFeature = feature;
                        nearestDistance = actDistance;
                    }
                } else {
                    double actDistance = mp.distance(point);
                    if (actDistance < nearestDistance && actDistance <= acceptance) {
                        nearestFeature = feature;
                        nearestDistance = mp.distance(point);
                    }
                    if (nearestDistance == 0.0) return nearestFeature;
                }
            }
        }
        return nearestFeature;
    }

    public String getZoneAttribute(Coord coord){
        SimpleFeature zone = getZone(coord);
        if(zone == null){
            return UNDEFINED;
        }
        return zone.getAttribute(attribute).toString();
    }

    public String getNearestZoneAttribute(Coord coord, double acceptance) {
        SimpleFeature zone = getNearestZone(coord, acceptance);
        if (zone == null) {
            return UNDEFINED;
        }
        return zone.getAttribute(attribute).toString();
    }

    public static void main(String[] args) {
        new LocateAct("\\\\V00925\\Simba\\10_Daten\\70_Geodaten\\400_Geodaten\\Raumgliederung_CH\\BFS_CH14\\BFS_CH14_Gemeinden.shp", "GMDNR");
    }
}
