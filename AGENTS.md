# AGENTS.md — matsim-sbb (SIMBA MOBi)

Agent-based passenger demand model for **SBB** (Swiss Federal Railways), built on **MATSim**.
Java 25 / Maven project. `groupId=ch.sbb`, main class `ch.sbb.matsim.RunSBB`.

## Big picture

A simulation run is a MATSim `Controler` that is heavily customized via SBB modules and
config groups. Flow to understand before editing:

1. `RunSBB.main(args)` — `args[0]` = config XML, optional `args[1]` = output dir.
2. `ConfigUtils.loadConfig(configFile, getSbbDefaultConfigGroups())` — SBB-specific
   `ConfigGroup`s are registered in `RunSBB.getSbbDefaultConfigGroups()`. **Any new config
   group MUST be added there** or it won't be parsed from XML.
3. `RunSBB.run(config)` — builds `Scenario` (preparation steps from `preparation/`), then
   constructs a `Controler` and installs anonymous/`AbstractModule`s that `bind(...)` SBB
   implementations (e.g. `SBBScoringFunctionFactory`, `SBBPermissibleModesCalculator`,
   `RaptorTransferCostCalculator`, analysis listeners).
4. Real base configs live in `baseconfigs/config_<year>.xml` (2017–2050 scenarios).

## Package map (`src/main/java/ch/sbb/matsim`)

- `config/` — custom `ConfigGroup`s (extend `ReflectiveConfigGroup`), e.g.
  `PostProcessingConfigGroup`, `SBBBehaviorGroupsConfigGroup`. `config/variables/` holds
  constants (`SBBModes`, `Variables`, `SamplesizeFactors`).
- `routing/` — network + PT routing (`SBBNetworkRoutingModule`, `access/AccessEgressModule`,
  `pt/raptor/` SwissRailRaptor customizations, capacity-dependent in-vehicle cost).
- `zones/` — spatial zone system (`ZonesModule`, `ZonesCollection`), backed by `.gpkg`.
- `intermodal/` — `IntermodalModule` for multimodal (park&ride, feeder) routing.
- `scoring/` — `SBBScoringFunctionFactory`, behavior-group-aware scoring params.
- `mobsim/qsim/` — `SBBTransitModule`, `SBBTransitEngineQSimModule` (deterministic PT sim).
- `preparation/` — scenario building: XY→link/facility assignment, transit schedule prep,
  scenario cutters, population slicing (`slicer/`).
- `replanning/` — `SBBSubtourModeChoice`, `SBBPermissibleModesCalculator`.
- `analysis/` — `SBBDefaultAnalysisListener` + `convergence/`, `modalsplit/`,
  `tripsandlegsanalysis/` (implement `ControlerListener`, bound in modules).
- `mavi/` — Visum (PTV) network / PT import + street network export (uses JACOB COM on Windows).
- `csv/` — `CSVReader` / `CSVWriter` (configurable separator, compression via MATSim `IOUtils`).

## MOBi Plans (`plans/`) — synthetic demand generation

Second entry point next to `RunSBB`. Generates MATSim-compatible daily activity plans
from a synthetic population, upstream of the mobsim. Unlike `RunSBB` this subsystem uses
**YAML config**, **Google Guice** DI (not MATSim modules), and a pipeline of composable
`ModelStep`s.

- `RunMOBiPlans.main(args)` — `args[0]` = YAML config; loads `MOBiPlansConfig` via
  `MOBiPlansConfig.readFromYaml`, creates the Guice `Injector` from
  `DefaultMOBiPlansModule`, then runs `MOBiPlansRunner`.
- `MOBiPlansRunner` — orchestrator. Registers ~17 named steps (e.g. `SynpopLoader`,
  `CalcZonalAttributes`, `MobilityToolOwnershipModel`, `PrimaryLocationChoiceModel`,
  `WorkFromHomeModel`, `InitialModeChoiceModel`, `StartTimeChoiceModel`,
  `TourBuildingModel`, `IterativeDailyPlanGenerationOrchestrator`, `AssignFacilityLocations`,
  `PopulationSampler`, `StatsWriter`, `MOBiPlansOutputWriter`) and executes the sequence
  declared in `models.run` of the YAML. Steps are lazily instantiated via the injector.
- Config: `models/config/MOBiPlansConfig` is a Jackson `record` with `DataConfig`
  (input paths: synpop Arrow/Feather, population/households XML, `.gpkg` zones,
  facilities, car/PT skims, Kalif matrices, `modelBaseDir`, `outputBaseDir`, etc.)
  and `ModelsConfig` (`threads`, `sampleSize`, `run_id`, `run` sequence, `wfh_rate`, …).
  Constants in `MOBiPlansConfig` name the many CSV/YAML files expected under
  `modelBaseDir` (`config/`, `choice_models/`, `data/`, `reports/`).
- DI: `modules/DefaultMOBiPlansModule` binds config, `Scenario`, zones
  (`MOBiZonesProvider` loads `.gpkg` + CSV attributes), matrices
  (`MOBiMatricesProvider` loads skims + Kalif OD), and each `ModelStep` via a Guice
  `Multibinder`.

Subpackages under `plans/`:
- `constants/` — synpop attribute keys (`SynpopConstants`: person_id, household_id,
  age, employment, education, driving_license, work/school zone, …).
- `matrices/` — `Matrices` API + `MOBiMatricesProvider` (car/PT skims, Kalif OD).
- `models/` — `config/` (YAML records) and `steps/` (all `ModelStep` implementations:
  discrete choice + rule-based population/plan generators).
- `modules/` — Guice `AbstractModule`s and providers (`DefaultMOBiPlansModule`,
  `MOBiZonesProvider`, …).
- `synpop/` — `SynpopLoader` reading Arrow/Feather persons/households/businesses or
  MATSim XML, linking to zones.
- `stats/` — analysis / report writers (activated by `models.write_analysis`).
- `tours/` — tour + subtour data structures used by tour-building steps.

Run: `java -cp target/matsim-sbb-*.jar ch.sbb.matsim.plans.RunMOBiPlans path/to/config.yaml`.

When adding a new model step: implement `ModelStep`, register it in
`DefaultMOBiPlansModule`'s multibinder, and reference its `getName()` in the YAML
`models.run` list.

## Developer workflows

- Build (fat jar via shade, main class `ch.sbb.matsim.RunSBB`):
  `mvn clean package` → `target/matsim-sbb-<version>.jar`.
- Run a scenario: `java -jar target/matsim-sbb-*.jar baseconfigs/config_2023.xml <outputDir>`.
- Tests: `mvn test`. JUnit 5 (`org.junit.jupiter`). Convention: `*Test.java` = unit,
  `*IT.java` = integration tests that run **full mini scenarios** from
  `test/input/scenarios/` (e.g. `RunSBBIT` uses `mobi50test/config_2023.xml`,
  `RunSBBDRTScenarioIT` for DRT). Keep integration scenarios small.
- Dependencies come from custom repos (MATSim `repo.matsim.org`, geotools `osgeo`, jitpack,
  SBB artifactory). MATSim version is pinned in `pom.xml` `<matsim.version>` — bump carefully.

## Conventions

- Config groups: extend `ReflectiveConfigGroup`, expose params with `@StringGetter`/
  `@StringSetter`; nested repeatable params use the `ParameterSet` pattern (see
  `SBBBehaviorGroupsConfigGroup`). Register in `RunSBB.getSbbDefaultConfigGroups()`.
- Modules end in `Module` and extend `AbstractModule`; DI via `bind(...)`, `Provider<T>`,
  and `@Named` bindings.
- Naming: classes prefixed `SBB` override/replace MATSim defaults.
- `System.setProperty("matsim.preferLocalDtds", "true")` is set at startup — keep it.

## Data formats & integrations

- Zones: GeoPackage `.gpkg`; matrices: Apache Arrow / CSV; skims/matrices: `io.jhdf` HDF5.
- Outputs are compressed: `.csv.gz` and `.zst` (see `output/`, `test/output/`).
- Geospatial via GeoTools (shapefiles, geotiff, epsg-hsql), JTS geometry, SQLite JDBC.
- S3 access via `s3/S3Downloader` (AWS SDK v2).
- Python calibration in `src/main/python/`: `calibrate.py` tunes mode-choice ASCs using
  `matsim-tools[calibration]`; run `./src/main/python/setup.sh` to create the venv.

## Language
- Stick to English for code, comments, and documentation. Reply in the language asked.
