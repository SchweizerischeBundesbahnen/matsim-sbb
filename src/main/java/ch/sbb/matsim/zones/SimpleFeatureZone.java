package ch.sbb.matsim.zones;

import org.apache.logging.log4j.LogManager;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;

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
		Object o = this.feature.getAttribute(name);
		if (o == null) {
			LogManager.getLogger(getClass()).warn("Attribute " + name + " not found in zones. Will return null");
		}
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
