/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author mrieser / SBB
 */
public class SBBTransitConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "sbbPt";

    static private final String PARAM_PASSENGER_MODES = "passengerModes";
    static private final String PARAM_DETERMINISTIC_SERVICE_MODES = "deterministicServiceModes";
    static private final String PARAM_NETWORK_SERVICE_MODES = "networkServiceModes";

    private Set<String> passengerModes = new HashSet<>();
    private Set<String> deterministicServiceModes = new HashSet<>();
    private Set<String> networkServiceModes = new HashSet<>();

    public SBBTransitConfigGroup() {
        super(GROUP_NAME);
        this.passengerModes.add(TransportMode.pt);
    }

    @StringGetter(PARAM_PASSENGER_MODES)
    private String getPassengerModesAsString() {
        return CollectionUtils.setToString(this.passengerModes);
    }

    public Set<String> getPassengerModes() {
        return this.passengerModes;
    }

    @StringSetter(PARAM_PASSENGER_MODES)
    private void setPassengerModes(String modes) {
        setPassengerModes(CollectionUtils.stringToSet(modes));
    }

    public void setPassengerModes(Set<String> modes) {
        this.passengerModes.clear();
        this.passengerModes.addAll(modes);
    }

    @StringGetter(PARAM_DETERMINISTIC_SERVICE_MODES)
    private String getDeterministicServiceModesAsString() {
        return CollectionUtils.setToString(this.deterministicServiceModes);
    }

    public Set<String> getDeterministicServiceModes() {
        return this.deterministicServiceModes;
    }

    @StringSetter(PARAM_DETERMINISTIC_SERVICE_MODES)
    private void setDeterministicServiceModes(String modes) {
        setDeterministicServiceModes(CollectionUtils.stringToSet(modes));
    }

    public void setDeterministicServiceModes(Set<String> modes) {
        this.deterministicServiceModes.clear();
        this.deterministicServiceModes.addAll(modes);
    }

    @StringGetter(PARAM_NETWORK_SERVICE_MODES)
    private String getNetworkServiceModesAsString() {
        return CollectionUtils.setToString(this.networkServiceModes);
    }

    public Set<String> getNetworkServiceModes() {
        return this.networkServiceModes;
    }

    @StringSetter(PARAM_NETWORK_SERVICE_MODES)
    private void setNetworkServiceModes(String modes) {
        setNetworkServiceModes(CollectionUtils.stringToSet(modes));
    }

    public void setNetworkServiceModes(Set<String> modes) {
        this.networkServiceModes.clear();
        this.networkServiceModes.addAll(modes);
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_PASSENGER_MODES, "Leg modes used by agents that should be handled as public transport legs.");
        comments.put(PARAM_DETERMINISTIC_SERVICE_MODES, "Leg modes used by the created transit drivers that should be simulated strictly according to the schedule.");
        comments.put(PARAM_NETWORK_SERVICE_MODES, "Leg modes used by the created transit drivers that should be simulated on the (Queue-)Network and which might deviate from the schedule (being early or late at stops).");
        return comments;
    }
}
