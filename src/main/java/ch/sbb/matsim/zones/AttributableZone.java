package ch.sbb.matsim.zones;

import org.apache.logging.log4j.LogManager;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

public class AttributableZone implements Zone {

    private final Id<Zone> id;
    private final Attributes attributes = new AttributesImpl();
    private final Geometry geometry;

    public AttributableZone(Id<Zone> id, SimpleFeature feature) {
        this.id = id;
        if (feature != null) {
            this.geometry = (Geometry) feature.getDefaultGeometry();
            feature.getFeatureType().getAttributeDescriptors().forEach(attributeDescriptor -> {
                String name = attributeDescriptor.getLocalName();
                if (name.equals("geometry")) {
                    // Skip the geometry attribute, as we already have it in geometry.
                    return;
                }
                Object value = feature.getAttribute(name);
                attributes.putAttribute(name, value);
            });

        } else {
            this.geometry = null;
        }
    }

    @Override
    public Id<Zone> getId() {
        return this.id;
    }

    @Override
    public Object getAttribute(String name) {
        Object o = this.attributes.getAttribute(name);
        if (o == null) {
            LogManager.getLogger(getClass()).warn("Attribute " + name + " not found in zones. Will return null");
        }
        return o;
    }

    @Override
    public Envelope getEnvelope() {
        return geometry.getEnvelopeInternal();
    }

    @Override
    public boolean contains(Point pt) {
        return geometry.intersects(pt);
    }

    @Override
    public double distance(Point pt) {
        return geometry.distance(pt);
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    public Geometry getGeometry() {
        return geometry;
    }
}
