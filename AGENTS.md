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

