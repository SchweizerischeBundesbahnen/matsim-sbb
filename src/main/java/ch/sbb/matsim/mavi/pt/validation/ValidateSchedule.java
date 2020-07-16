package ch.sbb.matsim.mavi.pt.validation;

import org.locationtech.jts.util.Assert;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class ValidateSchedule {

	public static void main(String[] args) {
		Scenario scenario_ref = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario_ref).readFile("\\\\k13536\\mobi\\sim\\input\\transit\\2016\\reference\\v1\\transitSchedule.xml.gz");
		TransitSchedule schedule_ref = scenario_ref.getTransitSchedule();
		Scenario scenario_var = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario_var).readFile("C:\\Users\\u225744\\Desktop\\output\\transitSchedule.xml.gz");
		TransitSchedule schedule_var = scenario_var.getTransitSchedule();

		Assert.isTrue(schedule_ref.getFacilities().size() == schedule_var.getFacilities().size(), "Number of stops is not equal");
		Assert.isTrue(schedule_ref.getTransitLines().size() == schedule_var.getTransitLines().size(), "Number of lines is not equal");

		int mttRef = 0;
		MinimalTransferTimes.MinimalTransferTimesIterator iterator = schedule_ref.getMinimalTransferTimes().iterator();
		while (iterator.hasNext()) {
			iterator.next();
			double sec = schedule_var.getMinimalTransferTimes().get(iterator.getFromStopId(), iterator.getToStopId());
			if (sec != iterator.getSeconds()) {
				System.out.println("Number of mtt is not equal " + sec + " " + iterator.getSeconds() + " " + iterator.getFromStopId() + " " + iterator.getToStopId());
			}
			mttRef += 1;
		}

		int mttVar = 0;
		MinimalTransferTimes.MinimalTransferTimesIterator iteratorvar = schedule_var.getMinimalTransferTimes().iterator();
		while (iteratorvar.hasNext()) {
			iteratorvar.next();
			mttVar += 1;
		}

		Assert.isTrue(mttRef == mttVar, "Number of mtt is not equal" + mttRef + " " + mttVar);

		int routesRef = 0;
		for (TransitLine line : schedule_ref.getTransitLines().values()) {
			routesRef += line.getRoutes().size();
			for (TransitRoute route : line.getRoutes().values()) {
				Assert.isTrue(schedule_var.getTransitLines().get(line.getId()).getRoutes().get(route.getId()) != null, line.getId().toString() + " " + route.getId().toString());
				Assert.isTrue(route.getStops().size() == schedule_var.getTransitLines().get(line.getId()).getRoutes().get(route.getId()).getStops().size(),
						line.getId().toString() + " " + route.getId().toString());
				Assert.isTrue(route.getDepartures().size() == schedule_var.getTransitLines().get(line.getId()).getRoutes().get(route.getId()).getDepartures().size(),
						line.getId().toString() + " " + route.getId().toString());
				Assert.isTrue(route.getTransportMode().equals(schedule_var.getTransitLines().get(line.getId()).getRoutes().get(route.getId()).getTransportMode()),
						line.getId().toString() + " " + route.getId().toString());
				Assert.isTrue(route.getRoute().getLinkIds().size() == schedule_var.getTransitLines().get(line.getId()).getRoutes().get(route.getId()).getRoute().getLinkIds().size(),
						line.getId().toString() + " " + route.getId().toString());
			}
		}
		int routesVar = 0;
		for (TransitLine line : schedule_var.getTransitLines().values()) {
			routesVar += line.getRoutes().size();
		}
		Assert.isTrue(routesRef == routesVar, "Number of routes is not equal");

	}
}

