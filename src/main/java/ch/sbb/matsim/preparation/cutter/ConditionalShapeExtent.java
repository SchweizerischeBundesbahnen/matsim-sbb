package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import java.util.function.Predicate;

/**
 * Defines an extent covered by a part of zones. For every point that lies within one Zone, the zone can decide if it belongs to the extent or not. This allows to filter zones based on attribute
 * values. Zones should not overlap each other, or the result might be random if a point is within or outside the extent.
 *
 * @author mrieser
 */
public class ConditionalShapeExtent implements CutExtent {

	private final Zones zones;
	private final Predicate<Zone> predicate;

	public ConditionalShapeExtent(Zones zones, Predicate<Zone> predicate) {
		this.zones = zones;
		this.predicate = predicate;
	}

	@Override
	public boolean isInside(double x, double y) {
		Zone zone = zones.findZone(x, y);
		if (zone == null) {
			return false;
		}
		return this.predicate.test(zone);
	}

}
