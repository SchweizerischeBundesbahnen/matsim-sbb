package ch.sbb.matsim.rerouting2;

import ch.sbb.matsim.rerouting.DemandStorage;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

public class DemandStorage2 {

    Id<Link> matsimLink;
    String visumLink;
    String wkt;
    double demand = 0.;

    DemandStorage2(Id<Link> linkId, String line) {
        this.matsimLink = linkId;
        this.visumLink = line;
    }

    DemandStorage2(Id<Link> linkId, String visumLink, String wkt) {
        this.matsimLink = linkId;
        this.visumLink = visumLink;
        this.wkt = wkt;
    }

    DemandStorage2(Id<Link> linkId) {
        new DemandStorage2(linkId, "", "");
    }

    public synchronized void increaseDemand(double value) {
        this.demand += value;
    }

    public void setWkt(String wkt) {
        this.wkt = wkt;
    }

    @Override
    public String toString() {
        return matsimLink.toString() + ";" + demand + ";" + (int) demand + ";" + visumLink + ";" + wkt;
    }

}
