package ch.sbb.matsim.plans.models.steps.demandstrata;

import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class DemandStrataRegistry {

    private final Map<String, DemandStrata> registry = new ConcurrentHashMap<>();

    public void register(DemandStrata strata) {
        registry.put(strata.getName().toLowerCase(), strata);
    }

    public DemandStrata getByName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Cannot query DemandStrata with a null name.");
        }
        DemandStrata result = registry.get(name.toLowerCase());
        if (result == null) {
            throw new IllegalArgumentException("No DemandStrata registered for name: " + name);
        }
        return result;
    }

    public Set<DemandStrata> getPrimaryDestinationChoiceStratas() {
        return registry.values().stream()
                .filter(s -> s.getType() == DemandStrata.Type.PRIMARY_DESTINATION)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<DemandStrata> getSecondaryDestinationStratas() {
        return registry.values().stream()
                .filter(s -> s.getType() == DemandStrata.Type.SECONDARY_DESTINATION)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Collection<DemandStrata> getAll() {
        return registry.values();
    }
}