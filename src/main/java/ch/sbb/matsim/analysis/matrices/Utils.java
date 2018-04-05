/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Random;

/**
 * @author mrieser / SBB
 */
public final class Utils {

    private final static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private Utils() {
    }

    public static Coord getRandomCoordinateInFeature(SimpleFeature f, Random r) {
        Geometry geom = (Geometry) f.getDefaultGeometry();
        Envelope envelope = geom.getEnvelopeInternal();
        double minX = envelope.getMinX();
        double minY = envelope.getMinY();
        double width = envelope.getWidth();
        double height = envelope.getHeight();
        while (true) {
            double x = minX + r.nextDouble() * width;
            double y = minY + r.nextDouble() * height;
            Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
            if (pt.intersects(geom)) {
                return new Coord(x, y);
            }
        }
    }

}
