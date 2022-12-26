package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.Set;

/**
 * @author mrieser
 */
public class ParkingCostConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUP_NAME = "parkingCosts";

	@Parameter
	@Comment("Enables / disables parkingCost use")
	public boolean useParkingCost = true;

	@Parameter
	@Comment("Parking Cost link Attribute prefix." +
			" This needs to be followed by the parking cost for the mode, " +
			"e.g., \"pc_\" as prefix and \"car\" as mode would " +
			"require an attribute \"pc_ride\" to be set on every " +
			"link where parking costs should be considered.")
	public String linkAttributePrefix = "pc_";

	@Parameter
	@Comment("Modes that should use parking costs, separated by comma")
	public String modesWithParkingCosts = null;

	@Parameter
	@Comment("Activitiy types where no parking costs are charged, e.g., at home. char sequence must be part of the activity.")
	public String activityTypesWithoutParkingCost = null;

	public ParkingCostConfigGroup() {
		super(GROUP_NAME);
	}

	public Set<String> getModesWithParkingCosts() {
		return CollectionUtils.stringToSet(modesWithParkingCosts);
	}

	public Set<String> getActivityTypesWithoutParkingCost() {
		return CollectionUtils.stringToSet(activityTypesWithoutParkingCost);
	}
}
