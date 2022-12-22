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

    DemandStorage(Id<Link> linkId, String visumLink, String wkt) {
        this.matsimLink = linkId;
        this.visumLink = visumLink;
        this.wkt = wkt;
    }

    public void setWkt(String wkt) {
        this.wkt = wkt;
    }

    @Override
    public String toString() {
        return matsimLink.toString() + ";" + demand + ";" + visumLink + ";" + wkt;
    }

}
