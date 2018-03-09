/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mavi;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author pmanser / SBB
 */

public class ExportPTSupplyFromVisumConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "ExportPTSupplyFromVisum";

    static private final String PARAMSET_TIMEPROFILFILTER = "TimeProfileFilter";
    static private final String PARAMSET_STOPATTRIBUTES = "StopAttributes";
    static private final String PARAMSET_ROUTEATTRIBUTES = "RouteAttributes";

    static private final String PARAM_PATHTOVISUM = "PathToVisumVersion";
    static private final String PARAM_PATHTOATTRIBUTES = "PathToVisumAttributeFile";
    static private final String PARAM_OUTPUT_PATH = "OutputPath";
    static private final String PARAM_NETWORK_MODE = "NetworkMode";
    static private final String PARAM_VEHICLE_MODE = "VehicleMode";
    static private final String PARAM_TRANSFERTIMES = "ExportTransferTimes";
    static private final String PARAM_LINESTOROUTE = "LinesToRoute";

    private String pathToVisum = null;
    private String pathToVisumAttributeFile = null;
    private String outputPath = null;
    private String networkMode = null;
    private String vehicleMode = null;
    private boolean exportTransferTimes = false;
    private HashSet<String> linesToRoute = new HashSet<>();

    public ExportPTSupplyFromVisumConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter(PARAM_PATHTOVISUM)
    public String getPathToVisum() {
        return this.pathToVisum;
    }

    @StringSetter(PARAM_PATHTOVISUM)
    public void setPathToVisum(String value) {
        this.pathToVisum = value;
    }

    @StringGetter(PARAM_NETWORK_MODE)
    public String getNetworkMode() {
        return this.networkMode;
    }

    @StringSetter(PARAM_NETWORK_MODE)
    public void setNetworkMode(String value) {
        this.networkMode = value;
    }

    @StringGetter(PARAM_VEHICLE_MODE)
    public String getVehicleMode() {
        return this.vehicleMode;
    }

    @StringSetter(PARAM_VEHICLE_MODE)
    public void setVehicleMode(String value) {
        this.vehicleMode = value;
    }

    @StringGetter(PARAM_PATHTOATTRIBUTES)
    public String getPathToAttributeFile() {
        return this.pathToVisumAttributeFile;
    }

    @StringSetter(PARAM_PATHTOATTRIBUTES)
    public void setPathToAttributeFile(String value) {
        this.pathToVisumAttributeFile = value;
    }

    @StringGetter(PARAM_LINESTOROUTE)
    private String getLinesToRouteAsString() {
        return CollectionUtils.setToString(this.linesToRoute);
    }

    public Set<String> getLinesToRoute() {
        return this.linesToRoute;
    }

    @StringSetter(PARAM_LINESTOROUTE)
    private void setLinesToRoute(String lines) {
        setLinesToRoute(CollectionUtils.stringToSet(lines));
    }

    public void setLinesToRoute(Set<String> lines) {
        this.linesToRoute.clear();
        this.linesToRoute.addAll(lines);
    }

    @StringGetter(PARAM_OUTPUT_PATH)
    public String getOutputPath() {
        return this.outputPath;
    }

    @StringSetter(PARAM_OUTPUT_PATH)
    public void setOutputPath(String value) {
        this.outputPath = value;
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_PATHTOVISUM, "Path to the visum version.");
        comments.put(PARAM_PATHTOATTRIBUTES, "Provide an additional visum attribute file. IMPORTANT: the script can change EXISTING attributes only, but not load new ones. It is recommended to this manually.");
        comments.put(PARAM_LINESTOROUTE, "All the lines from the specific Datenherkunft will be routed (Visum link = MATSim link).");
        comments.put(PARAM_OUTPUT_PATH, "Set the path of the output directory.");
        return comments;
    }

    @Override
    public ConfigGroup createParameterSet(final String type ) {
        switch ( type ) {
            case TimeProfilFilterParams.SET_TYPE:
                return new TimeProfilFilterParams();
            case StopAttributeParams.SET_TYPE:
                return new StopAttributeParams();
            case RouteAttributeParams.SET_TYPE:
                return new RouteAttributeParams();
            default:
                throw new IllegalArgumentException( type );
        }
    }

    @Override
    protected void checkParameterSet( final ConfigGroup module ) {
        switch ( module.getName() ) {
            case TimeProfilFilterParams.SET_TYPE:
                if ( !(module instanceof TimeProfilFilterParams) ) {
                    throw new RuntimeException( "unexpected class for module " + module );
                }
                break;
            case StopAttributeParams.SET_TYPE:
                if ( !(module instanceof StopAttributeParams) ) {
                    throw new RuntimeException( "unexpected class for module " + module );
                }
                break;
            case RouteAttributeParams.SET_TYPE:
                if ( !(module instanceof RouteAttributeParams) ) {
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
            case TimeProfilFilterParams.SET_TYPE:
                addTimeProfilFilterParams( (TimeProfilFilterParams) set );
                break;
            case StopAttributeParams.SET_TYPE:
                addStopAttributeParams( (StopAttributeParams) set );
                break;
            case RouteAttributeParams.SET_TYPE:
                addRouteAttributeParams( (RouteAttributeParams) set );
                break;
            default:
                throw new IllegalArgumentException( set.getName() );
        }
    }

    public void addTimeProfilFilterParams(final TimeProfilFilterParams params) {
        final TimeProfilFilterParams previous = this.getTimeProfilFilterParams().get(params.getPosition());

        if ( previous != null ) {
            final boolean removed = removeParameterSet( previous );
            if ( !removed ) throw new RuntimeException( "problem replacing filter params." );
        }

        super.addParameterSet( params );
    }

    public Map<Integer, TimeProfilFilterParams> getTimeProfilFilterParams() {
        final Map<Integer, TimeProfilFilterParams> map = new LinkedHashMap< >();

        for ( ConfigGroup pars : getParameterSets( TimeProfilFilterParams.SET_TYPE ) ) {
            final Integer position = ((TimeProfilFilterParams) pars).getPosition();
            final TimeProfilFilterParams old = map.put( position , (TimeProfilFilterParams)	pars );
            if ( old != null ) throw new IllegalStateException( "several parameters set for filter position " + position );
        }
        return map;
    }

    public static class TimeProfilFilterParams extends ReflectiveConfigGroup {
        public static final String SET_TYPE = PARAMSET_TIMEPROFILFILTER;

        private static final String PARAM_POSITION = "Position";
        private static final String PARAM_OP = "Op";
        private static final String PARAM_COMPLEMENT = "Complement";
        private static final String PARAM_ATTRIBUTE = "Attribute";
        private static final String PARAM_COMPARATOR = "CompareOperator";
        private static final String PARAM_VAL = "Val";

        private int position;
        private String op;
        private boolean complement;
        private String attribute;
        private int comparator;
        private String val;

        public TimeProfilFilterParams() {
            super( PARAMSET_TIMEPROFILFILTER );
        }

        @StringGetter(PARAM_POSITION)
        public Integer getPosition() {
            return this.position;
        }

        @StringSetter(PARAM_POSITION)
        public void setPosition(Integer value) {
            this.position = value;
        }

        @StringGetter(PARAM_OP)
        public String getOp() {
            return this.op;
        }

        @StringSetter(PARAM_OP)
        public void setOp(String value) {
            this.op = value;
        }

        @StringGetter(PARAM_COMPLEMENT)
        public boolean isComplement() {
            return this.complement;
        }

        @StringSetter(PARAM_COMPLEMENT)
        public void setIsComplement(Boolean value) {
            this.complement = value;
        }

        @StringGetter(PARAM_ATTRIBUTE)
        public String getAttribute() {
            return this.attribute;
        }

        @StringSetter(PARAM_ATTRIBUTE)
        public void setAttribute(String value) {
            this.attribute = value;
        }

        @StringGetter(PARAM_COMPARATOR)
        public Integer getComparator() {
            return this.comparator;
        }

        @StringSetter(PARAM_COMPARATOR)
        public void setComparator(Integer value) {
            this.comparator = value;
        }

        @StringGetter(PARAM_VAL)
        public String getVal() {
            return this.val;
        }

        @StringSetter(PARAM_VAL)
        public void setVal(String value) {
            this.val = value;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_POSITION, "Position where to insert the new condition as integer.");
            comments.put(PARAM_OP, "Combine condition with other conditions as a string (OP_NONE, OP_OR, OP_AND).");
            comments.put(PARAM_COMPLEMENT, "Direct effect of condition (false), complementary effect of condition (true).");
            comments.put(PARAM_ATTRIBUTE, "Attribute ID (String) the filter condition has an effect on.");
            comments.put(PARAM_COMPARATOR, "Compare operator as an integer (e.g 9 stands for EqualVal).");
            comments.put(PARAM_VAL, "Attribute name (String).");
            return comments;
        }
    }

    public void addStopAttributeParams(final StopAttributeParams params) {
        final StopAttributeParams previous = this.getStopAttributeParams().get(params.getAttributeName());

        if ( previous != null ) {
            final boolean removed = removeParameterSet( previous );
            if ( !removed ) throw new RuntimeException( "problem replacing filter params." );
        }

        super.addParameterSet( params );
    }

    public Map<String, StopAttributeParams> getStopAttributeParams() {
        final Map<String, StopAttributeParams> map = new LinkedHashMap< >();

        for ( ConfigGroup pars : getParameterSets( StopAttributeParams.SET_TYPE ) ) {
            final String name = ((StopAttributeParams) pars).getAttributeName();
            final StopAttributeParams old = map.put( name , (StopAttributeParams)	pars );
            if ( old != null ) throw new IllegalStateException( "several names set for stop attribute " + name );
        }
        return map;
    }

    public static class StopAttributeParams extends ReflectiveConfigGroup {
        public static final String SET_TYPE = PARAMSET_STOPATTRIBUTES;

        private static final String PARAM_NAME = "AttName";
        private static final String PARAM_VALUE = "AttVal";
        private static final String PARAM_TYPE = "DataType";

        private String name;
        private String value;
        private String type;

        public StopAttributeParams() {
            super( PARAMSET_STOPATTRIBUTES );
        }

        @StringGetter(PARAM_NAME)
        public String getAttributeName() {
            return this.name;
        }

        @StringSetter(PARAM_NAME)
        public void setAttributeName(String value) {
            this.name = value;
        }

        @StringGetter(PARAM_VALUE)
        public String getAttributeValue() {
            return this.value;
        }

        @StringSetter(PARAM_VALUE)
        public void setAttributeValue(String value) {
            this.value = value;
        }

        @StringGetter(PARAM_TYPE)
        public String getDataType() {
            return this.type;
        }

        @StringSetter(PARAM_TYPE)
        public void setDataType(String value) {
            this.type = value;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_NAME, "Name of the attribute.");
            comments.put(PARAM_VALUE, "Value of the attribute.");
            comments.put(PARAM_TYPE, "Data type of the attribute. Choose between Integer, Double and String");
            return comments;
        }
    }

    public void addRouteAttributeParams(final RouteAttributeParams params) {
        final RouteAttributeParams previous = this.getRouteAttributeParams().get(params.getAttributeName());

        if ( previous != null ) {
            final boolean removed = removeParameterSet( previous );
            if ( !removed ) throw new RuntimeException( "problem replacing filter params." );
        }

        super.addParameterSet( params );
    }

    public Map<String, RouteAttributeParams> getRouteAttributeParams() {
        final Map<String, RouteAttributeParams> map = new LinkedHashMap< >();

        for ( ConfigGroup pars : getParameterSets( RouteAttributeParams.SET_TYPE ) ) {
            final String name = ((RouteAttributeParams) pars).getAttributeName();
            final RouteAttributeParams old = map.put( name , (RouteAttributeParams) 	pars );
            if ( old != null ) throw new IllegalStateException( "several names set for stop attribute " + name );
        }
        return map;
    }

    public static class RouteAttributeParams extends ReflectiveConfigGroup {
        public static final String SET_TYPE = PARAMSET_ROUTEATTRIBUTES;

        private static final String PARAM_NAME = "AttName";
        private static final String PARAM_VALUE = "AttVal";
        private static final String PARAM_TYPE = "DataType";

        private String name;
        private String value;
        private String type;

        public RouteAttributeParams() {
            super( PARAMSET_ROUTEATTRIBUTES );
        }

        @StringGetter(PARAM_NAME)
        public String getAttributeName() {
            return this.name;
        }

        @StringSetter(PARAM_NAME)
        public void setAttributeName(String value) {
            this.name = value;
        }

        @StringGetter(PARAM_VALUE)
        public String getAttributeValue() {
            return this.value;
        }

        @StringSetter(PARAM_VALUE)
        public void setAttributeValue(String value) {
            this.value = value;
        }

        @StringGetter(PARAM_TYPE)
        public String getDataType() {
            return this.type;
        }

        @StringSetter(PARAM_TYPE)
        public void setDataType(String value) {
            this.type = value;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> comments = super.getComments();
            comments.put(PARAM_NAME, "Name of the attribute.");
            comments.put(PARAM_VALUE, "Value of the attribute.");
            comments.put(PARAM_TYPE, "Data type of the attribute. Choose between Integer, Double and String");
            return comments;
        }
    }
}