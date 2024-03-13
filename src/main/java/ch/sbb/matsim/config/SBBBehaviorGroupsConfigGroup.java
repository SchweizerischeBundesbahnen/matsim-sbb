/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.*;

/**
 * @author pmanser / SBB
 */

public class SBBBehaviorGroupsConfigGroup extends ReflectiveConfigGroup {

	static public final String GROUP_NAME = "SBBBehaviorGroups";
	public static final String PARAM_MARGINAL_UTILITY_OF_PARKING_PRICE = "marginalUtilityOfParkingPrice";
	public static final String PARAM_TRANSFER_UTILITY_PER_TRAVEL_TIME = "transferUtilityPerTravelTime";
	public static final String PARAM_TRANSFER_UTILITY_BASE = "transferUtilityBase";
	public static final String PARAM_TRANSFER_UTILITY_MINIMUM = "transferUtilityMinimum";
	public static final String PARAM_TRANSFER_UTILITY_MAXIMUM = "transferUtilityMaximum";
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
	static private final String PARAM_DELTAPARKINGPRICE = "deltaMarginalUtilityOfParkingPrice_util_money";
	static private final String PARAM_DELTATRANSFERUTILITYBASE = "deltaTransferUtilityBase";
	static private final String PARAM_DELTATRANSFERUTILITYPERHOUR = "deltaTransferUtilityPerTravelTime_util_hr";

	private double marginalUtilityOfParkingPrice = 0.0;
	private double transferUtilityBase = 0;
	private double transferUtilityPerTravelTime_utilsPerHour = 0;
	private double transferUtilityMinimum = Double.NEGATIVE_INFINITY;
	private double transferUtilityMaximum = Double.POSITIVE_INFINITY;

	public SBBBehaviorGroupsConfigGroup() {
		super(GROUP_NAME);
	}

	@Override
	public ConfigGroup createParameterSet(final String type) {
		if (BehaviorGroupParams.SET_TYPE.equals(type)) {
			return new BehaviorGroupParams();
		}
		throw new IllegalArgumentException(type);
	}

	@Override
	protected void checkParameterSet(final ConfigGroup module) {
		if (BehaviorGroupParams.SET_TYPE.equals(module.getName())) {
			if (!(module instanceof BehaviorGroupParams)) {
				throw new RuntimeException("unexpected class for module " + module);
			}
		} else {
			throw new IllegalArgumentException(module.getName());
		}
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		if (BehaviorGroupParams.SET_TYPE.equals(set.getName())) {
			addBehaviorGroupParams((BehaviorGroupParams) set);
		} else {
			throw new IllegalArgumentException(set.getName());
		}
	}

	public Map<String, BehaviorGroupParams> getBehaviorGroupParams() {
		final Map<String, BehaviorGroupParams> map = new LinkedHashMap<>();
		for (ConfigGroup pars : getParameterSets(BehaviorGroupParams.SET_TYPE)) {
			final String name = ((BehaviorGroupParams) pars).getBehaviorGroupName();
			final BehaviorGroupParams old = map.put(name, (BehaviorGroupParams) pars);
			if (old != null) {
				throw new IllegalStateException("several parameter sets for behavior group " + name);
			}
		}
		return map;
	}

	public void addBehaviorGroupParams(final BehaviorGroupParams params) {
		final BehaviorGroupParams previous = this.getBehaviorGroupParams().get(params.getBehaviorGroupName());
		if (previous != null) {
			final boolean removed = removeParameterSet(previous);
			if (!removed) {
				throw new RuntimeException("problem replacing behavior group params ");
			}
		}
		super.addParameterSet(params);
	}

	@Override
	public Map<String, String> getComments() {
		Map<String, String> map = super.getComments();
		map.put(PARAM_MARGINAL_UTILITY_OF_PARKING_PRICE, "[utils/money]");
		map.put(PARAM_TRANSFER_UTILITY_BASE, "[utils] base utility for a scored transfer.");
		map.put(PARAM_TRANSFER_UTILITY_PER_TRAVEL_TIME, "[utils/hour] transfer penalty in utils, depending on the total transit travel time. Will be added to the base transfer utility.");
		map.put(PARAM_TRANSFER_UTILITY_MINIMUM, "[utils] minimum utility for transfers");
		map.put(PARAM_TRANSFER_UTILITY_MAXIMUM, "[utils] maximum utility for transfers");
		return map;
	}

	@StringGetter(PARAM_MARGINAL_UTILITY_OF_PARKING_PRICE)
	public String getMarginalUtilityOfParkingPrice_asString() {
		return Double.toString(this.marginalUtilityOfParkingPrice);
	}

	public double getMarginalUtilityOfParkingPrice() {
		return this.marginalUtilityOfParkingPrice;
	}

	@StringSetter(PARAM_MARGINAL_UTILITY_OF_PARKING_PRICE)
	public void setMarginalUtilityOfParkingPrice(String marginalUtilityOfParkingPrice) {
		this.marginalUtilityOfParkingPrice = Double.parseDouble(marginalUtilityOfParkingPrice);
	}

	public void setMarginalUtilityOfParkingPrice(double marginalUtilityOfParkingPrice) {
		this.marginalUtilityOfParkingPrice = marginalUtilityOfParkingPrice;
	}

	@StringGetter(PARAM_TRANSFER_UTILITY_BASE)
	public double getBaseTransferUtility() {
		return this.transferUtilityBase;
	}

	@StringSetter(PARAM_TRANSFER_UTILITY_BASE)
	public void setBaseTransferUtility(double transferUtilityBase) {
		this.transferUtilityBase = transferUtilityBase;
	}

	@StringSetter(PARAM_TRANSFER_UTILITY_PER_TRAVEL_TIME)
	public void setTransferUtilityPerTravelTime(String factor) {
		this.transferUtilityPerTravelTime_utilsPerHour = Double.parseDouble(factor);
	}

	@StringGetter(PARAM_TRANSFER_UTILITY_PER_TRAVEL_TIME)
	public String getTransferUtilityPerTravelTime_asString() {
		return Double.toString(this.transferUtilityPerTravelTime_utilsPerHour);
	}

	public double getTransferUtilityPerTravelTime_utils_hr() {
		return this.transferUtilityPerTravelTime_utilsPerHour;
	}

	public void setTransferUtilityPerTravelTime_utils_hr(double factor) {
		this.transferUtilityPerTravelTime_utilsPerHour = factor;
	}

	@StringGetter(PARAM_TRANSFER_UTILITY_MINIMUM)
	public double getMinimumTransferUtility() {
		return this.transferUtilityMinimum;
	}

	@StringSetter(PARAM_TRANSFER_UTILITY_MINIMUM)
	public void setMinimumTransferUtility(double transferUtilityMinimum) {
		this.transferUtilityMinimum = transferUtilityMinimum;
	}

	@StringGetter(PARAM_TRANSFER_UTILITY_MAXIMUM)
	public double getMaximumTransferUtility() {
		return this.transferUtilityMaximum;
	}

	@StringSetter(PARAM_TRANSFER_UTILITY_MAXIMUM)
	public void setMaximumTransferUtility(double transferUtilityMaximum) {
		this.transferUtilityMaximum = transferUtilityMaximum;
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
			if (PersonGroupValues.SET_TYPE.equals(type)) {
				return new PersonGroupValues();
			}
			throw new IllegalArgumentException(type);
		}

		@Override
		protected void checkParameterSet(final ConfigGroup module) {
			if (PersonGroupValues.SET_TYPE.equals(module.getName())) {
				if (!(module instanceof PersonGroupValues)) {
					throw new RuntimeException("wrong class for " + module);
				}
				final Set<String> t = ((PersonGroupValues) module).getPersonGroupAttributeValues();
				for (String value : t) {
					if (getPersonGroupByAttribute(value) != null) {
						throw new IllegalStateException("already a parameter set for attribute value " + t);
					}
				}
			} else {
				throw new IllegalArgumentException(module.getName());
			}
		}

		public Collection<String> getPersonGroupAttributes() {
			return this.getPersonGroupByAttribute().keySet();
		}

		public Map<String, PersonGroupValues> getPersonGroupByAttribute() {
			final Map<String, PersonGroupValues> map = new LinkedHashMap<>();
			for (ConfigGroup pars : getParameterSets(PersonGroupValues.SET_TYPE)) {
				final Set<String> attributeValues = ((PersonGroupValues) pars).getPersonGroupAttributeValues();
				for (String value : attributeValues) {
					final PersonGroupValues old = map.put(value, (PersonGroupValues) pars);
					if (old != null) {
						throw new IllegalStateException("several parameter sets for attribute value " + value);
					}
				}
			}
			return map;
		}

		public PersonGroupValues getPersonGroupByAttribute(final String value) {
			return this.getPersonGroupByAttribute().get(value);
		}

		public void addPersonGroupByAttribute(final PersonGroupValues values) {
			Set<String> attributes = values.getPersonGroupAttributeValues();
			for (String attribute : attributes) {
				final PersonGroupValues previous = this.getPersonGroupByAttribute().get(attribute);
				if (previous != null) {
					final boolean removed = removeParameterSet(previous);
					if (!removed) {
						throw new RuntimeException("problem replacing person group type params");
					}
				}
			}
			super.addParameterSet(values);
		}
	}

	public static class PersonGroupValues extends ReflectiveConfigGroup {

		public static final String SET_TYPE = PARAMSET_PERSONGROUPATTRIBUTE;

		private final Set<String> attributeValues = new LinkedHashSet<>();

		private double deltaMarginalUtilityOfParkingPrice = 0.0;
		private double deltaTransferUtilityBase = 0.0;
		private double deltaTransferUtilityPerTravelTime = 0.0;

		public PersonGroupValues() {
			super(PARAMSET_PERSONGROUPATTRIBUTE);
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

		@StringGetter(PARAM_DELTAPARKINGPRICE)
		public String getDeltaMarginalUtilityOfParkingPrice_asString() {
			return Double.toString(this.deltaMarginalUtilityOfParkingPrice);
		}

		public double getDeltaMarginalUtilityOfParkingPrice() {
			return this.deltaMarginalUtilityOfParkingPrice;
		}

		@StringSetter(PARAM_DELTAPARKINGPRICE)
		public void setDeltaMarginalUtilityOfParkingPrice(String value) {
			this.deltaMarginalUtilityOfParkingPrice = Double.parseDouble(value);
		}

		public void setDeltaMarginalUtilityOfParkingPrice(double value) {
			this.deltaMarginalUtilityOfParkingPrice = value;
		}

		@StringGetter(PARAM_DELTATRANSFERUTILITYPERHOUR)
		public double getDeltaTransferUtilityPerTravelTime() {
			return this.deltaTransferUtilityPerTravelTime;
		}

		@StringSetter(PARAM_DELTATRANSFERUTILITYPERHOUR)
		public void setDeltaTransferUtilityPerTravelTime(double value) {
			this.deltaTransferUtilityPerTravelTime = value;
		}

		@StringGetter(PARAM_DELTATRANSFERUTILITYBASE)
		public double getDeltaBaseTransferUtility() {
			return this.deltaTransferUtilityBase;
		}

		@StringSetter(PARAM_DELTATRANSFERUTILITYBASE)
		public void setDeltaBaseTransferUtility(double value) {
			this.deltaTransferUtilityBase = value;
		}

		@Override
		public Map<String, String> getComments() {
			Map<String, String> comments = super.getComments();
			comments.put(PARAM_ATTRIBUTE, "Attributes of the person group. It is possible to give a comma-separated list of different attributes");
			return comments;
		}

		@Override
		public ConfigGroup createParameterSet(final String mode) {
			if (ModeCorrection.SET_TYPE.equals(mode)) {
				return new ModeCorrection();
			}
			throw new IllegalArgumentException(mode);
		}

		@Override
		protected void checkParameterSet(final ConfigGroup module) {
			if (ModeCorrection.SET_TYPE.equals(module.getName())) {
				if (!(module instanceof ModeCorrection)) {
					throw new RuntimeException("wrong class for " + module);
				}
				final String t = ((ModeCorrection) module).getMode();
				if (getModeCorrectionsForMode(t) != null) {
					throw new IllegalStateException("already a parameter set for mode " + t);
				}
			} else {
				throw new IllegalArgumentException(module.getName());
			}
		}

		public Collection<String> getModes() {
			return this.getModeCorrectionParams().keySet();
		}

		public Map<String, ModeCorrection> getModeCorrectionParams() {
			final Map<String, ModeCorrection> map = new LinkedHashMap<>();
			for (ConfigGroup pars : getParameterSets(ModeCorrection.SET_TYPE)) {
				final String mode = ((ModeCorrection) pars).getMode();
				final ModeCorrection old = map.put(mode, (ModeCorrection) pars);
				if (old != null) {
					throw new IllegalStateException("several parameter sets for mode correction " + mode);
				}
			}
			return map;
		}

		public ModeCorrection getModeCorrectionsForMode(final String type) {
			return this.getModeCorrectionParams().get(type);
		}

		public void addModeCorrection(final ModeCorrection modeCorrection) {
			final ModeCorrection previous = this.getModeCorrectionParams().get(modeCorrection.getMode());
			if (previous != null) {
				final boolean removed = removeParameterSet(previous);
				if (!removed) {
					throw new RuntimeException("problem replacing mode correction params ");
				}
			}
			super.addParameterSet(modeCorrection);
		}

		public boolean isSet() {
			return !this.getModeCorrectionParams().isEmpty() || this.deltaMarginalUtilityOfParkingPrice != 0.0 || this.deltaTransferUtilityPerTravelTime != 0.0;
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
			if (this.mode == null) {
				throw new RuntimeException("no mode defined for parameterset " + this);
			}
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
			return ((this.getConstant() != 0) || (this.getMargUtilOfTime() != 0) || (this.getMargUtilOfDistance() != 0) || (this.getDistanceRate() != 0));
		}
	}
}
