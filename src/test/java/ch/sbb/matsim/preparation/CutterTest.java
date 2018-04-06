/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.preparation;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class CutterTest {
    @Test
    public void testGeographicallyFilterPopulation() {
        CutterFixture fixture = new CutterFixture();
        fixture.init();

        final Config config = fixture.scenario.getConfig();
        Cutter.CutterConfigGroup cutterConfig = ConfigUtils.addOrGetModule(config, Cutter.CutterConfigGroup.class);

        Cutter cutter = new Cutter(config, cutterConfig, fixture.scenario);

        cutter.geographicallyFilterPopulation();

        Population population = fixture.scenario.getPopulation();
        Assert.assertTrue(population.getPersons().keySet().contains(Id.createPersonId("agent_001")));
        Assert.assertEquals("regular", (String) fixture.scenario.getPopulation().getPersonAttributes().getAttribute("agent_001", "subpopulation"));
        Assert.assertTrue(population.getPersons().keySet().contains(Id.createPersonId("agent_002")));
        Assert.assertEquals("cb", (String) fixture.scenario.getPopulation().getPersonAttributes().getAttribute("agent_002", "subpopulation"));
        Assert.assertTrue(population.getPersons().keySet().contains(Id.createPersonId("agent_003")));
        Assert.assertEquals("regular", (String) fixture.scenario.getPopulation().getPersonAttributes().getAttribute("agent_003", "subpopulation"));
        Assert.assertFalse(population.getPersons().keySet().contains(Id.createPersonId("agent_004")));
        Assert.assertTrue(population.getPersons().keySet().contains(Id.createPersonId("agent_005")));
        Assert.assertEquals("cb", (String) fixture.scenario.getPopulation().getPersonAttributes().getAttribute("agent_005", "subpopulation"));
        Assert.assertTrue(population.getPersons().keySet().contains(Id.createPersonId("agent_006")));
        Assert.assertEquals("cb", (String) fixture.scenario.getPopulation().getPersonAttributes().getAttribute("agent_006", "subpopulation"));
    }
}
