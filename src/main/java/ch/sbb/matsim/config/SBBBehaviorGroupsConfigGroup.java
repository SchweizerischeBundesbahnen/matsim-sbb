/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author pmanser / SBB
 *
 */

public class SBBBehaviorGroupsConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBBehaviorGroups";

    static private final String PARAMSET_BEHAVIORGROUP = "behaviorGroup";
    static private final String PARAMSET_PERSONGROUPATTRIBUTE = "personGroupAttributeValues";
    static private final String PARAMSET_ABSOLUTEMODECORRECTIONS = "absoluteModeCorrections";

    static private final String PARAM_NAME = "name";
    static private final String PARAM_PERSONATTRIBUTE = "personAttribute";
    static private final String PARAM_ATTRIBUTE = "attributeValues";
    static private final String PARAM_MODE = "mode";
    static private final String PARAM_DELTACONSTANT = "deltaConstant";
    static private final String PARAM_DELTAUTILDISTANCE = "deltaMarginalUtilityOfDistance_util_m";
    static private final String PARAM_DELTAUTILTIME = "deltaMarginalUtilityOfTraveling_util_hr";
    static private final String PARAM_DELTADISTANCERATE = "deltaMonetaryDistanceRate";

    public SBBBehaviorGroupsConfigGroup() {
        super(GROUP_NAME);
    }

    @Override
    public ConfigGroup createParameterSet( final String type ) {
        switch ( type ) {
            case BehaviorGroupParams.SET_TYPE:
                return new BehaviorGroupParams();
            default:
                throw new IllegalArgumentException( type );
        }
    }

    @Override
    protected void checkParameterSet( final ConfigGroup module ) {
        switch ( module.getName() ) {
            case BehaviorGroupParams.SET_TYPE:
                if ( !(module instanceof BehaviorGroupParams) )
                    throw new RuntimeException( "unexpected class for module " + module );
                break;
            default:
                throw new IllegalArgumentException( module.getName() );
        }
    }

    @Override
    public void addParameterSet(final ConfigGroup set) {
        switch ( set.getName() ) {
            case BehaviorGroupParams.SET_TYPE:
                addBehaviorGroupParams( (BehaviorGroupParams) set );
                break;
            default:
                throw new IllegalArgumentException( set.getName() );
        }
    }

    public Map<String, BehaviorGroupParams> getBehaviorGroupParams() {
        final Map<String, BehaviorGroupParams> map = new LinkedHashMap< >();
        for ( ConfigGroup pars : getParameterSets( BehaviorGroupParams.SET_TYPE ) ) {
            final String name = ((BehaviorGroupParams) pars).getBehaviorGroupName();
            final BehaviorGroupParams old = map.put( name , (BehaviorGroupParams)	pars );
            if ( old != null ) throw new IllegalStateException( "several parameter sets for behavior group " + name );
        }
        return map;
    }

    public void addBehaviorGroupParams(final BehaviorGroupParams params) {
        final BehaviorGroupParams previous = this.getBehaviorGroupParams().get(params.getBehaviorGroupName());
        if ( previous != null ) {
            final boolean removed = removeParameterSet( previous );
            if ( !removed ) throw new RuntimeException( "problem replacing behavior group params " );
        }
        super.addParameterSet( params );
    }

    public static class BehaviorGroupParams extends ReflectiveConfigGroup {
        public static final String SET_TYPE = PARAMSET_BEHAVIORGROUP;

        private String name = null;
        private String personAttribute = null;

        public BehaviorGroupParams() {
            super(PARAMSET_BEHAVIORGROUP);
        }

        @Override
        public void checkConsistency(Config config) {
            if (this.name == null) {
                throw new RuntimeException("behaviour group name for parameter set " + this + " cannot be  null!");
            }
        }

        @StringGetter(PARAM_NAME)
        public String getBehaviorGroupName() {
            return this.name;
        }

        @StringSetter(PARAM_NAME)
        public void setBehaviorGroupName(String name) {
            this.name = name;
        }

        @StringGetter(PARAM_PERSONATTRIBUTE)
        public String getPersonAttribute() {
            return this.personAttribute;
        }

        @StringSetter(PARAM_PERSONATTRIBUTE)
        public void setPersonAttribute(String personAttribute) {
            this.personAttribute = personAttribute;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_NAME, "Name of the behavior group as identifier.");
            comments.put(PARAM_PERSONATTRIBUTE, "Custom person attribute name. MUST be in line with the person attributes in the population files");
            return comments;
        }

        @Override
        public ConfigGroup createParameterSet(final String type) {
            switch ( type ) {
                case PersonGroupAttributeValues.SET_TYPE:
                    return new PersonGroupAttributeValues();
                default:
                    throw new IllegalArgumentException( type );
            }
        }

        @Override
        protected void checkParameterSet( final ConfigGroup module ) {
            switch ( module.getName() ) {
                case PersonGroupAttributeValues.SET_TYPE:
                    if ( !(module instanceof PersonGroupAttributeValues) )
                        throw new RuntimeException( "wrong class for " + module );
                    final Set<String> t = ((PersonGroupAttributeValues) module).getPersonGroupAttributeValues();
                    for(String value: t) {
                        if (getPersonGroupByAttribute(value) != null)
                            throw new IllegalStateException("already a parameter set for attribute value " + t);
                    }
                    break;
                default:
                    throw new IllegalArgumentException( module.getName() );
            }
        }

        public Collection<String> getPersonGroupAttributes() {
            return this.getPersonGroupByAttribute().keySet();
        }

        public Map<String, PersonGroupAttributeValues> getPersonGroupByAttribute() {
            final Map<String, PersonGroupAttributeValues> map = new LinkedHashMap<>();
            for ( ConfigGroup pars : getParameterSets( PersonGroupAttributeValues.SET_TYPE ) ) {
                final Set<String> attributeValues = ((PersonGroupAttributeValues) pars).getPersonGroupAttributeValues();
                for(String value: attributeValues) {
                    final PersonGroupAttributeValues old = map.put(value, (PersonGroupAttributeValues) pars);
                    if (old != null) throw new IllegalStateException("several parameter sets for attribute value " + value);
                }
            }
            return map;
        }

        public PersonGroupAttributeValues getPersonGroupByAttribute(final String value) {
            return this.getPersonGroupByAttribute().get(value);
        }

        public void addPersonGroupByAttribute(final PersonGroupAttributeValues values) {
            Set<String> attributes = values.getPersonGroupAttributeValues();
            for(String attribute: attributes) {
                final PersonGroupAttributeValues previous = this.getPersonGroupByAttribute().get(attribute);
                if (previous != null) {
                    final boolean removed = removeParameterSet(previous);
                    if (!removed) throw new RuntimeException("problem replacing person group type params");
                }
            }
            super.addParameterSet( values );
        }
    }

    public static class PersonGroupAttributeValues extends ReflectiveConfigGroup {
        public static final String SET_TYPE = PARAMSET_PERSONGROUPATTRIBUTE;

        private Set<String> attributeValues = new HashSet<>();

        public PersonGroupAttributeValues() {
            super(PARAMSET_PERSONGROUPATTRIBUTE);
        }

        @Override
        public void checkConsistency(Config config) {
            if (this.attributeValues == null)
                throw new RuntimeException("behaviour group attribute values for parameter set " + this + " is null!");
        }

        @StringGetter(PARAM_ATTRIBUTE)
        private String getPersonGroupAttributeValuesAsString() {
            return CollectionUtils.setToString(this.attributeValues);
        }

        private Set<String> getPersonGroupAttributeValues() {
            return this.attributeValues;
        }

        @StringSetter(PARAM_ATTRIBUTE)
        public void setPersonGroupAttributeValues(String values) {
            setPersonGroupAttributeValues(CollectionUtils.stringToSet(values));
        }

        private void setPersonGroupAttributeValues(Set<String> values) {
            this.attributeValues.clear();
            this.attributeValues.addAll(values);
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_ATTRIBUTE, "Attributes of the person group. It is possible to give a comma-separated list of different attributes");
            return comments;
        }

        @Override
        public ConfigGroup createParameterSet(final String mode) {
            switch ( mode ) {
                case ModeCorrection.SET_TYPE:
                    return new ModeCorrection();
                default:
                    throw new IllegalArgumentException( mode );
            }
        }

        @Override
        protected void checkParameterSet( final ConfigGroup module ) {
            switch ( module.getName() ) {
                case ModeCorrection.SET_TYPE:
                    if ( !(module instanceof ModeCorrection) ) {
                        throw new RuntimeException( "wrong class for " + module );
                    }
                    final String t = ((ModeCorrection) module).getMode();
                    if ( getModeCorrectionsForMode( t  ) != null ) {
                        throw new IllegalStateException( "already a parameter set for mode " + t );
                    }
                    break;
                default:
                    throw new IllegalArgumentException( module.getName() );
            }
        }

        public Collection<String> getModes() {
            return this.getModeCorrectionParams().keySet();
        }

        public Map<String, ModeCorrection> getModeCorrectionParams() {
            final Map<String, ModeCorrection> map = new LinkedHashMap< >();
            for ( ConfigGroup pars : getParameterSets( ModeCorrection.SET_TYPE ) ) {
                final String mode = ((ModeCorrection) pars).getMode();
                final ModeCorrection old = map.put( mode , (ModeCorrection) pars );
                if ( old != null ) throw new IllegalStateException( "several parameter sets for mode correction " + mode );
            }
            return map;
        }

        public ModeCorrection getModeCorrectionsForMode(final String type) {
            return this.getModeCorrectionParams().get(type);
        }

        public void addModeCorrection(final ModeCorrection modeCorrection) {
            final ModeCorrection previous = this.getModeCorrectionParams().get(modeCorrection.getMode());
            if ( previous != null ) {
                final boolean removed = removeParameterSet( previous );
                if ( !removed ) throw new RuntimeException( "problem replacing mode correction params " );
            }
            super.addParameterSet( modeCorrection );
        }
    }

    public static class ModeCorrection extends ReflectiveConfigGroup {
        public static final String SET_TYPE = PARAMSET_ABSOLUTEMODECORRECTIONS;

        private String mode = null;
        private double constant = 0.0;
        private double margUtilOfTime = 0.0;
        private double margUtilOfDistance = 0.0;
        private double distanceRate = 0.0;

        public ModeCorrection() {
            super(PARAMSET_ABSOLUTEMODECORRECTIONS);
        }

        @Override
        public void checkConsistency(Config config) {
            if (this.mode == null)
                throw new RuntimeException("no mode defined for parameterset " + this);
        }

        @StringGetter(PARAM_MODE)
        public String getMode() {
            return this.mode;
        }

        @StringSetter(PARAM_MODE)
        public void setMode(String mode) {
            this.mode = mode;
        }

        @StringGetter(PARAM_DELTACONSTANT)
        public double getConstant() {
            return this.constant;
        }

        @StringSetter(PARAM_DELTACONSTANT)
        public void setConstant(double util) {
            this.constant = util;
        }

        @StringGetter(PARAM_DELTAUTILTIME)
        public double getMargUtilOfTime() {
            return this.margUtilOfTime;
        }

        @StringSetter(PARAM_DELTAUTILTIME)
        public void setMargUtilOfTime(double util) {
            this.margUtilOfTime = util;
        }

        @StringGetter(PARAM_DELTAUTILDISTANCE)
        public double getMargUtilOfDistance() {
            return this.margUtilOfDistance;
        }

        @StringSetter(PARAM_DELTAUTILDISTANCE)
        public void setMargUtilOfDistance(double util) {
            this.margUtilOfDistance = util;
        }

        @StringGetter(PARAM_DELTADISTANCERATE)
        public double getDistanceRate() {
            return this.distanceRate;
        }

        @StringSetter(PARAM_DELTADISTANCERATE)
        public void setDistanceRate(double distanceRate) {
            this.distanceRate = distanceRate;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_MODE, "The parameter corrections will be done for this specific mode.");
            return comments;
        }

        public boolean isSet() {
            return ((this.getConstant() != 0) || (this.getMargUtilOfTime() != 0)  || (this.getMargUtilOfDistance() != 0) || (this.getDistanceRate() != 0)) ;
        }
    }
}
