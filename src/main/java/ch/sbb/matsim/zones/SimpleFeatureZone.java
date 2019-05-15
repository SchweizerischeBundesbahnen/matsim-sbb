package ch.sbb.matsim.zones;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;
import org.opengis.feature.simple.SimpleFeature;

public class SimpleFeatureZone implements Zone {

    private final Id<Zone> id;
    private final SimpleFeature feature;

    public SimpleFeatureZone(Id<Zone> id, SimpleFeature feature) {
        this.id = id;
        this.feature = feature;
    }

    @Override
    public Id<Zone> getId() {
        return this.id;
    }

    @Override
    public Object getAttribute(String name) {
        return this.feature.getAttribute(name);
    }

    @Override
    public Envelope getEnvelope() {
        return ((Geometry) this.feature.getDefaultGeometry()).getEnvelopeInternal();
    }

    @Override
    public boolean contains(Point pt) {
        Geometry geom = (Geometry) this.feature.getDefaultGeometry();
        return geom.intersects(pt);
    }

    @Override
    public double distance(Point pt) {
        Geometry geom = (Geometry) this.feature.getDefaultGeometry();
        return geom.distance(pt);
    }
}
