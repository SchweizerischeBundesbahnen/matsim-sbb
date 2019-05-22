package ch.sbb.matsim.config;

import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Map;

/**
 * @author mrieser
 */
public class ParkingCostConfigGroup extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "parkingCosts";

    public static final String PARAM_ZONES_ID = "zonesId";
    public static final String PARAM_ZONES_ATTRIBUTE = "zonesParkingCostAttributeName";
    public static final String PARAM_ZONES_RIDE_ATTRIBUTE = "zonesRideParkingCostAttributeName";

    private Id<Zones> zonesId = null;
    private String zonesParkingCostAttributeName = null;
    private String zonesRideParkingCostAttributeName = null;

    public ParkingCostConfigGroup() {
        super(GROUP_NAME);
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> map = super.getComments();
        map.put(PARAM_ZONES_ID, "The id of the zones collection as listed in the zones-configuration.");
        map.put(PARAM_ZONES_ATTRIBUTE, "The zones' attribute name containing the hourly parking cost for each zone for car mode.");
        map.put(PARAM_ZONES_RIDE_ATTRIBUTE, "The zones' attribute name containing the hourly parking cost for each zone for ride mode.");
        return map;
    }

    @StringGetter(PARAM_ZONES_ID)
    public String getZonesId_asString() {
        return this.zonesId == null ? null : this.zonesId.toString();
    }

    @StringSetter(PARAM_ZONES_ID)
    public void setZonesId(String zonesId) {
        this.zonesId = zonesId == null ? null : Id.create(zonesId, Zones.class);
    }

    public Id<Zones> getZonesId() {
        return this.zonesId;
    }

    public void setZonesId(Id<Zones> zonesId) {
        this.zonesId = zonesId;
    }

    @StringGetter(PARAM_ZONES_ATTRIBUTE)
    public String getZonesParkingCostAttributeName() {
        return this.zonesParkingCostAttributeName;
    }

    @StringSetter(PARAM_ZONES_ATTRIBUTE)
    public void setZonesParkingCostAttributeName(String attributeName) {
        this.zonesParkingCostAttributeName = attributeName;
    }

    @StringGetter(PARAM_ZONES_RIDE_ATTRIBUTE)
    public String getZonesRideParkingCostAttributeName() {
        return this.zonesRideParkingCostAttributeName;
    }

    @StringSetter(PARAM_ZONES_RIDE_ATTRIBUTE)
    public void setZonesRideParkingCostAttributeName(String attributeName) {
        this.zonesRideParkingCostAttributeName = attributeName;
    }
}
