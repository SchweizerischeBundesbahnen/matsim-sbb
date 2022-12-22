package ch.sbb.matsim.rerouting;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

public class DemandStorage {

    final Id<Link> matsimLink;
    final String visumLink;
    String wkt;
    int demand = 0;

    DemandStorage(Id<Link> linkId, String line) {
        this.matsimLink = linkId;
        this.visumLink = line;
    }

    public void setWkt(String wkt) {
        this.wkt = wkt;
    }

    @Override
    public String toString() {
        return matsimLink.toString() + ";" + demand + ";" + visumLink + ";" + wkt;
    }

}
