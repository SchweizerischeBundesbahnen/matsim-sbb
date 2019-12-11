/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.*;

public class PopulationMergerConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "populationMerger";

    private static final String PARAM_BASE_PLANS = "baseInputPlansFile";
    private static final String PARAM_BASE_ATTRIBUTES = "baseInputPersonAttributesFile";
    private static final String PARAM_OUTPUT = "outputFolder";

    private String inputPlansFiles;
    private String outputFolder;

    private final Map<String, PopulationTypeParameterSet> subpopulations = new HashMap<>();

    public PopulationMergerConfigGroup() {
        super(GROUP_NAME);
    }


    @Override
    public ConfigGroup createParameterSet(String type) {
        if (PopulationTypeParameterSet.TYPE.equals(type)) {
            return new PopulationTypeParameterSet();
        } else {
            throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
        }
    }

    public Set<String> getPopulationTypes() {
        return this.subpopulations.keySet();
    }

    private void addSubpopulation(PopulationTypeParameterSet set) {
        this.subpopulations.put(set.getSubpopulation(), set);
    }

    @Override
    public void addParameterSet(ConfigGroup set) {
        if (set instanceof PopulationTypeParameterSet) {
            this.addSubpopulation((PopulationTypeParameterSet) set);

        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
        }
        super.addParameterSet(set);
    }

    public PopulationTypeParameterSet getSubpopulations(String subpopulation) {
        return this.subpopulations.get(subpopulation);
    }


    @StringGetter(PARAM_BASE_PLANS)
    public String getInputPlansFiles() {
        return inputPlansFiles;
    }

    @StringSetter(PARAM_BASE_PLANS)
    public void setInputPlansFiles(String inputPlansFiles) {
        this.inputPlansFiles = inputPlansFiles;
    }

    @StringGetter(PARAM_OUTPUT)
    public String getOutputFolder() {
        return outputFolder;
    }

    @StringSetter(PARAM_OUTPUT)
    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }


    public static class PopulationTypeParameterSet extends ReflectiveConfigGroup {

        private static final String TYPE = "populationType";

        private static final String PARAM_PLANSFILE = "plansFile";
        private static final String PARAM_SUBPOPULATION = "subpopulation";

        private String plansFile;
        private String subpopulation;

        public PopulationTypeParameterSet() {
            super(TYPE);
        }


        @StringGetter(PARAM_PLANSFILE)
        public String getPlansFile() {
            return plansFile;
        }

        @StringSetter(PARAM_PLANSFILE)
        public void setPlansFile(String plansFile) {
            this.plansFile = plansFile;
        }

        @StringGetter(PARAM_SUBPOPULATION)
        public String getSubpopulation() {
            return this.subpopulation;
        }

        @StringSetter(PARAM_SUBPOPULATION)
        public void setSubpopulation(String subpopulation) {
            this.subpopulation = subpopulation;
        }

    }

}
