package ch.sbb.matsim.plans.matrices;

import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.zones.Zone;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.matsim.api.core.v01.Id;

@Singleton
public class MOBiMatricesProvider implements Provider<Matrices> {

    @Inject
    MOBiPlansConfig config;

    @Override
    public Matrices get() {
        Matrices matrices = new DefaultMatrices();
        MatrixReaders.readAndProcessCarSkims(config.data().carSkimsFile(), matrices);
        MatrixReaders.readAndProcessPTSkims(config.data().ptSkimsFile(), matrices);
        MatrixReaders.readKalifMatrices(config.data().kalifMatricesFile(), matrices);
        return matrices;
    }
}
