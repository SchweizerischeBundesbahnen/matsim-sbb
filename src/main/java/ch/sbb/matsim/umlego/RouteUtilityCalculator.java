package ch.sbb.matsim.umlego;

@FunctionalInterface
public interface RouteUtilityCalculator {

	/**
	 * @param r the impedance of the evaluated route
	 * @param r_min the minimal impedance of any route on the same OD-relation
	 */
	double calculateUtility(double r, double r_min);
}
