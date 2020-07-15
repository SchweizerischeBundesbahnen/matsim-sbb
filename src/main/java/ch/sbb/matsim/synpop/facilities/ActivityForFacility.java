package ch.sbb.matsim.synpop.facilities;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.synpop.reader.Synpop2MATSim;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityOption;

public class ActivityForFacility {

	private static final Logger log = Logger.getLogger(ActivityForFacility.class);
	private Map<String, HashSet<String>> bus2acts;
	private List<String> activities;
	private ActivityFacilitiesFactory factory;

	public ActivityForFacility(String csvPath, ActivityFacilitiesFactory factory) {
		this.bus2acts = new HashMap<>();
		this.activities = new ArrayList<>();
		this.factory = factory;
		this.read(csvPath);

	}

	private void read(String csvPath) {
		try (CSVReader reader = new CSVReader(csvPath, ";")) {
			log.info(csvPath);

			//removing business_id from columns and read activities
			Set<String> columns = new HashSet<String>(Arrays.asList(reader.getColumns()));
			columns.remove(Synpop2MATSim.BUSINESS_ID);
			this.activities = new ArrayList<String>(columns);
			log.info(this.activities);

			Map<String, String> map;
			while ((map = reader.readLine()) != null) {

				HashSet<String> acts = new HashSet<>();
				for (String act : activities) {
					if (map.get(act).equals("1") || map.get(act).equals("True")) {
						acts.add(act);
					}
				}

				this.bus2acts.put(map.get(Synpop2MATSim.BUSINESS_ID), acts);
			}
		} catch (IOException e) {
			log.warn(e);
		}
	}

	//ths function remove the original ActivityType and the types specified in the CSV file
	public void run(Collection<ActivityFacility> activityFacilities) {
		for (ActivityFacility activityFacility : activityFacilities) {
			activityFacility.getActivityOptions().clear();
			for (String act : this.activities) {
				ActivityOption option = this.factory.createActivityOption(act);
				activityFacility.addActivityOption(option);
			}
		}
	}
}
