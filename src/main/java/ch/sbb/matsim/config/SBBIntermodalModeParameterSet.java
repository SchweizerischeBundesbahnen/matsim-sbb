package ch.sbb.matsim.config;

import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ReflectiveConfigGroup;

public class SBBIntermodalModeParameterSet extends ReflectiveConfigGroup {

	public static final String PARAM_MODE_DESC = "Mode to use as feeder";
	public static final String PARAM_DETOUR_FACTOR_DESC = "Factor to multiply the fastest travel time with as an estimation of potential detours to pick up other passengers.";
	public static final String PARAM_SIMULATION_NETWORKMODE_DESC = "If true, the mode will be added as main-mode to be simulated on the road network.";
	public static final String PARAM_ROUTING_NETWORKMODE_DESC = "If true, the mode will be added as main-mode to be simulated on the road network.";
	public static final String PARAM_ACCESSTIME_ZONEATT_DESC = "Zone Id field for mode specific access (or wait) time (in seconds).";
	public static final String PARAM_DETOUR_FACTOR_ZONEATT_DESC = "Zone Id field for mode specific detour factor.";
	public static final String PARAM_EGRESSTIME_ZONEATT_DESC = "Zone Id field for mode specific egress time (in seconds).";
	static final String TYPE = "mode";
	static private final String PARAM_MODE = "mode";
	static private final String PARAM_DETOUR_FACTOR = "detourFactor";
	static private final String PARAM_SIMULATION_NETWORKMODE = "isSimulatedOnNetwork";
	static private final String PARAM_ROUTING_NETWORKMODE = "isRoutedOnNetwork";
	static private final String PARAM_ACCESSTIME_ZONEATT = "accessTimeZonesAttributeName";
	static private final String PARAM_DETOUR_FACTOR_ZONEATT = "detourFactorZonesAttributeName";
	static private final String PARAM_EGRESSTIME_ZONEATT = "egressTimeZonesAttributeName";
	static private final String PARAM_USEMINIMALTRANSFERTIMES_DESC = "use minimal transfer times";
	static private final String PARAM_USEMINIMALTRANSFERTIMES = "useMinimalTransferTimes";
	static private final String PARAM_WAITINGTIME = "waitingTime";
	static private final String PARAM_WAITINGTIME_DESC = "Additional waiting time in seconds.";

	static private final String PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE = "personActivityFilterAttribute";
	static private final String PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE_DESC = "activities from/to which feeder mode is available";

	static private final String PARAM_CACHE_FILE = "intermodalAccessCacheFile";
	static private final String PARAM_CACHE_FILE_DESC = "Cached intermodal travel times to and from stations.";

	private String mode = null;
	private Integer waitingTime = 0;
	private Double detourFactor = 1.0;
	private boolean routedOnNetwork = false;
	private boolean simulatedOnNetwork = false;
	private String accessTimeZoneId = null;
	private String egressTimeZoneId = null;
	private String detourFactorZoneId = null;
	private boolean useMinimalTransferTimes = false;
	private String personActivityFilterAttribute = null;
	private String intermodalAccessCacheFile = null;

	public SBBIntermodalModeParameterSet() {
		super(TYPE);
	}

	public SBBIntermodalModeParameterSet(String mode, int waitingTime, double detourFactor) {
		super(TYPE);
		this.mode = mode;
		this.waitingTime = waitingTime;
		this.detourFactor = detourFactor;
	}

	@StringGetter(PARAM_MODE)
	public String getMode() {
		return this.mode;
	}

	@StringSetter(PARAM_MODE)
	public void setMode(String mode) {
		this.mode = mode;
	}

	@StringGetter(PARAM_WAITINGTIME)
	public Integer getWaitingTime() {
		return this.waitingTime;
	}

	@StringSetter(PARAM_WAITINGTIME)
	public void setWaitingTime(int waitingTime) {
		this.waitingTime = waitingTime;
	}

	@StringGetter(PARAM_DETOUR_FACTOR)
	public Double getDetourFactor() {
		return this.detourFactor;
	}

	@StringSetter(PARAM_DETOUR_FACTOR)
	public void setDetourFactor(double detourFactor) {
		this.detourFactor = detourFactor;
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

	@StringGetter(PARAM_ACCESSTIME_ZONEATT)
	public String getAccessTimeZoneId() {
		return accessTimeZoneId;
	}

	@StringSetter(PARAM_ACCESSTIME_ZONEATT)
	public void setAccessTimeZoneId(String accessTimeZoneId) {
		this.accessTimeZoneId = accessTimeZoneId;
	}

	@StringGetter(PARAM_EGRESSTIME_ZONEATT)
	public String getEgressTimeZoneId() {
		return egressTimeZoneId;
	}

	@StringSetter(PARAM_EGRESSTIME_ZONEATT)
	public void setEgressTimeZoneId(String egressTimeZoneId) {
		this.egressTimeZoneId = egressTimeZoneId;
	}

	@StringGetter(PARAM_DETOUR_FACTOR_ZONEATT)
	public String getDetourFactorZoneId() {
		return detourFactorZoneId;
	}

	@StringSetter(PARAM_DETOUR_FACTOR_ZONEATT)
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

	@StringSetter(PARAM_CACHE_FILE)
	public void setIntermodalAccessCacheFile(String intermodalAccessCacheFile) {
		this.intermodalAccessCacheFile = intermodalAccessCacheFile;
	}

	@StringGetter(PARAM_CACHE_FILE)
	public String getIntermodalAccessCacheFileString() {
		return intermodalAccessCacheFile;
	}

	public URL getIntermodalAccessCacheFile(URL context) {
		return ConfigGroup.getInputFileURL(context, intermodalAccessCacheFile);
	}

	@Override
	public Map<String, String> getComments() {
		Map<String, String> comments = super.getComments();
		comments.put(PARAM_MODE, PARAM_MODE_DESC);
		comments.put(PARAM_DETOUR_FACTOR, PARAM_DETOUR_FACTOR_DESC);
		comments.put(PARAM_SIMULATION_NETWORKMODE, PARAM_SIMULATION_NETWORKMODE_DESC);
		comments.put(PARAM_ROUTING_NETWORKMODE, PARAM_ROUTING_NETWORKMODE_DESC);
		comments.put(PARAM_WAITINGTIME, PARAM_WAITINGTIME_DESC);
		comments.put(PARAM_DETOUR_FACTOR_ZONEATT, PARAM_DETOUR_FACTOR_ZONEATT_DESC);
		comments.put(PARAM_EGRESSTIME_ZONEATT, PARAM_EGRESSTIME_ZONEATT_DESC);
		comments.put(PARAM_ACCESSTIME_ZONEATT, PARAM_ACCESSTIME_ZONEATT_DESC);
		comments.put(PARAM_USEMINIMALTRANSFERTIMES, PARAM_USEMINIMALTRANSFERTIMES_DESC);
		comments.put(PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE, PARAM_PERSON_ACTIVITY_FILTER_ATTRIBUTE_DESC);
		comments.put(PARAM_CACHE_FILE, PARAM_CACHE_FILE_DESC);
		return comments;
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);
		SwissRailRaptorConfigGroup railRaptorConfigGroup = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
		if (detourFactor != 1.0 && detourFactorZoneId != null) {
			throw new RuntimeException("Both Zone based and network wide detour factor are set for mode " + mode + " . Please set only one of them.");
		}
		if (waitingTime > 0 && accessTimeZoneId != null) {
			throw new RuntimeException("Both Zone based and network wide detour factor are set for mode " + mode + " . Please set only one of them.");
		}
		if (!isRoutedOnNetwork() && isSimulatedOnNetwork()) {
			throw new RuntimeException("Mode " + mode + " is simulated but not routed on network. This will not work.");
		}

		Set<String> modesInRaptorConfig = railRaptorConfigGroup.getIntermodalAccessEgressParameterSets()
				.stream()
				.map(p -> p.getMode())
				.collect(Collectors.toSet());
		if (!modesInRaptorConfig.contains(mode)) {
			throw new RuntimeException("Mode " + mode + "is defined in SBBIntermodalConfigGroup, but not in SwissRailRaptorConfigGroup. " +
					" This will most likely be unwanted.");
		}

	}
}
