package ch.sbb.matsim.plans;

import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.modules.DefaultMOBiPlansModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.File;
import java.io.IOException;

public class RunMOBiPlans {
    public static void main(String[] args) throws IOException {
        String configFile = args[0];
        MOBiPlansConfig config = MOBiPlansConfig.readFromYaml(configFile);

        File outputDir = new File(config.data().outputBaseDir());
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        DefaultMOBiPlansModule defaultMOBiPlansModule = new DefaultMOBiPlansModule(config);
        Injector injector = Guice.createInjector(defaultMOBiPlansModule);
        injector.getInstance(MOBiPlansRunner.class).run();
    }
}
