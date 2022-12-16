package ch.sbb.matsim.analysis.linkAnalysis;

import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer.VehicleType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

public class LinkStorage {

    private final static Logger log = LogManager.getLogger(LinkStorage.class);

    private final Id<Link> linkId;

    private int freightCount = 0;
    private int carCount = 0;
    private int rideCount = 0;

    LinkStorage(Id<Link> linkId){
        this.linkId = linkId;
    }

    public void increase(VehicleType vehicleType) {
        switch (vehicleType) {
            case freight -> freightCount++;
            case car -> carCount++;
            default -> log.warn("Vehicle type cannot be recognized");
        }
    }

    public Id<Link> getLinkId() {
        return linkId;
    }

    public int getFreightCount() {
        return freightCount;
    }

    public int getCarCount() {
        return carCount;
    }

    public int getRideCount() {
        return rideCount;
    }
}
