/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package playgrounds.mrieser.pt.transfers;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.PtConstants;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * main idea: collect transfer times per stop-to-stop tuple, calculate minimal, average, maximal transfer time
 * between stops, see if the minimum changes in some relations.
 *
 * @author mrieser / SBB
 */
public class TransferTimeAnalysis {

    private static final Logger log = Logger.getLogger(TransferTimeAnalysis.class);

    private final Map<Id<TransitStopFacility>, Map<Id<TransitStopFacility>, List<Double>>> transferTimes = new TreeMap<>();

    public void run(String eventsFilename, String transitScheduleFilename, String analysisFilename) {
        collectData(eventsFilename);
        analyzeData(transitScheduleFilename, analysisFilename);
    }

    private void collectData(String eventsFilename) {
        Set<Id<Vehicle>> transitVehicles = new HashSet<>();
        Set<Id<Person>> transitDrivers = new HashSet<>();
        Map<Id<Vehicle>, VehicleData> vehiclePositions = new HashMap<>();

        Map<Id<Person>, PassengerExitData> paxData = new HashMap<>();

        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(new TransitDriverStartsEventHandler() {
            @Override
            public void handleEvent(TransitDriverStartsEvent event) {
                transitVehicles.add(event.getVehicleId());
                transitDrivers.add(event.getDriverId());
            }

            @Override
            public void reset(int iteration) {
            }
        });

        events.addHandler(new VehicleArrivesAtFacilityEventHandler() {
            @Override
            public void handleEvent(VehicleArrivesAtFacilityEvent event) {
                if (transitVehicles.contains(event.getVehicleId())) {
                    vehiclePositions.put(event.getVehicleId(), new VehicleData(event.getFacilityId(), event.getTime()));
                }
            }

            @Override
            public void reset(int iteration) {
            }
        });

        events.addHandler(new VehicleDepartsAtFacilityEventHandler() {
            @Override
            public void handleEvent(VehicleDepartsAtFacilityEvent event) {
                VehicleData vehData = vehiclePositions.remove(event.getVehicleId());
                Id<TransitStopFacility> toStopId = vehData.arrivalStopId;
                for (PassengerExitData data : vehData.transferedAgents) {
                    Id<TransitStopFacility> fromStopId = data.exitStopId;
                    Map<Id<TransitStopFacility>, List<Double>> toMap = TransferTimeAnalysis.this.transferTimes.computeIfAbsent(fromStopId, id -> new TreeMap<>());
                    List<Double> list = toMap.computeIfAbsent(toStopId, id -> new ArrayList<>());
                    double transferTime = event.getTime() - data.exitTime;
                    list.add(transferTime);
                }
            }

            @Override
            public void reset(int iteration) {
            }
        });

        events.addHandler(new ActivityStartEventHandler() {
            @Override
            public void handleEvent(ActivityStartEvent event) {
                if (!PtConstants.TRANSIT_ACTIVITY_TYPE.equals(event.getActType())) {
                    paxData.remove(event.getPersonId());
                }
            }

            @Override
            public void reset(int iteration) {
            }
        });

        events.addHandler(new PersonLeavesVehicleEventHandler() {
            @Override
            public void handleEvent(PersonLeavesVehicleEvent event) {
                if (transitVehicles.contains(event.getVehicleId()) && !transitDrivers.contains(event.getPersonId())) {
                    VehicleData vehData = vehiclePositions.get(event.getVehicleId());
                    paxData.put(event.getPersonId(), new PassengerExitData(vehData.arrivalStopId, vehData.arrivalTime));
                }
            }

            @Override
            public void reset(int iteration) {
            }
        });

        events.addHandler(new PersonEntersVehicleEventHandler() {
            @Override
            public void handleEvent(PersonEntersVehicleEvent event) {
                if (transitVehicles.contains(event.getVehicleId())) {
                    PassengerExitData passengerData = paxData.get(event.getPersonId());
                    if (passengerData != null) {
                        VehicleData vehData = vehiclePositions.get(event.getVehicleId());
                        vehData.transferedAgents.add(passengerData);
                    }
                }
            }

            @Override
            public void reset(int iteration) {
            }
        });

        new MatsimEventsReader(events).readFile(eventsFilename);
        events.finishProcessing();
    }

    private void analyzeData(String transitScheduleFilename, String analysisFilename) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFilename);
        log.info("ANALYSIS");
        try (BufferedWriter out = IOUtils.getBufferedWriter(analysisFilename)) {
            out.write("FROM ID\tFROM NAME\tTO ID\tTO NAME\tMIN_TIME\tAVG_TIME\tMED_TIME\tMAX_TIME\tCOUNT\n");
            for (Map.Entry<Id<TransitStopFacility>, Map<Id<TransitStopFacility>, List<Double>>> e1 : this.transferTimes.entrySet()) {
                Id<TransitStopFacility> fromStopFacilityId = e1.getKey();
                TransitStopFacility fromStop = scenario.getTransitSchedule().getFacilities().get(fromStopFacilityId);
                Map<Id<TransitStopFacility>, List<Double>> toMap = e1.getValue();
                for (Map.Entry<Id<TransitStopFacility>, List<Double>> e2 : toMap.entrySet()) {
                    Id<TransitStopFacility> toStopFacilityId = e2.getKey();
                    TransitStopFacility toStop = scenario.getTransitSchedule().getFacilities().get(toStopFacilityId);
                    List<Double> transferTimes = e2.getValue();
                    transferTimes.sort(Double::compare);
                    double minTime = transferTimes.get(0);
                    double maxTime = transferTimes.get(transferTimes.size() - 1);
                    double medTime = transferTimes.get(transferTimes.size() / 2);
                    double avgTime = calcAverage(transferTimes);
                    out.write(fromStopFacilityId + "\t" + fromStop.getName() + "\t" + toStopFacilityId + "\t" + toStop.getName() + "\t" + minTime + "\t" + avgTime + "\t" + medTime + "\t" + maxTime + "\t" + transferTimes.size() + "\n");
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

    private static class PassengerExitData {
        final Id<TransitStopFacility> exitStopId;
        final double exitTime;

        public PassengerExitData(Id<TransitStopFacility> exitStopId, double exitTime) {
            this.exitStopId = exitStopId;
            this.exitTime = exitTime;
        }
    }

    private static class VehicleData {
        final Id<TransitStopFacility> arrivalStopId;
        final double arrivalTime;
        final List<PassengerExitData> transferedAgents = new ArrayList<>();

        public VehicleData(Id<TransitStopFacility> arrivalStopId, double arrivalTime) {
            this.arrivalStopId = arrivalStopId;
            this.arrivalTime = arrivalTime;
        }
    }

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

//        String eventsFilename = "D:/devsbb/mrieser/data/mtt_test/withoutMTT/CNB.10pct.2015.output_events.xml.gz";
        String eventsFilename = "D:/devsbb/mrieser/data/mtt_test/withMTT/CNB.10pct.2015.output_events.xml.gz";
        String scheduleFilename = "D:/devsbb/mrieser/data/mtt_test/withMTT/CNB.10pct.2015.output_transitSchedule.xml.gz";
//        String analysisFilename = "D:/devsbb/mrieser/data/mtt_test/withoutMTT/transferStatsWITHOUT.txt";
        String analysisFilename = "D:/devsbb/mrieser/data/mtt_test/withMTT/transferStatsWITH.txt";

        new TransferTimeAnalysis().run(eventsFilename, scheduleFilename, analysisFilename);
    }
}
