package ch.sbb.matsim.preparation.cutter;

/**
 * Defines a radial extent with a specified radius around a given center.
 *
 * @author mrieser
 */
public class RadialExtent implements CutExtent {

    private final double centerX;
    private final double centerY;
    private final double radiusSquared;

    public RadialExtent(double centerX, double centerY, double radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radiusSquared = radius * radius;
    }

    @Override
    public boolean isInside(double x, double y) {
        double dx = x - this.centerX;
        double dy = y - this.centerY;
        double distSquared = dx * dx + dy * dy;
        return distSquared <= this.radiusSquared;
    }
}
