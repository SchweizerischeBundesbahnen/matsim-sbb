package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author mrieser / Simunto GmbH
 */
public class SBBIntermodalRaptorStopFinder implements RaptorStopFinder {

	private final static Logger log = LogManager.getLogger(SBBIntermodalRaptorStopFinder.class);

	private final RaptorIntermodalAccessEgress intermodalAE;
	private final Map<String, SBBIntermodalModeParameterSet> intermodalModeParams;
	private final Map<String, RoutingModule> routingModules;
	private final TransitSchedule transitSchedule;
	private final Random random = MatsimRandom.getLocalInstance();
	private final AccessEgressRouteCache accessEgressRouteCache;
	private final IntermodalAccessEgressParameterSet walkParameterset;

	@Inject
	public SBBIntermodalRaptorStopFinder(Config config, RaptorIntermodalAccessEgress intermodalAE,
										 Map<String, Provider<RoutingModule>> routingModuleProviders,
										 TransitSchedule transitSchedule, AccessEgressRouteCache accessEgressRouteCache) {
		this.intermodalAE = intermodalAE;
		this.transitSchedule = transitSchedule;
		this.accessEgressRouteCache = accessEgressRouteCache;

		SBBIntermodalConfiggroup intermodalConfigGroup = ConfigUtils.addOrGetModule(config, SBBIntermodalConfiggroup.class);
		this.intermodalModeParams = intermodalConfigGroup.getModeParameterSets().stream().collect(Collectors.toMap(SBBIntermodalModeParameterSet::getMode, set -> set, (a, b) -> a));
		SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
		walkParameterset = srrConfig.getIntermodalAccessEgressParameterSets().stream().filter(l -> l.getMode().equals(TransportMode.walk)).findFirst().orElseThrow(RuntimeException::new);
		this.routingModules = new HashMap<>();
		if (srrConfig.isUseIntermodalAccessEgress()) {
			for (IntermodalAccessEgressParameterSet params : srrConfig.getIntermodalAccessEgressParameterSets()) {
				String mode = params.getMode();
				this.routingModules.put(mode, routingModuleProviders.get(mode).get());

			}
		}

	}

	@Override
	public List<InitialStop> findStops(Facility fromFacility, Facility toFacility, Person person, double departureTime, Attributes routingAttributes, RaptorParameters parameters,
			SwissRailRaptorData data, Direction type) {
		if (type == Direction.ACCESS) {
			return findAccessStops(fromFacility, person, departureTime, parameters, data);
		}
		if (type == Direction.EGRESS) {
			return findEgressStops(toFacility, person, departureTime, parameters, data);
		}
		return Collections.emptyList();
	}

	private List<InitialStop> findAccessStops(Facility facility, Person person, double departureTime, RaptorParameters parameters, SwissRailRaptorData data) {
        SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
        if (srrCfg.isUseIntermodalAccessEgress()) {
            return findIntermodalStops(facility, person, departureTime, Direction.ACCESS, parameters, data);
        } else {
            double distanceFactor = data.config.getBeelineWalkDistanceFactor();
            List<TransitStopFacility> stops = findNearbyStops(facility, parameters, data);
            return stops.stream().map(stop -> {
				double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
				double travelTime = Math.ceil(beelineDistance / parameters.getBeelineWalkSpeed());
				double disutility = travelTime * -parameters.getMarginalUtilityOfTravelTime_utl_s(TransportMode.non_network_walk);
				return new InitialStop(stop, disutility, travelTime, beelineDistance * distanceFactor, TransportMode.non_network_walk);
			}).collect(Collectors.toList());
		}
	}

	private List<InitialStop> findEgressStops(Facility facility, Person person, double departureTime, RaptorParameters parameters, SwissRailRaptorData data) {
		SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
		if (srrCfg.isUseIntermodalAccessEgress()) {
			return findIntermodalStops(facility, person, departureTime, Direction.EGRESS, parameters, data);
		} else {
			double distanceFactor = data.config.getBeelineWalkDistanceFactor();
			List<TransitStopFacility> stops = findNearbyStops(facility, parameters, data);
			return stops.stream().map(stop -> {
				double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
				double travelTime = Math.ceil(beelineDistance / parameters.getBeelineWalkSpeed());
				double disutility = travelTime * -parameters.getMarginalUtilityOfTravelTime_utl_s(TransportMode.non_network_walk);
				return new InitialStop(stop, disutility, travelTime, beelineDistance * distanceFactor, TransportMode.non_network_walk);
			}).collect(Collectors.toList());
		}
	}

	private List<InitialStop> findIntermodalStops(Facility facility, Person person, double departureTime, Direction direction, RaptorParameters parameters, SwissRailRaptorData data) {
		SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
		double x = facility.getCoord().getX();
		double y = facility.getCoord().getY();
		List<InitialStop> initialStops = new ArrayList<>();

		List<IntermodalAccessEgressParameterSet> filteredParameterSet = new ArrayList<>();
		for (IntermodalAccessEgressParameterSet paramSet : srrCfg.getIntermodalAccessEgressParameterSets()) {
			if (personMatches(facility, person, paramSet)) {
				filteredParameterSet.add(paramSet);
			}
		}
		if (filteredParameterSet.size() > 0) {
			switch (srrCfg.getIntermodalAccessEgressModeSelection()) {
				case CalcLeastCostModePerStop:
					for (IntermodalAccessEgressParameterSet parameterSet : filteredParameterSet) {
						addInitialStopsForParamSet(facility, person, departureTime, direction, parameters, data, x, y, initialStops, parameterSet);
					}
					break;
				case RandomSelectOneModePerRoutingRequestAndDirection:
					int counter = 0;
					do {
						int rndSelector = random.nextInt(filteredParameterSet.size());
						IntermodalAccessEgressParameterSet parameterSet = filteredParameterSet.get(rndSelector);
						List<IntermodalAccessEgressParameterSet> params = new ArrayList<>();
						params.add(walkParameterset);
						if (!parameterSet.getMode().equals(TransportMode.walk)) {
							params.add(parameterSet);
						}
						for (IntermodalAccessEgressParameterSet set : params) {
							addInitialStopsForParamSet(facility, person, departureTime, direction, parameters, data, x, y,
									initialStops, set);
						}
						counter++;
						// try again if no initial stop was found for the parameterset. Avoid infinite loop by limiting number of tries.
					} while (initialStops.isEmpty() && counter < 2 * srrCfg.getIntermodalAccessEgressParameterSets().size());
					break;
				default:
					throw new RuntimeException(srrCfg.getIntermodalAccessEgressModeSelection() + " : not implemented!");
			}
		}

		return initialStops;
	}

	private boolean personMatches(Facility facility, Person person, IntermodalAccessEgressParameterSet paramset) {
		String personFilterAttribute = paramset.getPersonFilterAttribute();
		String personFilterValue = paramset.getPersonFilterValue();
		Object attr = null;
		String attrValue = null;
		boolean personDoesMatch = true;
		if (personFilterAttribute != null) {
			attr = person.getAttributes().getAttribute(personFilterAttribute);
			attrValue = attr == null ? null : attr.toString();
			personDoesMatch = personFilterValue.equals(attrValue);
		}
		if (personDoesMatch) {
			Optional<String> actType = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities).stream()
					.filter(activity -> activity.getCoord().equals(facility.getCoord())).map(Activity::getType).findAny();
			if (actType.isPresent()) {
				final String activityType = actType.get();
				List<SBBIntermodalModeParameterSet> filtered = intermodalModeParams.values().stream().filter(a -> a.getMode().equals(paramset.getMode()))
						.collect(Collectors.toList());
				if (filtered.size() == 1) {
					String personActivityFilterAttribute = filtered.get(0).getParamPersonActivityFilterAttribute();
					if (personActivityFilterAttribute != null) {
						attr = person.getAttributes().getAttribute(personActivityFilterAttribute);
						if (attr != null) {
							personDoesMatch = false;
							attrValue = attr.toString();
							for (String at : attrValue.split(",")) {
								if (activityType.startsWith(at) && !at.equals("")) {
									personDoesMatch = true;
									break;
								}
							}
						}
					}
				}
			}
		}
		return personDoesMatch;
	}

	private void addInitialStopsForParamSet(Facility facility, Person person, double departureTime, Direction direction, RaptorParameters parameters, SwissRailRaptorData data, double x, double y,
			List<InitialStop> initialStops, IntermodalAccessEgressParameterSet paramset) {
		double radius = paramset.getMaxRadius();
		String mode = paramset.getMode();
		SBBIntermodalModeParameterSet params = this.intermodalModeParams.get(mode);

		boolean useMinimalTransferTimes = this.doUseMinimalTransferTimes(mode);
		String overrideMode = null;
		if (mode.equals(SBBModes.WALK_MAIN_MAINMODE) || mode.equals(SBBModes.PT_FALLBACK_MODE)) {
			overrideMode = SBBModes.ACCESS_EGRESS_WALK;
		}
		String linkIdAttribute = paramset.getLinkIdAttribute();
		String stopFilterAttribute = paramset.getStopFilterAttribute();
		String stopFilterValue = paramset.getStopFilterValue();

		Collection<TransitStopFacility> stopFacilities = data.stopsQT.getDisk(x, y, radius);
		for (TransitStopFacility stop : stopFacilities) {
			boolean filterMatches = true;
			if (stopFilterAttribute != null) {
				Object attr = stop.getAttributes().getAttribute(stopFilterAttribute);
				String attrValue = attr == null ? null : attr.toString();
				filterMatches = stopFilterValue.equals(attrValue);
			}
			if (filterMatches) {
				Facility stopFacility = stop;
				if (linkIdAttribute != null) {
					Object attr = stop.getAttributes().getAttribute(linkIdAttribute);
					if (attr != null) {
						stopFacility = new ChangedLinkFacility(stop, Id.create(attr.toString(), Link.class));
					}
				}

				List<? extends PlanElement> routeParts;
				RoutingModule module = this.routingModules.get(mode);
				if (direction == Direction.ACCESS) {
					if (params != null && params.isRoutedOnNetwork() && (!params.isSimulatedOnNetwork())) {
						routeParts = getCachedTravelTime(stopFacility, facility, departureTime, person, mode, module, true);
					} else {
                        if (stopFacility.getLinkId() == null) {
                            LogManager.getLogger(getClass()).warn(stop.getName() + " has no link Id associated.");
                        }
                        if (facility.getLinkId() == null) {
                            LogManager.getLogger(getClass()).warn("Facility " + facility.getCoord() + " has no link Id associated.");
                        }
                        Objects.requireNonNull(facility.getLinkId(), "Facility" + facility.getCoord() + "has no link Id associated.");
                        Objects.requireNonNull(stopFacility.getLinkId(), "StopFacility" + stop.getName() + "has no link Id associated.");
                        routeParts = module.calcRoute(DefaultRoutingRequest.withoutAttributes(facility, stopFacility, departureTime, person));
                    }
					if (routeParts == null) continue;

				} else { // it's Egress
					// We don't know the departure time for the egress trip, so just use the original departureTime,
					// although it is wrong and might result in a wrong traveltime and thus wrong route.
					if (params != null && params.isRoutedOnNetwork() && (!params.isSimulatedOnNetwork())) {
						routeParts = getCachedTravelTime(stopFacility, facility, departureTime, person, mode, module, false);
					} else {
                      	Objects.requireNonNull(facility.getLinkId(), "Facility" + facility.getCoord() + "has no link Id associated.");
						Objects.requireNonNull(stopFacility.getLinkId(), "StopFacility" + stop.getName() + "has no link Id associated.");
                        routeParts = module.calcRoute(DefaultRoutingRequest.withoutAttributes(stopFacility, facility, departureTime, person));
                    }
					if (routeParts == null) continue;
					// clear the (wrong) departureTime so users don't get confused

					for (PlanElement pe : routeParts) {
						if (pe instanceof Leg) {
							((Leg) pe).setDepartureTimeUndefined();
						}
					}

				}
				if (overrideMode != null) {
					for (PlanElement pe : routeParts) {
						if (pe instanceof Leg) {
							((Leg) pe).setMode(overrideMode);
						}
					}
				}
				if (stopFacility != stop) {
					if (direction == Direction.ACCESS) {
						Leg transferLeg = PopulationUtils.createLeg(SBBModes.ACCESS_EGRESS_WALK);
						Route transferRoute = RouteUtils.createGenericRouteImpl(stopFacility.getLinkId(), stop.getLinkId());
						double transferTime = 0.0;
						if (useMinimalTransferTimes) {
							transferTime = this.getMinimalTransferTime(stop);
						}
						transferRoute.setTravelTime(transferTime);
						transferRoute.setDistance(0);
						transferLeg.setRoute(transferRoute);
						transferLeg.setTravelTime(transferTime);

						List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
						tmp.addAll(routeParts);
						tmp.add(transferLeg);
						routeParts = tmp;
					} else {
						Leg transferLeg = PopulationUtils.createLeg(SBBModes.ACCESS_EGRESS_WALK);
						Route transferRoute = RouteUtils.createGenericRouteImpl(stop.getLinkId(), stopFacility.getLinkId());
						double transferTime = 0.0;
						if (useMinimalTransferTimes) {
							transferTime = this.getMinimalTransferTime(stop);
						}
						transferRoute.setTravelTime(transferTime);
						transferRoute.setDistance(0);
						transferLeg.setRoute(transferRoute);
						transferLeg.setTravelTime(transferTime);

						List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
						tmp.add(transferLeg);
						tmp.addAll(routeParts);
						routeParts = tmp;
					}
				}
				RaptorIntermodalAccessEgress.RIntermodalAccessEgress accessEgress = this.intermodalAE.calcIntermodalAccessEgress(routeParts, parameters, person, direction);
				InitialStop iStop = new InitialStop(stop, accessEgress.disutility, accessEgress.travelTime, accessEgress.routeParts);
				initialStops.add(iStop);
			}
		}
	}

	private List<? extends PlanElement> getCachedTravelTime(Facility stopFacility, Facility actFacility, double departureTime, Person person, String mode, RoutingModule module, boolean backwards) {
        AccessEgressRouteCache.RouteCharacteristics characteristics = this.accessEgressRouteCache.getCachedRouteCharacteristics(mode, stopFacility, actFacility, module, person);

        Id<Link> startLink = backwards ? actFacility.getLinkId() : stopFacility.getLinkId();
        Id<Link> endLink = backwards ? stopFacility.getLinkId() : actFacility.getLinkId();
        List<PlanElement> travel = new ArrayList<>();
        double accessTime = backwards ? characteristics.egressTime() : characteristics.accessTime();
        double egressTime = backwards ? characteristics.accessTime() : characteristics.egressTime();

        Leg leg = PopulationUtils.createLeg(mode);
        Route route = RouteUtils.createGenericRouteImpl(startLink, endLink);
        double travelTime = characteristics.travelTime();
        if (!Double.isNaN(accessTime)) {
            travelTime += accessTime;
        }
        if (!Double.isNaN(egressTime)) {
            travelTime += egressTime;
        }
        route.setTravelTime(travelTime);
        route.setDistance(characteristics.distance());
        leg.setTravelTime(travelTime);
		leg.setRoute(route);
		leg.setDepartureTime(departureTime);
		travel.add(leg);

		return travel;

	}

	private boolean doUseMinimalTransferTimes(String mode) {
		var params = this.intermodalModeParams.get(mode);
		return (params != null ? params.doUseMinimalTransferTimes() : false);
	}

	private double getMinimalTransferTime(TransitStopFacility stop) {
		MinimalTransferTimes mtt = this.transitSchedule.getMinimalTransferTimes();
		double transferTime = mtt.get(stop.getId(), stop.getId());
		if (Double.isNaN(transferTime)) {
			// return a default value of 30 seconds
			return 30.0;
		} else {
			return transferTime;
		}
	}

	private List<TransitStopFacility> findNearbyStops(Facility facility, RaptorParameters parameters, SwissRailRaptorData data) {
		double x = facility.getCoord().getX();
		double y = facility.getCoord().getY();
		Collection<TransitStopFacility> stopFacilities = data.stopsQT.getDisk(x, y, parameters.getSearchRadius());
		if (stopFacilities.size() < 2) {
			TransitStopFacility nearestStop = data.stopsQT.getClosest(x, y);
			double nearestDistance = CoordUtils.calcEuclideanDistance(facility.getCoord(), nearestStop.getCoord());
			stopFacilities = data.stopsQT.getDisk(x, y, nearestDistance + parameters.getExtensionRadius());
		}
		if (stopFacilities instanceof List) {
			return (List<TransitStopFacility>) stopFacilities;
		}
		return new ArrayList<>(stopFacilities);
	}

    static class ChangedLinkFacility implements Facility, Identifiable<TransitStopFacility> {

        private final TransitStopFacility delegate;
        private final Id<Link> linkId;

        ChangedLinkFacility(final TransitStopFacility delegate, final Id<Link> linkId) {
            this.delegate = delegate;
            this.linkId = linkId;
        }

        @Override
		public Id<Link> getLinkId() {
			return this.linkId;
		}

		@Override
		public Coord getCoord() {
			return this.delegate.getCoord();
		}

		@Override
		public Map<String, Object> getCustomAttributes() {
			return this.delegate.getCustomAttributes();
		}

		@Override
		public Id<TransitStopFacility> getId() {
			return this.delegate.getId();
		}
	}

}
