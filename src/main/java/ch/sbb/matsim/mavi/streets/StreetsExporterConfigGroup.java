/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.mavi.streets;

import org.matsim.core.config.*;

import java.net.URL;

public class StreetsExporterConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "VisumStreets2MATSim";
    public final static String DESC_mainRoadSpeedFactor = "mainRoadSpeedFactor";
    public final static String DESC_smallRoadSpeedFactor = "smallRoadSpeedFactor";
    public final static String DESC_zonesFile = "zonesFile";
    public final static String DESC_visumFile = "visumFile";
    public final static String DESC_visumVersion = "visumVersion";
    public final static String DESC_outputDir = "outputDir";
    public final static String DESC_mergeRuralLinks = "mergeRuralLinks";
    public final static String DESC_reduceForeignLinks = "reduceForeignLinks";
    public final static String DESC_exportCounts = "exportCounts";
    double mainRoadSpeedFactor = 1.0;
    double smallRoadSpeedFactor = 1.0;
    String zonesFile = null;
    String visumFile = null;
    String visumVersion = "21";
    String outputDir = null;
    boolean mergeRuralLinks = true;
    boolean reduceForeignLinks = true;
    boolean exportCounts = true;

    public StreetsExporterConfigGroup() {
        super(GROUP_NAME);
    }

    public static void main(String[] args) {
        Config config = ConfigUtils.createConfig(new StreetsExporterConfigGroup());
        new ConfigWriter(config).write("config.xml");
    }

    @StringGetter(DESC_mainRoadSpeedFactor)
    public double getMainRoadSpeedFactor() {
        return mainRoadSpeedFactor;
    }

    @StringSetter(DESC_mainRoadSpeedFactor)
    public void setMainRoadSpeedFactor(double mainRoadSpeedFactor) {
        this.mainRoadSpeedFactor = mainRoadSpeedFactor;
    }

    @StringGetter(DESC_smallRoadSpeedFactor)
    public double getSmallRoadSpeedFactor() {
        return smallRoadSpeedFactor;
    }

    @StringSetter(DESC_smallRoadSpeedFactor)
    public void setSmallRoadSpeedFactor(double smallRoadSpeedFactor) {
        this.smallRoadSpeedFactor = smallRoadSpeedFactor;
    }

    @StringGetter(DESC_zonesFile)
    public String getZonesFile() {
        return zonesFile;
    }

    @StringSetter(DESC_zonesFile)
    public void setZonesFile(String zonesFile) {
        this.zonesFile = zonesFile;
    }

    @StringGetter(DESC_visumFile)
    public String getVisumFile() {
        return visumFile;
    }

    @StringSetter(DESC_visumFile)
    public void setVisumFile(String visumFile) {
        this.visumFile = visumFile;
    }

    @StringGetter(DESC_visumVersion)
    public String getVisumVersion() {
        return visumVersion;
    }

    public URL getVisumVersionURL(URL context) {
        return ConfigGroup.getInputFileURL(context, visumVersion);
    }


    @StringSetter(DESC_visumVersion)
    public void setVisumVersion(String visumVersion) {
        this.visumVersion = visumVersion;
    }

    @StringGetter(DESC_outputDir)
    public String getOutputDir() {
        return outputDir;
    }


    public URL getOutputDirURL(URL context) {
        return ConfigGroup.getInputFileURL(context, outputDir);
    }

    @StringSetter(DESC_outputDir)
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    @StringGetter(DESC_mergeRuralLinks)
    public boolean isMergeRuralLinks() {
        return mergeRuralLinks;
    }

    @StringSetter(DESC_mergeRuralLinks)
    public void setMergeRuralLinks(boolean mergeRuralLinks) {
        this.mergeRuralLinks = mergeRuralLinks;
    }

    @StringGetter(DESC_reduceForeignLinks)
    public boolean isReduceForeignLinks() {
        return reduceForeignLinks;
    }

    @StringSetter(DESC_reduceForeignLinks)
    public void setReduceForeignLinks(boolean reduceForeignLinks) {
        this.reduceForeignLinks = reduceForeignLinks;
    }

    @StringGetter(DESC_exportCounts)
    public boolean isExportCounts() {
        return exportCounts;
    }

    @StringSetter(DESC_exportCounts)
    public void setExportCounts(boolean exportCounts) {
        this.exportCounts = exportCounts;
    }
}
