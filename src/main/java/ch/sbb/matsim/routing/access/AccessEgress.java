package ch.sbb.matsim.routing.access;

import java.util.Collection;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;

import ch.sbb.matsim.analysis.LocateAct;
import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRouting;
import ch.sbb.matsim.routing.teleportation.SBBTeleportation;

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

    private SBBAccessTimeConfigGroup getConfigGroup() {
        final Config config = this.controler.getConfig();
        return ConfigUtils.addOrGetModule(config, SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);
    }

    private SBBNetworkRouting getNetworkRouting(final String mode) {
        final LocateAct _locateAct = this.getLocateAct();
        return new SBBNetworkRouting(mode, _locateAct);
    }

    private SBBTeleportation getTeleportationRouting(final String mode) {
        final LocateAct _locateAct = this.getLocateAct();
        PlansCalcRouteConfigGroup.ModeRoutingParams params = this.controler.getConfig().plansCalcRoute().getOrCreateModeRoutingParams(mode);
        return new SBBTeleportation(params, _locateAct);
    }

    private AbstractModule getModule(final Collection<String> modes, final Collection<String> mainModes) {
        final AccessEgress that = this;
        return new AbstractModule() {
            @Override
            public void install() {

                for (final String mode : modes) {
                    if (mainModes.contains(mode) || mode.equals(TransportMode.ride)) {
                        addRoutingModuleBinding(mode).toProvider(that.getNetworkRouting(mode));
                    } else {

                        addRoutingModuleBinding(mode).toProvider(that.getTeleportationRouting(mode));
                    }
                }
            }
        };
    }

    public void installAccessTime() {
        final Scenario scenario = controler.getScenario();
        final Config config = scenario.getConfig();

        final SBBAccessTimeConfigGroup accessTimeConfigGroup = this.getConfigGroup();
        final Collection<String> mainModes = config.qsim().getMainModes();

        if (accessTimeConfigGroup.getInsertingAccessEgressWalk()) {
            config.plansCalcRoute().setInsertingAccessEgressWalk(true);
            final AbstractModule module = this.getModule(accessTimeConfigGroup.getModesWithAccessTime(), mainModes);
            controler.addOverridingModule(module);
        }
    }
}
