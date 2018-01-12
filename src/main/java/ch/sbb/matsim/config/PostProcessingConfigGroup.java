/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;


import org.matsim.core.config.ReflectiveConfigGroup;

public class PostProcessingConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "PostProcessing";

    private String shapeFile = "./output_merger";
    private String zoneAttribute = "GMDNR";
    private Boolean mapActivitiesToZone = false;
    private Boolean travelDiaries = false;
    private Boolean ptVolumes = false;
    private Boolean linkVolumes = false;
    private Boolean eventsPerPerson = false;
    private String personAttributes = "season_ticket,subpopulation,carAvail,hasLicense";
    private Boolean writePlansCSV = false;
    private Boolean visumNetFile = false;
    private String linkCountDataFile = null;
    private String stopCountDataFile = null;
    private Boolean writeVisumPuTSurvey = false;

    @StringGetter("writeVisumPuTSurvey")
    public Boolean getWriteVisumPuTSurvey() {
        return writeVisumPuTSurvey;
    }

    @StringSetter("writeVisumPuTSurvey")
    public void setWriteVisumPuTSurvey(Boolean write) {
        this.writeVisumPuTSurvey = write;
    }

    @StringGetter("writePlansCSV")
    public Boolean getWritePlansCSV() {
        return writePlansCSV;
    }

    @StringSetter("writePlansCSV")
    public void setWritePlansCSV(Boolean writePlansCSV) {
        this.writePlansCSV = writePlansCSV;
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

}
