package ch.sbb.matsim.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;


public class SBBAccessTimeConfigGroup extends ReflectiveConfigGroup {
    static public final String GROUP_NAME = "SBBAccessTime";

    static private final String PARAM_MODES_WITH_ACCESS_TIME = "modesWithAccessTime";
    static private final String PARAM_SHAPEFILE = "shapefile";
    static private final String PARAM_IS_INSERTING = "isInsertingAccessEgressWalk";

    private String shapefile = "";
    private Boolean isInsertingAccessEgressWalk = false;
    private Set<String> modesWithAccessTime = new HashSet<>();

    public SBBAccessTimeConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter(PARAM_SHAPEFILE)
    public String getShapefile() {
        return shapefile;
    }

    @StringSetter(PARAM_SHAPEFILE)
    public void setShapefile(String shapefile) {
        this.shapefile = shapefile;
    }

    @StringGetter(PARAM_IS_INSERTING)
    public Boolean getInsertingAccessEgressWalk() {
        return isInsertingAccessEgressWalk;
    }

    @StringSetter(PARAM_IS_INSERTING)
    public void setInsertingAccessEgressWalk(Boolean insertingAccessEgressWalk) {
        isInsertingAccessEgressWalk = insertingAccessEgressWalk;
    }

    @StringGetter(PARAM_MODES_WITH_ACCESS_TIME)
    private String getModesWithAccessTimeAsString() {
        return CollectionUtils.setToString(this.modesWithAccessTime);
    }

    public Set<String> getModesWithAccessTime() {
        return this.modesWithAccessTime;
    }

    @StringSetter(PARAM_MODES_WITH_ACCESS_TIME)
    public void setModesWithAccessTime(String modes) {
        setModes(CollectionUtils.stringToSet(modes));
    }

    public void setModes(Set<String> modes) {
        this.modesWithAccessTime.clear();
        this.modesWithAccessTime.addAll(modes);
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_MODES_WITH_ACCESS_TIME, "Specifies for which modes access and egress legs are added.");
        comments.put(PARAM_IS_INSERTING, "If true, will add access and egress legs for the specified modes. If false, will use the standard RoutingModules.");
        comments.put(PARAM_SHAPEFILE, "Path of the shapefile used to map activities to zones. Zones of the shapefile contains following attributes 'ACC'+mode.");
        return comments;
    }
}
