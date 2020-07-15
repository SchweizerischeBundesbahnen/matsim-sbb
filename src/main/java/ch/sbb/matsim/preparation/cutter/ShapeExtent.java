package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.zones.Zones;

/**
 * Defines an extent covered by zones. Every point within at least one zone is considered to be within the extent.
 *
 * @author mrieser
 */
public class ShapeExtent implements CutExtent {

	private final Zones zones;

	public ShapeExtent(Zones zones) {
		this.zones = zones;
	}

	@Override
	public boolean isInside(double x, double y) {
		return zones.findZone(x, y) != null;
	}
}
