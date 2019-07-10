package ch.sbb.matsim.preparation.cutter;

import org.matsim.api.core.v01.Coord;

public interface CutExtent {
    boolean isInside(double x, double y);

    default boolean isInside(Coord coord) {
        return isInside(coord.getX(), coord.getY());
    }
}
