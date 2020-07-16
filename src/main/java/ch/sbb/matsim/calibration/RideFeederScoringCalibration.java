package ch.sbb.matsim.calibration;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.preparation.XLSXScoringParser;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunction;

public class RideFeederScoringCalibration {

	private String configPath;
	private Scenario scenario;
	public RideFeederScoringCalibration(String configPath, String xlsx) throws IOException {
		this.configPath = configPath;

		Config config = RunSBB.buildConfig(configPath);
		config.plans().setInputFile(null);
		config.transit().setTransitScheduleFile(null);
		config.facilities().setInputFile(null);
		config.network().setInputFile(null);
		config.vehicles().setVehiclesFile(null);

		try (FileInputStream inputStream = new FileInputStream(xlsx)) {
			Workbook workbook = WorkbookFactory.create(inputStream);

			XLSXScoringParser.parseXLSXWorkbook(workbook, config);
		} catch (IOException e) {
			e.printStackTrace();
		}

		this.scenario = ScenarioUtils.loadScenario(config);

	}

	public static void main(String[] args) throws IOException {
		System.setProperty("matsim.preferLocalDtds", "true");

		String configPath = "\\\\k13536\\mobi\\40_Projekte\\20190701_NMD_PotentialAnalyse_quicksim\\intermodal_scoring_calibration\\config_with_mapped_stations_and_para.xml";
		String xlsx = "\\\\k13536\\mobi\\40_Projekte\\20190701_NMD_PotentialAnalyse_quicksim\\inputs\\scoring\\2.0.0_ridefeeder_v3.xlsx";

		RideFeederScoringCalibration rideFeederScoringCalibration = new RideFeederScoringCalibration(configPath, xlsx);
		rideFeederScoringCalibration.read();
	}

	private Activity createActivity(PopulationFactory pf, String type, double x, double y, double startTime, double endTime) {
		Activity act = pf.createActivityFromCoord(type, new Coord(x, y));
		act.setStartTime(startTime);
		act.setEndTime(endTime);
		return act;
	}

	private Leg createLeg(PopulationFactory pf, String mode, double departureTime, double arrivalTime, double distance) {
		Leg leg = pf.createLeg(mode);
		leg.setDepartureTime(departureTime);
		leg.setTravelTime(arrivalTime - departureTime);
		LinkNetworkRouteFactory factory = new LinkNetworkRouteFactory();
		NetworkRoute route = (NetworkRoute) factory.createRoute(Id.createLinkId("1"), Id.createLinkId(2));
		route.setDistance(distance);
		route.setTravelTime(arrivalTime - departureTime);
		//route.setLinkIds(link1.getId(), Arrays.asList(link2.getId()), link3.getId());
		leg.setRoute(route);
		return leg;
	}

	private void read() throws IOException {
		String csv_path = "\\\\k13536\\mobi\\40_Projekte\\20190701_NMD_PotentialAnalyse_quicksim\\intermodal_scoring_calibration\\plans_v7.csv";
		String output_csv_path = "\\\\k13536\\mobi\\40_Projekte\\20190701_NMD_PotentialAnalyse_quicksim\\intermodal_scoring_calibration\\plans_v7_out.csv";

		try (CSVWriter csvWriter = new CSVWriter("", new String[]{"plan_id", "mode", "end_time", "score", "distance"}, output_csv_path)) {
			try (CSVReader visumVolume = new CSVReader(csv_path, ";")) {

				Map<String, String> row;

				boolean first = true;
				int lastPlanId = 0;
				boolean isAct = true;

				PopulationFactory pf = scenario.getPopulation().getFactory();
				Person person1 = null;
				Plan plan = null;

				while ((row = visumVolume.readLine()) != null) {
					int planId = Integer.valueOf(row.get("plan_id"));

					if (lastPlanId != planId) {
						first = true;
					}

					if (first) {
						plan = pf.createPlan();
						first = false;
						isAct = true;
					}

					if (isAct) {
						isAct = false;

						String start = row.get("start_time");
						String end = row.get("end_time");

						double start_time = 0;
						double end_time = 24 * 60 * 60;

						if (!start.equals("-")) {
							start_time = Integer.valueOf(start);
						}

						if (!end.equals("-")) {
							end_time = Integer.valueOf(end);
						}

						plan.addActivity(createActivity(pf, row.get("mode"), 0, 0, start_time, end_time));
						if (row.get("end_time").equals("-")) {

							person1 = pf.createPerson(Id.create(row.get("plan_id"), Person.class));
							person1.getAttributes().putAttribute("subpopulation", "regular");
							person1.getAttributes().putAttribute("carAvail", "never");
							person1.getAttributes().putAttribute("pt_subscr", "GA");
							person1.getAttributes().putAttribute("empl_pct_cat", 1);
							person1.getAttributes().putAttribute("edu_type", 1);
							person1.getAttributes().putAttribute("ms_region", 99);
							scenario.getPopulation().addPerson(person1);

							isAct = true;

							person1.addPlan(plan);
							test(person1, csvWriter);
						}

					} else {
						plan.addLeg(createLeg(pf, row.get("mode"), Integer.valueOf(row.get("start_time")), Integer.valueOf(row.get("end_time")), Integer.valueOf(row.get("distance"))));
						isAct = true;
					}

					lastPlanId = planId;

				}
			}
		}
	}

	private void test(Person person, CSVWriter csvWriter) {

		Plan plan = person.getSelectedPlan();

		List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(plan);

		SBBScoringFunctionFactory factory = new SBBScoringFunctionFactory(scenario);
		ScoringFunction sf = factory.createNewScoringFunction(person);
		String mode = "";
		double distance = 0;
		double end_time = 0;
		for (PlanElement pe : plan.getPlanElements()) {
			if (pe instanceof Leg) {
				sf.handleLeg((Leg) pe);
				Leg leg = (Leg) pe;
				mode = leg.getMode();
				distance = leg.getRoute().getDistance();
				end_time = leg.getDepartureTime().seconds() + leg.getTravelTime().seconds();
			} else {

				sf.handleActivity((Activity) pe);
				mode = ((Activity) pe).getType();
				distance = 0;
				end_time = ((Activity) pe).getEndTime().seconds();
			}
			System.out.println(sf.getScore() + " " + pe.toString());

			csvWriter.set("plan_id", person.getId().toString());
			csvWriter.set("mode", mode);
			csvWriter.set("end_time", String.valueOf(end_time));
			csvWriter.set("score", String.valueOf(sf.getScore()));
			csvWriter.set("distance", String.valueOf(distance));
			csvWriter.writeRow();
		}

		TripStructureUtils.Trip trip = trips.get(0);
		sf.handleTrip(trip);
		sf.finish();

		double score = sf.getScore();
		System.out.println(score);
		System.out.println("-----");

	}

}
