package ch.sbb.matsim.zones;

import org.matsim.api.core.v01.Id;

/**
 * @author mrieser
 */
public interface Zones {

    Id<Zones> getId();

    int size();

    Zone findZone(double x, double y);

    Zone findNearestZone(double x, double y, double maxDistance);

    Zone getZone(Id<Zone> id);
}
