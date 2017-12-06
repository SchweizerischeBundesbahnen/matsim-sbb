package ch.sbb.matsim.utils;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author jlie
 *         <p>
 *         Converts the person events of the event-file into a csv-file
 *         Based on org.matsim.contrib.travelsummary.events2traveldiaries.EventToTravelDiaries.java
 *         Not properly cleaned!
 *         </p>
 */

public class EventsToEventsPerPersonTable implements
        PersonEntersVehicleEventHandler,
        PersonLeavesVehicleEventHandler,
        PersonDepartureEventHandler,
        PersonArrivalEventHandler,
        ActivityStartEventHandler,
        ActivityEndEventHandler,
        PersonStuckEventHandler,
        TeleportationArrivalEventHandler,
        TransitDriverStartsEventHandler {

    private static final Logger log = Logger.getLogger(EventsToEventsPerPersonTable.class);

    private final static String SEPARATOR = ";";

    private final Network network;
    private TransitSchedule transitSchedule;
    private boolean isTransitScenario = false;

    private Map<Id, List<PersonEvent>> eventsPerPerson = new HashMap<>();
    private HashSet<Id> transitDriverIds = new HashSet<>();
    private int stuck = 0;
    private String personIdString = null;

    public EventsToEventsPerPersonTable(Scenario scenario) {
        this.network = scenario.getNetwork();
        isTransitScenario = scenario.getConfig().transit().isUseTransit();
        if (isTransitScenario) {
            this.transitSchedule = scenario.getTransitSchedule();
        }
    }

    public EventsToEventsPerPersonTable(Scenario scenario, String personIdString) {
        this(scenario);
        this.personIdString = personIdString;
    }

    private boolean checkIfPersonMatches(Id<Person> personId) {
        if (personIdString != null) {
            if (personId.toString().equals(personIdString)) return true;
            else return false;
        }
        else return true;
    }

    private boolean isTransitDriver(Id<Person> personId) {
        return isTransitScenario && transitDriverIds.contains(personId);
    }


    @Override
    public void handleEvent(ActivityEndEvent event) {
        try {
            if (!checkIfPersonMatches(event.getPersonId())) return;
            if (isTransitDriver(event.getPersonId())) return;
            addNewEventToEventsPerPerson(event.getPersonId(), new PersonEvent(
                    event.getPersonId(),
                    event.getTime(),
                    event.getEventType(),
                    event.getActType(),
                    event.getLinkId().toString(),
                    "",
                    "",
                    ""));
        } catch (Exception e) {
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        try {
            if (!checkIfPersonMatches(event.getPersonId())) return;
            if (isTransitDriver(event.getPersonId())) return;
            addNewEventToEventsPerPerson(event.getPersonId(), new PersonEvent(
                    event.getPersonId(),
                    event.getTime(),
                    event.getEventType(),
                    event.getActType(),
                    event.getLinkId().toString(),
                    "",
                    "",
                    ""));
        } catch (Exception e) {
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
        try {
            if (!checkIfPersonMatches(event.getPersonId())) return;
            if (isTransitDriver(event.getPersonId())) return;
            addNewEventToEventsPerPerson(event.getPersonId(), new PersonEvent(
                    event.getPersonId(),
                    event.getTime(),
                    event.getEventType(),
                    "",
                    event.getLinkId().toString(),
                    event.getLegMode(),
                    "",
                    ""));
        } catch (Exception e) {
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        try {
            if (!checkIfPersonMatches(event.getPersonId())) return;
            if (isTransitDriver(event.getPersonId())) return;
            addNewEventToEventsPerPerson(event.getPersonId(), new PersonEvent(
                    event.getPersonId(),
                    event.getTime(),
                    event.getEventType(),
                    "",
                    event.getLinkId().toString(),
                    event.getLegMode(),
                    "",
                    ""));

        } catch (Exception e) {
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonStuckEvent event) {
        try {
            if (!checkIfPersonMatches(event.getPersonId())) return;
            if (isTransitDriver(event.getPersonId())) return;
            addNewEventToEventsPerPerson(event.getPersonId(), new PersonEvent(
                    event.getPersonId(),
                    event.getTime(),
                    event.getEventType(),
                    "",
                    event.getLinkId().toString(),
                    event.getLegMode(),
                    "",
                    ""));
        } catch (Exception e) {
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        try {
            if (!checkIfPersonMatches(event.getPersonId())) return;
            if (isTransitDriver(event.getPersonId())) return;
            addNewEventToEventsPerPerson(event.getPersonId(), new PersonEvent(
                    event.getPersonId(),
                    event.getTime(),
                    event.getEventType(),
                    "",
                    "",
                    "",
                    event.getVehicleId().toString(),
                    ""));
        } catch (Exception e) {
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        try {
            if (!checkIfPersonMatches(event.getPersonId())) return;
            if (isTransitDriver(event.getPersonId())) return;
            addNewEventToEventsPerPerson(event.getPersonId(), new PersonEvent(
                    event.getPersonId(),
                    event.getTime(),
                    event.getEventType(),
                    "",
                    "",
                    "",
                    event.getVehicleId().toString(),
                    ""));

        } catch (Exception e) {
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(TeleportationArrivalEvent event) {
        try {
            if (!checkIfPersonMatches(event.getPersonId())) return;
            if (isTransitDriver(event.getPersonId())) return;
            addNewEventToEventsPerPerson(event.getPersonId(), new PersonEvent(
                    event.getPersonId(),
                    event.getTime(),
                    event.getEventType(),
                    "",
                    "",
                    "",
                    "",
                    String.valueOf(event.getDistance())));
        } catch (Exception e) {
            e.printStackTrace(System.out);
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        try {
            transitDriverIds.add(event.getDriverId());
        } catch (Exception e) {
            log.error("Exception while working on event: " + event.toString(), e);
        }
    }

    // Methods
    @Override
    public void reset(int iteration) {
        eventsPerPerson = new HashMap<>();
    }

    public void writeSimulationResultsToTabSeparated(String path, String appendage) throws IOException {
        String eventPerPersonTableName;
        eventPerPersonTableName = "events_per_person" + appendage + ".csv";

        BufferedWriter eventsPerPersonWriter = IOUtils.getBufferedWriter(path + "/" + eventPerPersonTableName);
        eventsPerPersonWriter.write(String.join(SEPARATOR, getPersonEventHeaderAttributList()) + "\n");
        for (Entry<Id, List<PersonEvent>> entry: eventsPerPerson.entrySet()) {
            for (PersonEvent personEvent: entry.getValue()) {
                eventsPerPersonWriter.write(personEvent.getSepString());
            }
        }

        eventsPerPersonWriter.close();
    }

    public int getStuck() {
        return stuck;
    }

    void setStuck(int stuck) {
        this.stuck = stuck;
    }


    private void addNewEventToEventsPerPerson(Id id, PersonEvent personEvent) {
        List<PersonEvent> eventsPerPersonList = eventsPerPerson.get(id);
        if (eventsPerPersonList == null) {
            List<PersonEvent> newList = new ArrayList<>();
            newList.add(personEvent);
            eventsPerPerson.put(id, newList);
            return;
        }
        eventsPerPersonList.add(personEvent);
    }

    private class PersonEvent {
        private final List<String> attributList = new ArrayList<>();

        PersonEvent(Id id, Double time, String type, String actType, String link, String legMode, String vehicle, String distance) {
            attributList.add(id.toString());
            attributList.add(String.valueOf(time));
            attributList.add(millisToHHMMSS(time));
            attributList.add(type);
            attributList.add(actType);
            attributList.add(link);
            attributList.add(legMode);
            attributList.add(vehicle);
            attributList.add(distance);
        }

        public String getSepString() {
            return String.join(SEPARATOR, attributList) + "\n";
        }

        public List<String> getAttributList() {
            return attributList;
        }

    }

    private static List<String> getPersonEventHeaderAttributList() {
        List<String> out = new ArrayList<>();
        out.add("person");
        out.add("time");
        out.add("timeHHMMSS");
        out.add("type");
        out.add("actType");
        out.add("link");
        out.add("legMode");
        out.add("vehicle");
        out.add("distance");
        return out;
    }

    private String millisToHHMMSS(double secondsDbl) {
        int seconds = (int) secondsDbl;
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
