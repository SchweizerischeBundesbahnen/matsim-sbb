package ch.sbb.matsim.config;

import ch.sbb.matsim.zones.Zones;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

public class SBBAccessTimeConfigGroup extends ReflectiveConfigGroup {

	static public final String GROUP_NAME = "SBBAccessTime";

	static private final String PARAM_MODES_WITH_ACCESS_TIME = "modesWithAccessTime";
	static private final String PARAM_ZONES_ID = "zonesId";
	static private final String PARAM_PREFIX = "attributePrefix";
	static private final String PARAM_IS_INSERTING = "isInsertingAccessEgressWalk";

	private Id<Zones> zonesId = null;
	private String attributePrefix = "ACC";
	private Boolean isInsertingAccessEgressWalk = false;
	private Set<String> modesWithAccessTime = new HashSet<>();

	public SBBAccessTimeConfigGroup() {
		super(GROUP_NAME);
	}

	@StringGetter(PARAM_PREFIX)
	public String getAttributePrefix() {
		return attributePrefix;
	}

	@StringSetter(PARAM_PREFIX)
	public void setAttributePrefix(String attributePrefix) {
		this.attributePrefix = attributePrefix;
	}

	@StringGetter(PARAM_ZONES_ID)
	public String getZonesId_asString() {
		return this.zonesId == null ? null : this.zonesId.toString();
	}

	public Id<Zones> getZonesId() {
		return this.zonesId;
	}

	@StringSetter(PARAM_ZONES_ID)
	public void setZonesId(String zonesId) {
		setZonesId(Id.create(zonesId, Zones.class));
	}

	public void setZonesId(Id<Zones> zonesId) {
		this.zonesId = zonesId;
	}

	@StringGetter(PARAM_IS_INSERTING)
	public Boolean getInsertingAccessEgressWalk() {
		return isInsertingAccessEgressWalk;
	}

	@StringSetter(PARAM_IS_INSERTING)
	public void setInsertingAccessEgressWalk(Boolean insertingAccessEgressWalk) {
		isInsertingAccessEgressWalk = insertingAccessEgressWalk;
	}

	@StringGetter(PARAM_MODES_WITH_ACCESS_TIME)
	private String getModesWithAccessTimeAsString() {
		return CollectionUtils.setToString(this.modesWithAccessTime);
	}

	public Set<String> getModesWithAccessTime() {
		return this.modesWithAccessTime;
	}

	@StringSetter(PARAM_MODES_WITH_ACCESS_TIME)
	public void setModesWithAccessTime(String modes) {
		setModes(CollectionUtils.stringToSet(modes));
	}

	public void setModes(Set<String> modes) {
		this.modesWithAccessTime.clear();
		this.modesWithAccessTime.addAll(modes);
	}

	@Override
	public Map<String, String> getComments() {
		Map<String, String> comments = super.getComments();
		comments.put(PARAM_MODES_WITH_ACCESS_TIME, "Specifies for which modes access and egress legs are added.");
		comments.put(PARAM_IS_INSERTING, "If true, will add access and egress legs for the specified modes. If false, will use the standard RoutingModules.");
		comments.put(PARAM_ZONES_ID, "Id of the zones to be used to map activities to zones (see 'zones' config group). Zones must contains following attributes 'ACC'+mode.");
		return comments;
	}
}
