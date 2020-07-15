package ch.sbb.matsim.zones;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;

/**
 * @author mrieser
 */
public interface Zone {

	Id<Zone> getId();

	Object getAttribute(String name);

	Envelope getEnvelope();

	boolean contains(Point pt);

	double distance(Point pt);

}
