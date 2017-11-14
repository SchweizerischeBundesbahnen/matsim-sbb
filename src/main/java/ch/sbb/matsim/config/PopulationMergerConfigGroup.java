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
}
