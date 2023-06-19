/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Map;

public class PostProcessingConfigGroup extends ReflectiveConfigGroup {

	static public final String GROUP_NAME = "PostProcessing";


	private Id<Zones> zonesId = null;
	private String zoneAttribute = "GMDNR";
	private Boolean linkVolumes = false;
	private String personAttributes = "season_ticket,subpopulation,carAvail,hasLicense";
	private int writeOutputsInterval = 10;
	private Boolean writeAgentsCSV = false;
	private Boolean writePlanElementsCSV = false;
	private Boolean writeVisumPuTSurvey = false;
	static private final String SIMULATION_SAMPLE_SIZE = "simulationSampleSize";
	private double simulationSampleSize;

	static private final String WRITE_ANALYSIS = "writeDefaultAnalysis";
	static private final String RAIL_DEMAND_MATRIX_AGGREGATE = "railDemandMatrixAggregateAttribute";
	private boolean writeAnalsysis = true;
	private String railMatrixAggregate = "amgr_id";
	private Boolean dailylinkVolumes = false;

	public PostProcessingConfigGroup() {
		super(GROUP_NAME);
	}

	@StringGetter(SIMULATION_SAMPLE_SIZE)
	public double getSimulationSampleSize() {
		return simulationSampleSize;
	}

	@StringSetter(SIMULATION_SAMPLE_SIZE)
	public void setSimulationSampleSize(double simulationSampleSize) {
		this.simulationSampleSize = simulationSampleSize;
	}

	@StringGetter(WRITE_ANALYSIS)
	public boolean isWriteAnalsysis() {
		return writeAnalsysis;
	}

	@StringSetter(WRITE_ANALYSIS)
	public void setWriteAnalsysis(boolean writeAnalsysis) {
		this.writeAnalsysis = writeAnalsysis;
	}

	@StringGetter(RAIL_DEMAND_MATRIX_AGGREGATE)
	public String getRailMatrixAggregate() {
		return railMatrixAggregate;
	}

	@StringSetter(RAIL_DEMAND_MATRIX_AGGREGATE)
	public void setRailMatrixAggregate(String railMatrixAggregate) {
		this.railMatrixAggregate = railMatrixAggregate;
	}


	@StringGetter("writeVisumPuTSurvey")
	public Boolean getWriteVisumPuTSurvey() {
		return writeVisumPuTSurvey;
	}

	@StringSetter("writeVisumPuTSurvey")
	public void setWriteVisumPuTSurvey(Boolean write) {
		this.writeVisumPuTSurvey = write;
	}

	@StringGetter("writeOutputsInterval")
	public int getWriteOutputsInterval() {
		return this.writeOutputsInterval;
	}

	@StringSetter("writeOutputsInterval")
	public void setWriteOutputsInterval(final int writeOutputsInterval) {
		this.writeOutputsInterval = writeOutputsInterval;
	}

	@StringGetter("writeAgentsCSV")
	public Boolean getWriteAgentsCSV() {
		return writeAgentsCSV;
	}

	@StringSetter("writeAgentsCSV")
	public void setWriteAgentsCSV(Boolean value) {
		this.writeAgentsCSV = value;
	}

	@StringGetter("writePlanElementsCSV")
	public Boolean getWritePlanElementsCSV() {
		return writePlanElementsCSV;
	}

	@StringSetter("writePlanElementsCSV")
	public void setWritePlanElementsCSV(Boolean value) {
		this.writePlanElementsCSV = value;
	}

	@StringGetter("personAttributes")
	public String getPersonAttributes() {
		return personAttributes;
	}

	@StringSetter("personAttributes")
	public void setPersonAttributes(String personAttributes) {
		this.personAttributes = personAttributes;
	}


	@StringGetter("linkVolumes")
	public Boolean getLinkVolumes() {
		return linkVolumes;
	}

	@StringSetter("linkVolumes")
	public void setLinkVolumes(Boolean linkVolumes) {
		this.linkVolumes = linkVolumes;
	}

	@StringGetter("dailylinkVolumes")
	public Boolean getDailyLinkVolumes() {
		return dailylinkVolumes;
	}

	@StringSetter("dailylinkVolumes")
	public void setDailyLinkVolumes(Boolean linkVolumes) {
		this.dailylinkVolumes = linkVolumes;
	}


	@StringGetter("zoneAttribute")
	public String getZoneAttribute() {
		return zoneAttribute;
	}

	@StringSetter("zoneAttribute")
	void setZoneAttribute(String txt) {
		this.zoneAttribute = txt;
	}

	@StringGetter("zonesId")
	public String getZonesId_asString() {
		return this.zonesId == null ? null : this.zonesId.toString();
	}

	public Id<Zones> getZonesId() {
		return this.zonesId;
	}

	@StringSetter("zonesId")
	public void setZonesId(String zonesId) {
		this.zonesId = Id.create(zonesId, Zones.class);
	}

	void setZonesId(Id<Zones> zonesId) {
		this.zonesId = zonesId;
	}


	@Override
	public Map<String, String> getComments() {
        return super.getComments();
    }

	public void setAllPostProcessingOff() {
		this.linkVolumes = false;
		this.writeAgentsCSV = false;
		this.writePlanElementsCSV = false;
		this.writeVisumPuTSurvey = false;
		this.writeOutputsInterval = 0;
	}

}
