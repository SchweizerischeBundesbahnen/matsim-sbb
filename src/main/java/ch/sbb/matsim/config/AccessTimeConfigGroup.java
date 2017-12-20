package ch.sbb.matsim.config;

import java.util.HashSet;
import java.util.Set;

import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;


public class AccessTimeConfigGroup extends ReflectiveConfigGroup {
    static public final String GROUP_NAME = "SBBAccessTime";
    static private final String PARAM_MODES_WITH_ACCESS_TIME = "modesWithAccessTime";
    private String shapefile = "";
    private Boolean isInsertingAccessEgressWalk = false;
    private Set<String> modesWithAccessTime = new HashSet<>();

    public AccessTimeConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter("shapefile")
    public String getShapefile() {
        return shapefile;
    }

    @StringSetter("shapefile")
    public void setShapefile(String shapefile) {
        this.shapefile = shapefile;
    }

    @StringGetter("isInsertingAccessEgressWalk")
    public Boolean getInsertingAccessEgressWalk() {
        return isInsertingAccessEgressWalk;
    }

    @StringSetter("isInsertingAccessEgressWalk")
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
}
