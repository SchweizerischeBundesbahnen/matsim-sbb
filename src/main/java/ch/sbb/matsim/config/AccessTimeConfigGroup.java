package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;


public class AccessTimeConfigGroup extends ReflectiveConfigGroup{
    static public final String GROUP_NAME = "AccessTime";

    private String shapefile = "";
    private Boolean isInsertingAccessEgressWalk = false;

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
}
