/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.variables.SBBModes;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.pt.PtConstants;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * @author mrieser / SBB
 */
public class AccessEgressDistanceAnalysis {

	private static final Logger log = Logger.getLogger(AccessEgressDistanceAnalysis.class);

	private final Map<Id<TransitStopFacility>, List<LegData>> legData = new TreeMap<>();
	private final Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
	private final TransitSchedule schedule = this.scenario.getTransitSchedule();

	public static void main(String[] args) {
		System.setProperty("matsim.preferLocalDtds", "true");

		String scheduleFilename = "D:/devsbb/mrieser/data/mtt_test/0.9.14/CH.10pct.2015.output_transitScheduleFIXED.xml";
		String plansFilename = "D:/devsbb/mrieser/data/mtt_test/0.9.15/CH.10pct.2015.output_plans.xml.gz";
		String xyFilename = "D:/devsbb/mrieser/data/mtt_test/0.9.15/accessEgress.txt";
		String analysisFilename = "D:/devsbb/mrieser/data/mtt_test/0.9.15/aggregatedAccessEgressDistances.txt";

		new AccessEgressDistanceAnalysis().run(plansFilename, scheduleFilename, xyFilename, analysisFilename);
	}

	public void run(String plansFilename, String transitScheduleFilename, String xyFilename, String analysisFilename) {
		new TransitScheduleReader(this.scenario).readFile(transitScheduleFilename);

		collectData(plansFilename, xyFilename);
		analyzeData(analysisFilename);
	}

	private void collectData(String plansFilename, String xyFilename) {
		StreamingPopulationReader reader = new StreamingPopulationReader(this.scenario);
		try (BufferedWriter writer = IOUtils.getBufferedWriter(xyFilename)) {
			writer.write("PERSON_ID\tTYPE\tFROM_X\tFROM_Y\tTO_X\tTO_Y\tSTOP_ID\tSTOP_NAME\tDISTANCE\n");
			reader.addAlgorithm(p -> this.analyzePerson(p, writer));
			reader.readFile(plansFilename);
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
	}

	private void analyzePerson(Person person, Writer out) {
		Plan plan = person.getSelectedPlan();
		Activity prevAct = null;
		DefaultTransitPassengerRoute prevPtRoute = null;
		try {
			for (PlanElement pe : plan.getPlanElements()) {
				if (pe instanceof Activity) {
					Activity act = (Activity) pe;
					if (!PtConstants.TRANSIT_ACTIVITY_TYPE.equals(act.getType())) {
						if (prevPtRoute != null) {
							// egress leg
							Id<TransitStopFacility> fromStopId = prevPtRoute.getEgressStopId();
							TransitStopFacility fromStop = this.schedule.getFacilities().get(fromStopId);
							double distance = CoordUtils.calcEuclideanDistance(fromStop.getCoord(), act.getCoord());
							this.legData.computeIfAbsent(fromStopId, k -> new ArrayList<>()).add(
									new LegData(person.getId(), LegDataType.EGRESS, act.getCoord(), fromStop, distance));
							out.write(person.getId() + "\t" + LegDataType.EGRESS + "\t" + fromStop.getCoord().getX() + "\t" + fromStop.getCoord().getY() + "\t" +
									act.getCoord().getX() + "\t" + act.getCoord().getY() + "\t" + fromStopId + "\t" + fromStop.getName() + "\t" + distance + "\n");
							prevPtRoute = null;
						}
						prevAct = act;
					}
				}
				if (pe instanceof Leg) {
					Leg leg = (Leg) pe;
					if (SBBModes.PT.equals(leg.getMode())) {
						DefaultTransitPassengerRoute ptRoute = (DefaultTransitPassengerRoute) leg.getRoute();

						if (prevAct != null) {
							// access leg
							Id<TransitStopFacility> toStopId = ptRoute.getAccessStopId();
							TransitStopFacility toStop = this.schedule.getFacilities().get(toStopId);
							double distance = CoordUtils.calcEuclideanDistance(prevAct.getCoord(), toStop.getCoord());
							this.legData.computeIfAbsent(toStopId, k -> new ArrayList<>()).add(
									new LegData(person.getId(), LegDataType.ACCESS, prevAct.getCoord(), toStop, distance));
							out.write(person.getId() + "\t" + LegDataType.ACCESS + "\t" + prevAct.getCoord().getX() + "\t" + prevAct.getCoord().getY() + "\t" +
									toStop.getCoord().getX() + "\t" + toStop.getCoord().getY() + "\t" + toStopId + "\t" + toStop.getName() + "\t" + distance + "\n");

							prevAct = null;
						}

						prevPtRoute = ptRoute;
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void analyzeData(String analysisFilename) {
		log.info("ANALYSIS");
		try (BufferedWriter out = IOUtils.getBufferedWriter(analysisFilename)) {
			out.write("STOP_ID\tSTOP_NAME\tSTOP_X\tSTOP_Y\tTYPE\tMIN_DISTANCE\tAVG_DISTANCE\tMED_DISTANCE\tMAX_DISTANCE\tCOUNT\n");

			for (Map.Entry<Id<TransitStopFacility>, List<LegData>> e1 : this.legData.entrySet()) {
				Id<TransitStopFacility> stopId = e1.getKey();
				TransitStopFacility stop = this.schedule.getFacilities().get(stopId);
				List<Double> accessDistances = new ArrayList<>();
				List<Double> egressDistances = new ArrayList<>();
				for (LegData ld : e1.getValue()) {
					if (ld.type == LegDataType.ACCESS) {
						accessDistances.add(ld.distance);
					}
					if (ld.type == LegDataType.EGRESS) {
						egressDistances.add(ld.distance);
					}
				}
				// access
				if (!accessDistances.isEmpty()) {
					accessDistances.sort(Double::compare);
					double minDist = accessDistances.get(0);
					double maxDist = accessDistances.get(accessDistances.size() - 1);
					double medDist = accessDistances.get(accessDistances.size() / 2);
					double avgDist = calcAverage(accessDistances);
					out.write(stopId + "\t" + stop.getName() + "\t" + stop.getCoord().getX() + "\t" + stop.getCoord().getY() + "\t" + LegDataType.ACCESS + "\t" + minDist + "\t" + avgDist + "\t"
							+ medDist + "\t" + maxDist + "\t" + accessDistances.size() + "\n");
				}

				// egress
				if (!egressDistances.isEmpty()) {
					egressDistances.sort(Double::compare);
					double minDist = egressDistances.get(0);
					double maxDist = egressDistances.get(egressDistances.size() - 1);
					double medDist = egressDistances.get(egressDistances.size() / 2);
					double avgDist = calcAverage(egressDistances);
					out.write(stopId + "\t" + stop.getName() + "\t" + stop.getCoord().getX() + "\t" + stop.getCoord().getY() + "\t" + LegDataType.EGRESS + "\t" + minDist + "\t" + avgDist + "\t"
							+ medDist + "\t" + maxDist + "\t" + accessDistances.size() + "\n");
				}
			}
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
	}

	private double calcAverage(Collection<Double> values) {
		double sum = 0;
		int count = 0;
		for (Double d : values) {
			sum += d;
			count++;
		}
		if (count > 0) {
			return sum / count;
		}
		return Double.NaN;
	}

	private enum LegDataType {ACCESS, EGRESS}

	private static class LegData {

		final Id<Person> personId;
		final LegDataType type;
		final Coord coord;
		final TransitStopFacility stop;
		final double distance;

		LegData(Id<Person> personId, LegDataType type, Coord coord, TransitStopFacility stop, double distance) {
			this.personId = personId;
			this.type = type;
			this.coord = coord;
			this.stop = stop;
			this.distance = distance;
		}
	}
}
