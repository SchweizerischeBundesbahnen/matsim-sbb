package ch.sbb.matsim.umlego;

public class RouteUtilityCalculators {

	public static RouteUtilityCalculator boxcox(double beta, double tau) {
		return (double r, double r_min) -> {
			return Math.exp(-beta * (Math.pow(r, tau) - 1.0) / tau);
		};
	}

	public static RouteUtilityCalculator lohse(double beta) {
		return (double r, double r_min) -> {
			return Math.exp(-Math.pow(beta * (r / r_min - 1.0), 2.0));
		};
	}

}
