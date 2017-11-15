/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

public class PopulationMergerConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "populationMerger";

    private List<String> inputPlansFiles;
    private String outputPlansFile = "";
    private String outputPersonAttributesFile = "";
    private String mergedPersonAttributeKey = "";
    private String mergedPersonAttributeValue = "";

    public PopulationMergerConfigGroup() {
        super(GROUP_NAME);
        this.inputPlansFiles = new ArrayList<String>();
    }

    @StringGetter("inputPlansFiles")
    public String getInputPlansFiles() {
        try {
            return this.inputPlansFiles.get(0);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    @StringSetter("inputPlansFiles")
    public void addInputPlansFiles(String inputPlansFile) {
        this.inputPlansFiles.add(inputPlansFile);
    }

    public String shiftInputPlansFiles() {
        try {
            return this.inputPlansFiles.remove(0);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    @StringGetter("outputPlansFile")
    public String getOutputPlansFile() {
        return outputPlansFile;
    }

    @StringSetter("outputPlansFile")
    public void setOutputPlansFile(String outputPlansFile) {
        this.outputPlansFile = outputPlansFile;
    }

    @StringGetter("outputPersonAttributesFile")
    public String getOutputPersonAttributesFile() {
        return outputPersonAttributesFile;
    }

    @StringSetter("outputPersonAttributesFile")
    public void setOutputPersonAttributesFile(String outputPersonAttributesFile) {
        this.outputPersonAttributesFile = outputPersonAttributesFile;
    }

    @StringGetter("mergedPersonAttributeKey")
    public String getMergedPersonAttributeKey() {
        return mergedPersonAttributeKey;
    }

    @StringSetter("mergedPersonAttributeKey")
    public void setMergedPersonAttributeKey(String mergedPersonAttributeKey) {
        this.mergedPersonAttributeKey = mergedPersonAttributeKey;
    }

    @StringGetter("mergedPersonAttributeValue")
    public String getMergedPersonAttributeValue() {
        return mergedPersonAttributeValue;
    }

    @StringSetter("mergedPersonAttributeValue")
    public void setMergedPersonAttributeValue(String mergedPersonAttributeValue) {
        this.mergedPersonAttributeValue = mergedPersonAttributeValue;
    }
}
