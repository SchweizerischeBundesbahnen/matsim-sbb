package ch.sbb.matsim.visumdistribution;

import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.Transfer;
import java.util.function.Supplier;

public class VisumRaptorTransferCostCalculator implements RaptorTransferCostCalculator {
        @Override
        public double calcTransferCost(Supplier<Transfer> transfer, RaptorParameters raptorParams, int totalTravelTime, int transferCount, double existingTransferCosts, double currentTime) {
            return 10;
        }

    }