package ch.sbb.matsim.routing.access;

import java.util.Collection;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;

import ch.sbb.matsim.analysis.LocateAct;
import ch.sbb.matsim.config.AccessTimeConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRouter;
import ch.sbb.matsim.routing.teleportation.SBBBeelineTeleportationRouting;

public class AccessEgress {
    Controler controler;
    private LocateAct locateAct;
    private String shapefile;

    public AccessEgress(Controler controler) {
        this.controler = controler;
        this.shapefile = this.getConfigGroup().getShapefile();
    }

    public AccessEgress(Controler controler, String shapefile) {
        this.controler = controler;
        this.shapefile = shapefile;
    }

    public LocateAct getLocateAct() {
        if (locateAct == null) {
            locateAct = new LocateAct(shapefile);
            locateAct.fillCache(this.controler.getScenario().getPopulation());
        }
        return locateAct;
    }

    public AccessTimeConfigGroup getConfigGroup() {
        Config config = this.controler.getConfig();
        return ConfigUtils.addOrGetModule(config, AccessTimeConfigGroup.GROUP_NAME, AccessTimeConfigGroup.class);
    }

    public SBBNetworkRouter getNetworkRouter(String mode) {
        LocateAct _locateAct = this.getLocateAct();
        return new SBBNetworkRouter(mode, _locateAct);
    }

    public SBBBeelineTeleportationRouting getTeleportationRouter(String mode) {
        LocateAct _locateAct = this.getLocateAct();
        PlansCalcRouteConfigGroup.ModeRoutingParams params = this.controler.getConfig().plansCalcRoute().getModeRoutingParams().get(mode);
        return new SBBBeelineTeleportationRouting(params, _locateAct);
    }

    public AbstractModule getModule(Collection<String> modes, Collection<String> mainModes) {
        AccessEgress that = this;
        return new AbstractModule() {
            @Override
            public void install() {

                for (String mode : modes) {
                    if (mainModes.contains(mode)) {
                        addRoutingModuleBinding(mode).toProvider(that.getNetworkRouter(mode));
                    } else {

                        addRoutingModuleBinding(mode).toProvider(that.getTeleportationRouter(mode));
                    }
                }
            }
        };
    }

    public void installAccessTime() {
        Scenario scenario = controler.getScenario();
        Config config = scenario.getConfig();

        AccessTimeConfigGroup accessTimeConfigGroup = this.getConfigGroup();
        Collection<String> mainModes = config.qsim().getMainModes();

        if (accessTimeConfigGroup.getInsertingAccessEgressWalk()) {
            config.plansCalcRoute().setInsertingAccessEgressWalk(true);
            controler.addOverridingModule(this.getModule(accessTimeConfigGroup.getModesWithAccessTime(), mainModes));
        }
    }
}
