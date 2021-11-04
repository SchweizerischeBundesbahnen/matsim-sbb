//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package ch.sbb.matsim.routing;

import ch.sbb.matsim.routing.pt.raptor.RaptorInVehicleCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorInVehicleCostCalculator.RouteSegmentIterator;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;

public class SBBCapacityDependentInVehicleCostCalculator implements RaptorInVehicleCostCalculator {
    double minimumCostFactor = 0.4D;
    double lowerCapacityLimit = 0.3D;
    double higherCapacityLimit = 0.6D;
    double maximumCostFactor = 1.8D;

    public SBBCapacityDependentInVehicleCostCalculator() {
    }

    public SBBCapacityDependentInVehicleCostCalculator(double minimumCostFactor, double lowerCapacityLimit, double higherCapacityLimit, double maximumCostFactor) {
        this.minimumCostFactor = minimumCostFactor;
        this.lowerCapacityLimit = lowerCapacityLimit;
        this.higherCapacityLimit = higherCapacityLimit;
        this.maximumCostFactor = maximumCostFactor;
    }

    @Override
    public double getInVehicleCost(double inVehicleTime, double marginalUtility_utl_s, Person person, Vehicle vehicle, RaptorParameters paramters, RouteSegmentIterator iterator) {
        double costSum = 0.0D;
        double seatCount = (double)vehicle.getType().getCapacity().getSeats();
        double standingRoom = (double)vehicle.getType().getCapacity().getStandingRoom();
        double standingRoomFactor = (seatCount + standingRoom) / seatCount;

        double baseCost;
        double factor;
        for(; iterator.hasNext(); costSum += baseCost * factor) {
            iterator.next();
            double inVehTime = iterator.getInVehicleTime();
            double paxCount = iterator.getPassengerCount();
            double occupancy = paxCount / seatCount;
            baseCost = inVehTime * -marginalUtility_utl_s;
            factor = 1.0D;
            if (occupancy < this.lowerCapacityLimit) {
                factor = this.minimumCostFactor + (1.0D - this.minimumCostFactor) / this.lowerCapacityLimit * occupancy;
            }

            if (occupancy > this.higherCapacityLimit) {
                factor = 1.0D + (this.maximumCostFactor - 1.0D) / (standingRoomFactor - this.higherCapacityLimit) * (occupancy - this.higherCapacityLimit);
            }
        }

        return costSum;
    }
}
