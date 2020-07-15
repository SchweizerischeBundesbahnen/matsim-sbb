package ch.sbb.matsim.synpop.reader;

import ch.sbb.matsim.csv.CSVReader;
import java.io.IOException;
import java.util.Map;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Population;
import org.matsim.facilities.ActivityFacilities;

public class SynpopCSVReaderImpl implements SynpopReader {

	private static final Logger log = Logger.getLogger(SynpopCSVReaderImpl.class);
	final private Synpop2MATSim synpop2MATSim = new Synpop2MATSim();
	final private String folder;
	private int n = Integer.MAX_VALUE;

	public SynpopCSVReaderImpl(String folder, int n) {
		this.folder = folder;
		this.n = n;
	}

	public SynpopCSVReaderImpl(String folder) {
		this.folder = folder;
	}

	@Override
	public ActivityFacilities getFacilities() {
		return synpop2MATSim.getFacilites();
	}

	@Override
	public Population getPopulation() {
		return synpop2MATSim.getPopulation();
	}

	@Override
	public void load() {
		this.readPersons(folder + "/persons.csv");
		this.readHouseholds(folder + "/households.csv");
		this.readBusinesses(folder + "/businesses.csv");
	}

	private void readPersons(final String personsFile) {
		try (final CSVReader reader = new CSVReader(personsFile, ";")) {
			Map<String, String> map;
			int i = 0;
			while ((map = reader.readLine()) != null && i < n) {
				synpop2MATSim.loadPerson(map);
				i++;
			}
		} catch (final IOException e) {
			log.warn(e);
		}
	}

	private void readBusinesses(final String personsFile) {
		try (CSVReader reader = new CSVReader(personsFile, ";")) {
			Map<String, String> map;
			int i = 0;
			while ((map = reader.readLine()) != null && i < n) {
				synpop2MATSim.loadBusiness(map);
				i++;
			}
		} catch (IOException e) {
			log.warn(e);
		}

	}

	private void readHouseholds(final String personsFile) {
		try (CSVReader reader = new CSVReader(personsFile, ";")) {
			Map<String, String> map;
			int i = 0;
			while ((map = reader.readLine()) != null && i < n) {
				synpop2MATSim.loadHousehold(map);
				i++;
			}
		} catch (IOException e) {
			log.warn(e);
		}

	}

}
