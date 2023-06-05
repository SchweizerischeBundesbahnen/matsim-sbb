package ch.sbb.matsim.config;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SBBIntermodalModeParameterSet extends ReflectiveConfigGroup {

	public static final String PARAM_MODE_DESC = "Mode to use as feeder";
	public static final String PARAM_SIMULATION_NETWORKMODE_DESC = "If true, the mode will be added as main-mode to be simulated on the network according to its routing module.";
	public static final String PARAM_ROUTING_NETWORKMODE_DESC = "If true, the mode will be routed on the network.";
	public static final String PARAM_ACCESSTIME_ZoneATT_DESC = "Zone Id field for mode specific access (or wait) time (in seconds).";
	public static final String PARAM_DETOUR_FACTOR_ZoneATT_DESC = "Zone Id field for mode specific detour factor.";
	static final String TYPE = "mode";
	static private final String PARAM_MODE = "mode";
	static private final String PARAM_SIMULATION_NETWORKMODE = "isSimulatedOnNetwork";
	static private final String PARAM_ROUTING_NETWORKMODE = "isRoutedOnNetwork";
	static private final String PARAM_ACCESSTIME_ZoneATT = "accessTimeZonesAttributeName";
	static private final String PARAM_DETOUR_FACTOR_ZoneATT = "detourFactorZonesAttributeName";
	static private final String PARAM_USEMINIMALTRANSFERTIMES_DESC = "use minimal transfer times";
	static private final String PARAM_USEMINIMALTRANSFERTIMES = "useMinimalTransferTimes";


	static private final String PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE = "personActivityFilterAttribute";
	static private final String PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE_DESC = "activities from/to which feeder mode is available";

	private String mode = null;
	private boolean routedOnNetwork = false;
	private boolean simulatedOnNetwork = false;
	private String accessTimeZoneId = null;
	private String detourFactorZoneId = null;
	private boolean useMinimalTransferTimes = false;
	private String personActivityFilterAttribute = null;
	private String intermodalAccessCacheFile = null;

	public SBBIntermodalModeParameterSet() {
		super(TYPE);
	}


	@StringGetter(PARAM_MODE)
	public String getMode() {
		return this.mode;
	}

	@StringSetter(PARAM_MODE)
	public void setMode(String mode) {
		this.mode = mode;
	}

	@StringGetter(PARAM_SIMULATION_NETWORKMODE)
	public boolean isSimulatedOnNetwork() {
		return this.simulatedOnNetwork;
	}

	@StringSetter(PARAM_SIMULATION_NETWORKMODE)
	public void setSimulatedOnNetwork(boolean simulatedOnNetwork) {
		this.simulatedOnNetwork = simulatedOnNetwork;
	}

	@StringGetter(PARAM_USEMINIMALTRANSFERTIMES)
	public boolean doUseMinimalTransferTimes() {
		return this.useMinimalTransferTimes;
	}

	@StringSetter(PARAM_USEMINIMALTRANSFERTIMES)
	public void setUseMinimalTransferTimes(boolean useMinimalTransferTimes) {
		this.useMinimalTransferTimes = useMinimalTransferTimes;
	}

	@StringGetter(PARAM_ROUTING_NETWORKMODE)
	public boolean isRoutedOnNetwork() {
		return this.routedOnNetwork;
	}

	@StringSetter(PARAM_ROUTING_NETWORKMODE)
	public void setRoutedOnNetwork(boolean routedOnNetwork) {
		this.routedOnNetwork = routedOnNetwork;
	}

	@StringGetter(PARAM_ACCESSTIME_ZoneATT)
	public String getAccessTimeZoneId() {
		return accessTimeZoneId;
	}

	@StringSetter(PARAM_ACCESSTIME_ZoneATT)
	public void setAccessTimeZoneId(String accessTimeZoneId) {
		this.accessTimeZoneId = accessTimeZoneId;
	}


	@StringGetter(PARAM_DETOUR_FACTOR_ZoneATT)
	public String getDetourFactorZoneId() {
		return detourFactorZoneId;
	}

	@StringSetter(PARAM_DETOUR_FACTOR_ZoneATT)
	public void setDetourFactorZoneId(String detourFactorZoneId) {
		this.detourFactorZoneId = detourFactorZoneId;
	}

	@StringGetter(PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE)
	public String getParamPersonActivityFilterAttribute() {
		return personActivityFilterAttribute;
	}

	@StringSetter(PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE)
	public void setParamPersonActivityFilterAttribute(String personActivityFilterAttribute) {
		this.personActivityFilterAttribute = personActivityFilterAttribute;
	}

	public URL getIntermodalAccessCacheFile(URL context) {
		return ConfigGroup.getInputFileURL(context, intermodalAccessCacheFile);
	}

	@Override
	public Map<String, String> getComments() {
		Map<String, String> comments = super.getComments();
		comments.put(PARAM_MODE, PARAM_MODE_DESC);
		comments.put(PARAM_SIMULATION_NETWORKMODE, PARAM_SIMULATION_NETWORKMODE_DESC);
		comments.put(PARAM_ROUTING_NETWORKMODE, PARAM_ROUTING_NETWORKMODE_DESC);
		comments.put(PARAM_DETOUR_FACTOR_ZoneATT, PARAM_DETOUR_FACTOR_ZoneATT_DESC);
		comments.put(PARAM_ACCESSTIME_ZoneATT, PARAM_ACCESSTIME_ZoneATT_DESC);
		comments.put(PARAM_USEMINIMALTRANSFERTIMES, PARAM_USEMINIMALTRANSFERTIMES_DESC);
		comments.put(PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE, PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE_DESC);
		return comments;
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);
		SwissRailRaptorConfigGroup railRaptorConfigGroup = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
		if (!isRoutedOnNetwork() && isSimulatedOnNetwork()) {
			throw new RuntimeException("Mode " + mode + " is simulated but not routed on network. This will not work.");
		}

		Set<String> modesInRaptorConfig = railRaptorConfigGroup.getIntermodalAccessEgressParameterSets()
				.stream()
				.map(IntermodalAccessEgressParameterSet::getMode)
				.collect(Collectors.toSet());
		if (!modesInRaptorConfig.contains(mode)) {
			throw new RuntimeException("Mode " + mode + "is defined in SBBIntermodalConfigGroup, but not in SwissRailRaptorConfigGroup. " +
					" This will most likely be unwanted.");
		}

	}
}
