package ch.sbb.matsim.intermodal;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import java.util.HashSet;
import java.util.Set;

public class Intermodal {

    private final static Logger log = Logger.getLogger(Intermodal.class);
    public static void prepareNetwork(Network network, String newMode) {
        log.info("Prepare network for intermodal routing");
        for (Link link : network.getLinks().values()) {
            if (link.getAllowedModes().contains(TransportMode.car)) {
                Set<String> allowedModes = new HashSet<>();
                for (String mode : link.getAllowedModes())
                    allowedModes.add(mode);
                allowedModes.add(newMode);
                link.setAllowedModes(allowedModes);
            }
        }

    }

}
