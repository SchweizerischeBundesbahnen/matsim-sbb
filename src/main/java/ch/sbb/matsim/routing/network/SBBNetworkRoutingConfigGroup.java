package ch.sbb.matsim.routing.network;

import ch.sbb.matsim.config.variables.SBBModes;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

/**
 * @author jbischoff / SBB
 */
public class SBBNetworkRoutingConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUPNAME = "SBBNetworkRouting";
	public static final String NETWORKROUTINGMODES = "networkRoutingModes";
	public static final String DESCNETWORKROUTINGMODES = "Teleported modes that are routed with the same disutiliy and travel time as car. Default is [ride]";

	private Set<String> networkRoutingModes = Collections.singleton(SBBModes.RIDE);

	public SBBNetworkRoutingConfigGroup() {
		super(GROUPNAME);
	}

	@StringGetter(NETWORKROUTINGMODES)
	public String getNetworkRoutingModesAsString() {
		return CollectionUtils.setToString(networkRoutingModes);
	}

	public Set<String> getNetworkRoutingModes() {
		return networkRoutingModes;
	}

	@StringSetter(NETWORKROUTINGMODES)
	public void setNetworkRoutingModes(String networkRoutingModes) {
		this.networkRoutingModes = CollectionUtils.stringToSet(networkRoutingModes);
	}

	@Override
	public Map<String, String> getComments() {
		Map<String, String> comments = super.getComments();
		comments.put(NETWORKROUTINGMODES, DESCNETWORKROUTINGMODES);
		return comments;
	}
}
