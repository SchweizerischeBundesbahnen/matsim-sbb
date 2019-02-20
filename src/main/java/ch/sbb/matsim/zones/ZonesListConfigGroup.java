package ch.sbb.matsim.zones;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * ConfigGroup to manage zonal representations of a region.
 *
 * @author mrieser
 */
public class ZonesListConfigGroup extends ReflectiveConfigGroup {

    public final static String GROUPNAME = "zones";

    private final List<ZonesParameterSet> zonesGroups = new ArrayList<>();

    public ZonesListConfigGroup() {
        super(GROUPNAME);
    }

    @Override
    public ConfigGroup createParameterSet(String type) {
        if (ZonesParameterSet.TYPE.equals(type)) {
            return new ZonesParameterSet();
        }
        throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
    }

    @Override
    public void addParameterSet(ConfigGroup set) {
        if (set instanceof ZonesParameterSet) {
            addZones((ZonesParameterSet) set);
        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
        }
    }

    public void addZones(ZonesParameterSet zonesGroup) {
        this.zonesGroups.add(zonesGroup);
    }

    Collection<ZonesParameterSet> getZones() {
        return this.zonesGroups;
    }

    public static class ZonesParameterSet extends ReflectiveConfigGroup {
        static final String TYPE = "zones";

        private static final String PARAM_ID = "id";
        private static final String PARAM_FILENAME = "filename";
        private static final String PARAM_ID_ATTRIBUTE = "idAttributeName";

        private String id = null;
        private String filename = null;
        private String idAttributeName = null;

        public ZonesParameterSet() {
            super(TYPE);
        }

        public ZonesParameterSet(String id, String filename, String idAttributeName) {
            super(TYPE);
            this.id = id;
            this.filename = filename;
            this.idAttributeName = idAttributeName;
        }

        @StringGetter(PARAM_ID)
        public String getId() {
            return this.id;
        }

        @StringSetter(PARAM_ID)
        public void setId(String id) {
            this.id = id;
        }

        @StringGetter(PARAM_FILENAME)
        public String getFilename() {
            return this.filename;
        }

        @StringSetter(PARAM_FILENAME)
        public void setFilename(String filename) {
            this.filename = filename;
        }

        @StringGetter(PARAM_ID_ATTRIBUTE)
        public String getIdAttributeName() {
            return this.idAttributeName;
        }

        @StringSetter(PARAM_ID_ATTRIBUTE)
        public void setIdAttributeName(String idAttributeName) {
            this.idAttributeName = idAttributeName;
        }
    }

}
