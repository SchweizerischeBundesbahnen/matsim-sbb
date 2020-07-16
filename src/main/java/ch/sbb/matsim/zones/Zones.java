package ch.sbb.matsim.zones;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;

/**
 * @author mrieser
 */
public interface Zones {

	Id<Zones> getId();

	int size();

	Zone findZone(double x, double y);

	default Zone findZone(Coord coord) {
		return findZone(coord.getX(), coord.getY());
	}

	Zone findNearestZone(double x, double y, double maxDistance);

	default Zone findNearestZone(Coord coord, double maxDistance) {
		return findNearestZone(coord.getX(), coord.getY(), maxDistance);
	}

	Zone getZone(Id<Zone> id);
}
