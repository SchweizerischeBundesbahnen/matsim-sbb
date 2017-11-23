package ch.sbb.matsim.config;

import org.matsim.core.config.ReflectiveConfigGroup;


public class AccessTimeConfigGroup extends ReflectiveConfigGroup{
    static public final String GROUP_NAME = "AccessTime";

    private String shapefile = "";

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

}
