package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.matsim.core.utils.io.UncheckedIOException;

/**
 * A class to prepare a sim folder for a MATSim routechoice run
 * only with pt plans, and only Routechoice and Time mutation as strategies
 */
public class WriteRouteChoiceTrips {
    private final String pathInputRCTrips;
    private final String pathInputTrips;
    private final String pathOutputTrips;
    private final List<Integer> newStations;
    private final HashMap<Integer, Integer> stopToBez;
    private final HashSet<String> trips;

    /**
     * @param pathNewStations
     * @param pathInputRCTrips
     * @param pathInputTrips
     * @param pathStopToBez
     * @param pathOutputTrips
     */
    public WriteRouteChoiceTrips(String pathNewStations, String pathInputRCTrips, String pathInputTrips, String pathOutputTrips, String pathStopToBez) {
        this.pathInputRCTrips = pathInputRCTrips;
        this.pathInputTrips = pathInputTrips;
        this.pathOutputTrips = pathOutputTrips;
        this.trips = new HashSet<>();
        this.stopToBez = new HashMap<>();
        this.newStations = new ArrayList<>();

        try (CSVReader csv = new CSVReader(pathNewStations, ";")) {
            csv.readLine();
            Map<String, String> data; // header
            while ((data = csv.readLine()) != null) {
                this.newStations.add(Integer.parseInt(data.get("new_station_bez")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try (CSVReader csv = new CSVReader(pathStopToBez, ";")) {
            csv.readLine();
            Map<String, String> data; // header
            while ((data = csv.readLine()) != null) {
                this.stopToBez.put(Integer.parseInt(data.get("StopId")), Integer.parseInt(data.get("bezno")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        String pathInputRCTrips = args[0];
        String pathInputTrips = args[1];
        String pathOutputTrips = args[2];
        String pathNewStations = args[3];
        String pathStopToBez = args[4];
        new WriteRouteChoiceTrips(pathNewStations, pathInputRCTrips, pathInputTrips, pathOutputTrips, pathStopToBez).run();
    }

    public void readRCTrips() {
        try (CSVReader csv = new CSVReader(pathInputRCTrips, ";")) {
            csv.readLine();
            Map<String, String> data; // header
            while ((data = csv.readLine()) != null) {
                Integer firstRailStop = data.get("first_rail_stop").equals("") ? -1 : Integer.parseInt(data.get("first_rail_stop"));
                Integer lastRailStop = data.get("last_rail_stop").equals("") ? -1 : Integer.parseInt(data.get("last_rail_stop"));

                if (firstRailStop >= 0) {
                    Boolean first = this.stopToBez.containsKey(firstRailStop) && this.newStations.contains(this.stopToBez.get(firstRailStop));
                    Boolean last = this.stopToBez.containsKey(lastRailStop) && this.newStations.contains(this.stopToBez.get(lastRailStop));
                    if (first | last) {
                        this.trips.add(data.get("trip_id"));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeTrips() {
        try (CSVReader reader = new CSVReader(this.pathInputTrips, ";");
                CSVWriter writer = new CSVWriter("", reader.getColumns(), this.pathOutputTrips)){
            reader.readLine();
            Map<String, String> data;
            // write header
            for (String col: reader.getColumns()) {
                writer.set(col, col);
            }
            writer.writeRow();
            while ((data = reader.readLine()) != null) {
                if (this.trips.contains(data.get("trip_id"))) {
                    for (String col: reader.getColumns()) {
                        writer.set(col, data.get(col));
                    }
                    writer.writeRow();
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private void run() {
        readRCTrips();
        writeTrips();
    }


}

