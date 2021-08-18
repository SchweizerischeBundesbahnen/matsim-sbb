/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import ch.sbb.matsim.zones.Zones;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

public class SBBIntermodalConfiggroup extends ReflectiveConfigGroup {

	static public final String GROUP_NAME = "SBBIntermodal";

	static private final String PARAM_ZONESID = "zonesId";
	static private final String PARAM_ZONESID_DESC = "Zones ID";
	private static Logger logger = Logger.getLogger(SBBIntermodalConfiggroup.class);
	private final List<SBBIntermodalModeParameterSet> modeParamSets = new ArrayList<>();
	private Id<Zones> zonesId = null;

	public SBBIntermodalConfiggroup() {
		super(GROUP_NAME);
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (SBBIntermodalModeParameterSet.TYPE.equals(type)) {
			return new SBBIntermodalModeParameterSet();
		}
		throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
	}

	@StringGetter(PARAM_ZONESID)
	public String getZonesIdString() {
		return this.zonesId == null ? null : this.zonesId.toString();
	}

	public Id<Zones> getZonesId() {
		return this.zonesId;
	}

	@StringSetter(PARAM_ZONESID)
	void setZonesId(String zonesId) {
		if (zonesId != null) {
			this.zonesId = Id.create(zonesId, Zones.class);
		}
	}

	void setZonesId(Id<Zones> zonesId) {
		this.zonesId = zonesId;
	}

	@Override
	public void addParameterSet(ConfigGroup set) {
		if (set instanceof SBBIntermodalModeParameterSet) {
			addModeParameters((SBBIntermodalModeParameterSet) set);
		} else {
			throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
		}
	}

	public void addModeParameters(SBBIntermodalModeParameterSet set) {
		this.modeParamSets.add(set);
		super.addParameterSet(set);
	}

	@Override
	public boolean removeParameterSet(ConfigGroup set) {
		this.modeParamSets.remove(set);
		return super.removeParameterSet(set);

	}

	@Override
	public Map<String, String> getComments() {
		Map<String, String> comments = super.getComments();
		comments.put(PARAM_ZONESID, PARAM_ZONESID_DESC);
		return (comments);

	}

	public List<SBBIntermodalModeParameterSet> getModeParameterSets() {
		return this.modeParamSets;
	}

}
