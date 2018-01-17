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
    final private Controler controler;
    private LocateAct locateAct;
    final private String shapefile;

    public AccessEgress(final Controler controler) {
        this.controler = controler;
        this.shapefile = this.getConfigGroup().getShapefile();
    }

    public AccessEgress(final Controler controler, final String shapefile) {
        this.controler = controler;
        this.shapefile = shapefile;
    }

    private LocateAct getLocateAct() {
        if (locateAct == null) {
            locateAct = new LocateAct(shapefile);
            locateAct.fillCache(this.controler.getScenario().getPopulation());
        }
        return locateAct;
    }

    private AccessTimeConfigGroup getConfigGroup() {
        final Config config = this.controler.getConfig();
        return ConfigUtils.addOrGetModule(config, AccessTimeConfigGroup.GROUP_NAME, AccessTimeConfigGroup.class);
    }

    private SBBNetworkRouter getNetworkRouter(final String mode) {
        final LocateAct _locateAct = this.getLocateAct();
        return new SBBNetworkRouter(mode, _locateAct);
    }

    private SBBBeelineTeleportationRouting getTeleportationRouter(final String mode) {
        final LocateAct _locateAct = this.getLocateAct();
        PlansCalcRouteConfigGroup.ModeRoutingParams params = this.controler.getConfig().plansCalcRoute().getModeRoutingParams().get(mode);
        return new SBBBeelineTeleportationRouting(params, _locateAct);
    }

    private AbstractModule getModule(final Collection<String> modes, final Collection<String> mainModes) {
        final AccessEgress that = this;
        return new AbstractModule() {
            @Override
            public void install() {

                for (final String mode : modes) {
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
        final Scenario scenario = controler.getScenario();
        final Config config = scenario.getConfig();

        final AccessTimeConfigGroup accessTimeConfigGroup = this.getConfigGroup();
        final Collection<String> mainModes = config.qsim().getMainModes();

        if (accessTimeConfigGroup.getInsertingAccessEgressWalk()) {
            config.plansCalcRoute().setInsertingAccessEgressWalk(true);
            final AbstractModule module = this.getModule(accessTimeConfigGroup.getModesWithAccessTime(), mainModes);
            controler.addOverridingModule(module);
        }
    }
}
