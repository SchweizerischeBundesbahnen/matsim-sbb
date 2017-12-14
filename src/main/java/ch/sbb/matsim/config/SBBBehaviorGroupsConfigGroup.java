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
 * @author pmanser / SBB
 */

public class SBBBehaviorGroupsConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBBehaviorGroups";

    static private final String PARAMSET_BEHAVIORGROUP = "behaviorGroup";
    static private final String PARAMSET_PERSONGROUP = "personGroupType";
    static private final String PARAMSET_ABSOLUTEMODECORRECTIONS = "absoluteModeCorrections";

    static private final String PARAM_NAME = "name";
    static private final String PARAM_PERSONATTRIBUTE = "personAttribute";
    static private final String PARAM_TYPES = "types";
    static private final String PARAM_TYPE = "type";
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
            case PersonGroupTypes.SET_TYPE:
                return new PersonGroupTypes();
            default:
                throw new IllegalArgumentException( type );
        }
    }

    @Override
    protected void checkParameterSet( final ConfigGroup module ) {
        switch ( module.getName() ) {
            case BehaviorGroupParams.SET_TYPE:
                if ( !(module instanceof BehaviorGroupParams) ) {
                    throw new RuntimeException( "unexpected class for module " + module );
                }
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

    public void addBehaviorGroupParams(final BehaviorGroupParams params) {
        final BehaviorGroupParams previous = this.getBehaviorGroupParams().get(params.getBehaviorGroupName());

        if ( previous != null ) {

            final boolean removed = removeParameterSet( previous );
            if ( !removed ) throw new RuntimeException( "problem replacing behavior group params " );
        }

        super.addParameterSet( params );
    }

    public Map<String, BehaviorGroupParams> getBehaviorGroupParams() {
        final Map<String, BehaviorGroupParams> map = new LinkedHashMap< >();

        for ( ConfigGroup pars : getParameterSets( BehaviorGroupParams.SET_TYPE ) ) {
            if ( this.isLocked() ) {
                pars.setLocked();
            }
            final String mode = ((BehaviorGroupParams) pars).getBehaviorGroupName();
            final BehaviorGroupParams old = map.put( mode , (BehaviorGroupParams)	pars );
            if ( old != null ) throw new IllegalStateException( "several parameter sets for behavior group " + mode );
        }
        return map;
    }


    public static class BehaviorGroupParams extends ReflectiveConfigGroup {
        public static final String SET_TYPE = PARAMSET_BEHAVIORGROUP;

        private String name = null;
        private String personAttribute = null;
        private Set<String> types = new HashSet<>();

        public BehaviorGroupParams() {
            super(PARAMSET_BEHAVIORGROUP);
        }

        @Override
        public void checkConsistency(Config config) {
            if (this.name == null) {
                throw new RuntimeException("behaviour group name for parameter set " + this + " is null!");
            } else if (this.personAttribute == null && this.types == null) {
                throw new RuntimeException("no person attribute nor behaviour group types are set for group " + this.name);
            }
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_NAME, "Name of the behavior group as identifier.");
            comments.put(PARAM_PERSONATTRIBUTE, "Custom person attribute name. Must be in line with the person attributes in the population files");
            comments.put(PARAM_TYPES, "Possible behavior group types.");
            return comments;
        }

        @StringGetter(PARAM_NAME)
        public String getBehaviorGroupName() {
            return this.name;
        }

        @StringSetter(PARAM_NAME)
        public void setBehaviorGroupName(String name) {
            testForLocked() ;
            this.name = name;
        }

        @StringGetter(PARAM_PERSONATTRIBUTE)
        public String getPersonAttribute() {
            return this.personAttribute;
        }

        @StringSetter(PARAM_PERSONATTRIBUTE)
        public void setPersonAttribute(String personAttribute) {
            testForLocked() ;
            this.personAttribute = personAttribute;
        }

        @StringGetter(PARAM_TYPES)
        private String getBehaviorTypesAsString() {
            return CollectionUtils.setToString(this.types);
        }

        public Set<String> getBehaviorTypes() {
            return this.types;
        }

        @StringSetter(PARAM_TYPES)
        private void setBehaviorTypes(String types) {
            testForLocked() ;
            setBehaviorTypes(CollectionUtils.stringToSet(types));
        }

        public void setBehaviorTypes(Set<String> types) {
            this.types.clear();
            this.types.addAll(types);
        }

        @Override
        public ConfigGroup createParameterSet(final String type) {
            switch ( type ) {
                case PersonGroupTypes.SET_TYPE:
                    return new PersonGroupTypes();
                default:
                    throw new IllegalArgumentException( type );
            }
        }

        @Override
        protected void checkParameterSet( final ConfigGroup module ) {
            switch ( module.getName() ) {
                case PersonGroupTypes.SET_TYPE:
                    if ( !(module instanceof PersonGroupTypes) ) {
                        throw new RuntimeException( "wrong class for "+module );
                    }
                    final String t = ((PersonGroupTypes) module).getPersonGroupType();
                    if ( getPersonGroupTypeParams( t  ) != null ) {
                        throw new IllegalStateException( "already a parameter set for person group type "+t );
                    }
                    break;
                default:
                    throw new IllegalArgumentException( module.getName() );
            }
        }

        public Collection<String> getPersonGroupTypes() {
            return this.getPersonGroupTypeParamsPerType().keySet();
        }

        public Collection<PersonGroupTypes> getPersonGroupTypeParams() {
            @SuppressWarnings("unchecked")
            Collection<PersonGroupTypes> collection = (Collection<PersonGroupTypes>) getParameterSets( PersonGroupTypes.SET_TYPE );
            for ( PersonGroupTypes params : collection ) {
                if ( this.isLocked() ) {
                    params.setLocked();
                }
            }
            return collection ;
        }

        public PersonGroupTypes getPersonGroupTypeParams(final String type) {
            return this.getPersonGroupTypeParamsPerType().get(type);
        }

        public Map<String, PersonGroupTypes> getPersonGroupTypeParamsPerType() {
            final Map<String, PersonGroupTypes> map = new LinkedHashMap< >();

            for ( PersonGroupTypes pars : getPersonGroupTypeParams() ) {
                map.put( pars.getPersonGroupType() , pars );
            }
            return map;
        }
    }

    public static class PersonGroupTypes extends ReflectiveConfigGroup {
        public static final String SET_TYPE = PARAMSET_PERSONGROUP;

        private String type = null;

        public PersonGroupTypes() {
            super(PARAMSET_PERSONGROUP);
        }

        @Override
        public void checkConsistency(Config config) {
            if (this.type == null)
                throw new RuntimeException("behaviour group name for parameter set " + this + " is null!");
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_TYPE, "Type of the person group.");
            return comments;
        }

        @StringGetter(PARAM_TYPE)
        public String getPersonGroupType() {
            return this.type;
        }

        @StringSetter(PARAM_TYPE)
        public void setPersonGroupType(String type) {
            testForLocked();
            this.type = type;
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
                        throw new RuntimeException( "wrong class for "+module );
                    }
                    final String t = ((ModeCorrection) module).getMode();
                    if ( getModeCorrectionParams( t  ) != null ) {
                        throw new IllegalStateException( "already a parameter set for mode "+t );
                    }
                    break;
                default:
                    throw new IllegalArgumentException( module.getName() );
            }
        }

        public Collection<String> getModes() {
            return this.getPersonGroupTypeParamsPerMode().keySet();
        }

        public Collection<ModeCorrection> getModeCorrectionParams() {
            @SuppressWarnings("unchecked")
            Collection<ModeCorrection> collection = (Collection<ModeCorrection>) getParameterSets( ModeCorrection.SET_TYPE );
            for ( ModeCorrection params : collection ) {
                if ( this.isLocked() ) {
                    params.setLocked();
                }
            }
            return collection ;
        }

        public ModeCorrection getModeCorrectionParams(final String type) {
            return this.getPersonGroupTypeParamsPerMode().get(type);
        }

        public Map<String, ModeCorrection> getPersonGroupTypeParamsPerMode() {
            final Map<String, ModeCorrection> map = new LinkedHashMap< >();

            for ( ModeCorrection pars : getModeCorrectionParams() ) {
                map.put( pars.getMode() , pars );
            }
            return map;
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
            if (this.mode== null)
                throw new RuntimeException("behaviour group name for parameter set " + this + " is null!");
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_MODE, "Type of the person group.");
            return comments;
        }

        @StringGetter(PARAM_MODE)
        public String getMode() {
            return this.mode;
        }

        @StringSetter(PARAM_MODE)
        public void setMode(String mode) {
            testForLocked();
            this.mode = mode;
        }

        @StringGetter(PARAM_DELTACONSTANT)
        public double getConstant() {
            return this.constant;
        }

        @StringSetter(PARAM_DELTACONSTANT)
        public void setConstant(double util) {
            testForLocked();
            this.constant = util;
        }

        @StringGetter(PARAM_DELTAUTILTIME)
        public double getMargUtilOfTime() {
            return this.margUtilOfTime;
        }

        @StringSetter(PARAM_DELTAUTILTIME)
        public void setMargUtilOfTime(double util) {
            testForLocked();
            this.margUtilOfTime = util;
        }

        @StringGetter(PARAM_DELTAUTILDISTANCE)
        public double getMargUtilOfDistance() {
            return this.margUtilOfDistance;
        }

        @StringSetter(PARAM_DELTAUTILDISTANCE)
        public void setMargUtilOfDistance(double util) {
            testForLocked();
            this.margUtilOfDistance = util;
        }

        @StringGetter(PARAM_DELTADISTANCERATE)
        public double getDistanceRate() {
            return this.distanceRate;
        }

        @StringSetter(PARAM_DELTADISTANCERATE)
        public void setDistanceRate(double distanceRate) {
            testForLocked();
            this.distanceRate = distanceRate;
        }
    }
}
