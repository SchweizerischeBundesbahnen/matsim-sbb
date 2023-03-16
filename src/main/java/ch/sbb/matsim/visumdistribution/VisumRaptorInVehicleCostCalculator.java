package ch.sbb.matsim.visumdistribution;

import ch.sbb.matsim.routing.pt.raptor.RaptorInVehicleCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;

public class VisumRaptorInVehicleCostCalculator implements RaptorInVehicleCostCalculator {

    @Override
    public double getInVehicleCost(double inVehicleTime, double marginalUtility_utl_s, Person person, Vehicle vehicle, RaptorParameters parameters, RouteSegmentIterator iterator) {
        return inVehicleTime/60;
    }

}
