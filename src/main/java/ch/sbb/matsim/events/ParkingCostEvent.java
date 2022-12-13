package ch.sbb.matsim.events;

import java.util.Map;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.HasLinkId;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.events.HasPersonId;
import org.matsim.vehicles.Vehicle;

/**
 * @author mrieser
 */
public class ParkingCostEvent extends Event implements HasPersonId, HasLinkId {

	private static final String EVENT_TYPE = "personParkingCost";
	private static final String ATTRIBUTE_AMOUNT = "monetaryAmount";
	private static final String ATTRIBUTE_PERSON = "person";
	private static final String ATTRIBUTE_VEHICLE = "vehicle";
	private static final String ATTRIBUTE_LINK = "link";

	private final Id<Person> personId;
	private final Id<Vehicle> vehicleId;
	private final Id<Link> linkId;
	private final double monetaryAmount;

	public ParkingCostEvent(double time, Id<Person> personId, Id<Vehicle> vehicleId, Id<Link> linkId, double monetaryAmount) {
		super(time);
		this.personId = personId;
		this.vehicleId = vehicleId;
		this.linkId = linkId;
		this.monetaryAmount = monetaryAmount;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	@Override
	public Id<Person> getPersonId() {
		return this.personId;
	}

	public Id<Vehicle> getVehicleId() {
		return this.vehicleId;
	}

	@Override
	public Id<Link> getLinkId() {
		return this.linkId;
	}

	public double getMonetaryAmount() {
		return this.monetaryAmount;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();
		attr.put(ATTRIBUTE_PERSON, this.personId.toString());
		if (this.vehicleId != null) {
			// vehicleId is null in case of "ride" mode
			attr.put(ATTRIBUTE_VEHICLE, this.vehicleId.toString());
		}
		attr.put(ATTRIBUTE_LINK, this.linkId.toString());
		attr.put(ATTRIBUTE_AMOUNT, Double.toString(this.monetaryAmount));
		return attr;
	}
}
