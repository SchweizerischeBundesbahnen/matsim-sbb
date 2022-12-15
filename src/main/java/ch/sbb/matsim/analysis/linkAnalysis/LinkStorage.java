package ch.sbb.matsim.analysis.linkAnalysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

public class LinkStorage {

    Id<Link> linkId;

    int simCount = 0;
    int carCount = 0;
    int rideCount = 0;
    int freight = 0;

    LinkStorage(Id<Link> linkId){
        this.linkId = linkId;
    }


}
