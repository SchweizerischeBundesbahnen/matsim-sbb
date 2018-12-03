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
    private String inputAttributesFiles;
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

    @Override
    public void addParameterSet(ConfigGroup set) {
        if (set instanceof PopulationTypeParameterSet) {
            PopulationTypeParameterSet set2 = (PopulationTypeParameterSet) set;
            this.subpopulations.put(set2.getSubpopulation(), set2);


        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
        }
    }

    public PopulationTypeParameterSet getSubpopulations(String subpopulation) {
        return subpopulations.get(subpopulation);
    }

    @StringGetter(PARAM_BASE_PLANS)
    public String getInputPlansFiles() {
        return inputPlansFiles;
    }

    @StringSetter(PARAM_BASE_PLANS)
    public void setInputPlansFiles(String inputPlansFiles) {
        this.inputPlansFiles = inputPlansFiles;
    }

    @StringGetter(PARAM_BASE_ATTRIBUTES)
    public String getInputAttributesFiles() {
        return inputAttributesFiles;
    }

    @StringSetter(PARAM_BASE_ATTRIBUTES)
    public void setInputAttributesFiles(String inputAttributesFiles) {
        this.inputAttributesFiles = inputAttributesFiles;
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

        private static String plansFile;


        private static String subpopulation;

        public PopulationTypeParameterSet() {
            super(TYPE);
        }


        @StringGetter(PARAM_PLANSFILE)
        public static String getPlansFile() {
            return plansFile;
        }

        @StringSetter(PARAM_PLANSFILE)
        public static void setPlansFile(String plansFile) {
            PopulationTypeParameterSet.plansFile = plansFile;
        }

        @StringGetter(PARAM_SUBPOPULATION)
        public static String getSubpopulation() {
            return subpopulation;
        }

        @StringSetter(PARAM_SUBPOPULATION)
        public static void setSubpopulation(String subpopulation) {
            PopulationTypeParameterSet.subpopulation = subpopulation;
        }

    }

}
