package ch.sbb.matsim.routing;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.vehicles.Vehicle;

public class BycicleLinkTravelTime extends FreeSpeedTravelTime {
	/**
	 * Based on "Flügel et al. -- Empirical speed models for cycling in the Oslo road network" (not yet published!)
	 * Positive gradients (uphill): Roughly linear decrease in speed with increasing gradient
	 * At 9% gradient, cyclists are 42.7% slower
	 * Negative gradients (downhill):
	 * Not linear; highest speeds at 5% or 6% gradient; at gradients higher than 6% braking
	 */
	@Override
	public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
		double velocity = link.getFreespeed();
		if (vehicle!=null) {
			velocity = Math.min(velocity, vehicle.getType().getMaximumVelocity());
		}
		return link.getLength() / velocity / computeGradientFactor(link);
	}

	private double computeGradientFactor(Link link) {
		double factor = 1;
		if (link.getFromNode().getCoord().hasZ() && link.getToNode().getCoord().hasZ()) {
			double fromZ = link.getFromNode().getCoord().getZ();
			double toZ = link.getToNode().getCoord().getZ();
			if (toZ > fromZ) { // No positive speed increase for downhill, only decrease for uphill
				double reduction = 1 - 5 * ((toZ - fromZ) / link.getLength());
				factor = Math.max(0.1, reduction); // maximum reduction is 0.1
			}
		}

		return factor;
	}
}
