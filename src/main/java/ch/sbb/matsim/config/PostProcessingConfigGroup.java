/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;


import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Map;

public class PostProcessingConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "PostProcessing";

    static private final String PARAM_ANALYSE_SCREENLINE = "analyseScreenline";
    static private final String PARAM_SHAPEFILE_SCREENLINE = "shapefileScreenline";
    static private final String PARAM_THRESHOLD_VISUM_NETWORK = "visumNetworkThreshold";
    static private final String PARAM_MODE_VISUM_NETWORK = "visumNetworkMode";


    private String shapeFile = "./output_merger";
    private String zoneAttribute = "GMDNR";
    private Boolean mapActivitiesToZone = false;
    private Boolean travelDiaries = true;
    private Boolean ptVolumes = false;
    private Boolean linkVolumes = false;
    private Boolean eventsPerPerson = false;
    private String personAttributes = "season_ticket,subpopulation,carAvail,hasLicense";
    private int writeOutputsInterval = 10;
    private Boolean writeAgentsCSV = false;
    private Boolean writePlanElementsCSV = false;
    private String linkCountDataFile = null;
    private String stopCountDataFile = null;
    private Boolean writeVisumPuTSurvey = false;

    private Boolean analyseScreenline = false;
    private String shapefileScreenline = null;

    private Boolean visumNetFile = false;
    private Integer visumNetworkThreshold = 5000;
    private String visumNetworkMode = TransportMode.car;


    @StringGetter(PARAM_SHAPEFILE_SCREENLINE)
    public String getShapefileScreenline() {
        return shapefileScreenline;
    }

    @StringSetter(PARAM_SHAPEFILE_SCREENLINE)
    public void setShapefileScreenline(String shapefileScreenline) {
        this.shapefileScreenline = shapefileScreenline;
    }

    @StringGetter(PARAM_THRESHOLD_VISUM_NETWORK)
    public Integer getVisumNetworkThreshold() {
        return visumNetworkThreshold;
    }

    @StringSetter(PARAM_THRESHOLD_VISUM_NETWORK)
    public void setVisumNetworkThreshold(Integer visumNetworkThreshold) {
        this.visumNetworkThreshold = visumNetworkThreshold;
    }

    @StringGetter(PARAM_MODE_VISUM_NETWORK)
    public String getVisumNetworkMode() {
        return visumNetworkMode;
    }

    @StringSetter(PARAM_MODE_VISUM_NETWORK)
    public void setVisumNetworkMode(String visumNetworkMode) {
        this.visumNetworkMode = visumNetworkMode;
    }


    @StringGetter(PARAM_ANALYSE_SCREENLINE)
    public Boolean getAnalyseScreenline() {
        return analyseScreenline;
    }

    @StringSetter(PARAM_ANALYSE_SCREENLINE)
    public void setAnalyseScreenline(Boolean write) {
        this.analyseScreenline = write;
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

    public PostProcessingConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter("eventsPerPerson")
    public Boolean getEventsPerPerson() {
        return eventsPerPerson;
    }

    @StringSetter("eventsPerPerson")
    public void setEventsPerPerson(Boolean eventsPerPerson) {
        this.eventsPerPerson = eventsPerPerson;
    }

    @StringGetter("travelDiaries")
    public Boolean getTravelDiaries() {
        return travelDiaries;
    }

    @StringSetter("travelDiaries")
    public void setTravelDiaries(Boolean travelDiaries) {
        this.travelDiaries = travelDiaries;
    }

    @StringGetter("ptVolumes")
    public Boolean getPtVolumes() {
        return ptVolumes;
    }

    @StringSetter("ptVolumes")
    public void setPtVolumes(Boolean ptVolumes) {
        this.ptVolumes = ptVolumes;
    }

    @StringGetter("linkVolumes")
    public Boolean getLinkVolumes() {
        return linkVolumes;
    }

    @StringSetter("linkVolumes")
    public void setLinkVolumes(Boolean linkVolumes) {
        this.linkVolumes = linkVolumes;
    }

    @StringGetter("visumNetFile")
    public Boolean getVisumNetFile() {
        return visumNetFile;
    }

    @StringSetter("visumNetFile")
    public void setVisumNetFile(Boolean visumNetFile) {
        this.visumNetFile = visumNetFile;
    }

    @StringGetter("linkCountDataFile")
    public String getLinkCountDataFile() {
        return linkCountDataFile;
    }

    @StringSetter("linkCountDataFile")
    public void setLinkCountDataFile(String linkCountDataFile) {
        this.linkCountDataFile = linkCountDataFile;
    }


    @StringGetter("stopCountDataFile")
    public String getStopCountDataFile() {
        return stopCountDataFile;
    }

    @StringSetter("stopCountDataFile")
    public void setStopCountDataFile(String stopCountDataFile) {
        this.stopCountDataFile = stopCountDataFile;
    }

    @StringGetter("zoneAttribute")
    public String getZoneAttribute() {
        return zoneAttribute;
    }

    @StringSetter("zoneAttribute")
    void setZoneAttribute(String txt) {
        this.zoneAttribute = txt;
    }

    @StringGetter("shapeFile")
    public String getShapeFile() {
        return shapeFile;
    }

    @StringSetter("shapeFile")
    void setShapeFile(String txt) {
        this.shapeFile = txt;
    }


    @StringGetter("mapActivitiesToZone")
    public Boolean getMapActivitiesToZone() {
        return mapActivitiesToZone;
    }

    @StringSetter("mapActivitiesToZone")
    public void setMapActivitiesToZone(Boolean mapActivitiesToZone) {
        this.mapActivitiesToZone = mapActivitiesToZone;
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_ANALYSE_SCREENLINE, "Run Screenline Analysis");
        comments.put(PARAM_SHAPEFILE_SCREENLINE, "Shapefile for screenline. Contains polylines");
        comments.put(PARAM_MODE_VISUM_NETWORK, "Mode to consider to export Network with volume to Visum (*.net File");
        comments.put(PARAM_THRESHOLD_VISUM_NETWORK, "Links with volume > THRESHOLD are exported to Visum (*.net File). Links with less traffic are ignored");
        return comments;
    }

}
