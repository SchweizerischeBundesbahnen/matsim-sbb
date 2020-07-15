package ch.sbb.matsim.preparation.cutter;

import org.matsim.api.core.v01.Coord;

/**
 * An extent that includes everything.
 *
 * @author mrieser
 */
public class AllInExtent implements CutExtent {

	@Override
	public boolean isInside(double x, double y) {
		return true;
	}

	@Override
	public boolean isInside(Coord coord) {
		return true;
	}
}
