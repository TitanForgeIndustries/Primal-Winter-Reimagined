# Primal Winter: Reimagined — Audit and Stabilization Record

Audit date: 2026-09-09
Target: Minecraft 1.20.1 / Forge 47.4.10 / Java 17 / Gradle 8.1.1

The active distribution is the Forge project. The older `Common` source tree is wired directly
into that Forge source set; the obsolete standalone vanilla compiler project is no longer included
in the Gradle build. The legacy top-level `src/main` mirror is retained for compatibility with the
original repository layout and has the same behavioral fixes applied to its shared code.

## Findings and fixes

| ID | Problem | Severity | Root cause | Fix | Verification |
|---|---|---|---|---|---|
| BUILD-01 | A clean checkout could not configure Gradle because required Minecraft/Forge/mod properties were absent. | CRITICAL | `gradle.properties` only contained JVM settings while the root and Forge scripts delegated all metadata/version values. | Restored the complete 1.20.1 Forge property set and Java 17 requirement. | `clean build` succeeds. |
| BUILD-02 | ForgeGradle 5.1 was incompatible with the Gradle 8 wrapper. | CRITICAL | The project pinned an old ForgeGradle line while using Gradle 8.1.1. | Updated the Forge plugin to the compatible 6.0–6.2 range. | Forge compilation and packaging succeed. |
| BUILD-03 | Dependency resolution failed against the retired Epsilon JFrog endpoint. | HIGH | Epsilon was declared but no source code referenced it; the repository now serves an HTML landing page. | Removed the unused dependency, repository, relocation, and stale property. | Dependency resolution and full build succeed. |
| BUILD-04 | The archived Common vanilla compiler pipeline failed before it could build. | HIGH | The Sponge vanilla plugin/API path was obsolete and could not resolve the Minecraft/mergetool artifacts. | The Forge distribution now compiles Common sources directly; the obsolete Common project is not included. | `settings.gradle.kts` has a single active Forge project; clean build succeeds. |
| CRASH-01 | The supplied integrated-server crash occurred while Minecraft initialized `WorldGenRegion`. | CRITICAL | `WorldGenRegionMixin` used `@ModifyArgs` with `@At("HEAD")`; `HEAD` is not a method invocation, so Mixin rejected the injection with `InvalidInjectionException` before world generation. | Replaced the invalid injection with a descriptor-targeted `@Redirect` on `ChunkAccess#setBlockState`, retaining the winter-active and configured-biome guards. | Forge 47.4.10 client initialization, dedicated-server startup, and fresh Overworld/Aether chunk generation complete without the original mixin crash. |
| DATA-01 | The checked-in resource generator still constructed a nonexistent Fabric output and depended on the caller's working directory. | HIGH | The repository was reduced to Forge, but `Data/main.py` retained the old three-loader layout and relative paths. | Removed the stale Fabric target, linked only Forge/Common buffers, and resolved resource roots relative to the script. | Python 3.14 syntax check passes; generator no longer references a missing output tree. |
| RESOURCE-01 | The registered `primalwinter:instance` biome modifier had no JSON in the active Forge resources. | HIGH | The only copy was in the unused top-level source tree. | Added the biome modifier JSON to `Forge/src/main/resources`. | The JSON is present in both processed resources and the packaged jar. |
| RESOURCE-02 | Resource processing failed on duplicate assets. | HIGH | Common resources were added both through `sourceSets` and a second `processResources.from(...)` copy spec. | Kept the Common resource source directory once and removed the duplicate copy. | `:Forge:processResources` and `clean build` succeed. |
| DIM-01 | Snow logic was inconsistent across dimensions and client/server paths. | HIGH / COMPATIBILITY | Server accumulation, particles, fog, and rendering used different global rain/temperature checks, with no shared dimension capability rule. | Added `WeatherHelper`, which checks configured dimension/biome participation, `DimensionType.hasSkyLight()`, the persisted start day, sky visibility, and height. | Code compilation, mixin preparation, and static review; arbitrary registered dimensions use the same predicate. |
| WEATHER-01 | New worlds entered permanent thunder immediately, bypassing the configured grace period; the weather cycle also stayed disabled before winter. | HIGH | Level-load initialization always called the post-start storm setup. | Initialization and chunk ticking now share `applyWeatherState`: pre-start worlds are clear with the normal cycle, and post-start worlds are permanently thundering with the cycle disabled. | Source-level state transition audit; server launch reaches mod initialization without errors. |
| WEATHER-03 | The Aether's derived level data could share gamerules with the Overworld while not owning writable vanilla weather data. | HIGH / COMPATIBILITY | Calling vanilla weather setters from a `DerivedLevelData` level could toggle the shared weather-cycle rule or otherwise mutate the wrong dimension's authoritative state. | Added `WeatherHelper.canControlWeather`; only a writable `ServerLevel` with winter weather support can apply Primal Winter's permanent storm. Worldgen and the winter predicate remain dimension-aware and are not hardcoded to vanilla IDs. | Default-config Aether runtime plus an explicit Aether-exclusion config variant were both run; restart logs show no Aether weather mutation and the Overworld state remains stable. |
| WEATHER-02 | Snow accumulation could place layers in warm/non-winter biomes or under non-precipitating conditions. | HIGH | The old path checked global `isRaining()` and biome temperature only. | Accumulation now requires `WeatherHelper.isSnowingAt`, active configured biome/dimension participation, sky visibility, height, and light level. | Compiles and is shared with client weather checks. |
| WORLDGEN-01 | Freeze-top-layer worldgen scanned from Y=0, breaking negative-height and unusual-height dimensions. | HIGH | The loop ignored the world’s actual min/max build heights. | Clamped the scan to `getMinBuildHeight()` through `getMaxBuildHeight()-1` and guarded the feature by the active level’s weather rules. | Full Java build succeeds; code uses the 1.20.1 mapped height API. |
| PERFORMANCE-01 | Skylight BFS allocated collections/`Vec3i`s per air block and used `ArrayList.remove(0)`. | PERFORMANCE | A queue implementation was used as a list, with repeated object allocation during chunk generation. | Replaced it with a bounded primitive queue reused for the feature invocation. | Full build succeeds; no `remove(0)`/per-cell collection remains. |
| COMPAT-01 | The SurfaceSystem mixin applied frozen-ocean iceberg behavior to every ocean-tagged biome in every dimension. | COMPATIBILITY | The redirect had no dimension/config context and globally broadened vanilla surface generation. | Removed the invasive mixin and its registration. | The class and mixin entry are absent from the packaged jar. |
| COMPAT-02 | Tree/log replacement ran in every biome of an enabled dimension, including explicitly excluded biomes. | COMPATIBILITY | The mixin only checked the dimension. | The `setBlock` argument mixin now checks the actual configured winter biome at the generated position. | Java compile and Mixin AP succeed. |
| CLIENT-01 | Snow visuals, particles, fog, and sunrise suppression could affect warm/non-winter locations and leak fog across dimension changes. | MEDIUM / COMPATIBILITY | Client code used global rain/temperature checks and static fog interpolation without dimension reset. | Client rendering uses the shared local predicate; fog state resets on player absence and dimension changes; sunrise suppression no longer assumes Overworld effects. | Java compile and dedicated-server classloading path succeed. |
| CLIENT-02 | Eligible winter biomes still rendered rain after the permanent storm began, especially with Particle Rain's default-weather compatibility option enabled. | HIGH / GAMEPLAY / COMPATIBILITY | The previous redirect only covered vanilla `LevelRenderer` call sites. Particle Rain cancels vanilla weather rendering and calls `pigcart.particlerain.VersionUtil#getPrecipitationAt(Level, Holder<Biome>, BlockPos)` directly, so the old redirect was bypassed and warm/naturally snowy biomes could still be classified as rain. | Added a dynamic seasonal precipitation overlay: vanilla `LevelRenderer#renderSnowAndRain`/`tickRain`, `Level#isRainingAt`, and `ServerLevel#tickChunk` all receive `SNOW` for active eligible positions; an optional `@Pseudo` Particle Rain mixin redirects its `VersionUtil` query without a hard dependency. `BiomeMixin` supplies the cold side of vanilla freeze/snow checks while leaving registry data unchanged. | Exact Forge 47.4.10 build; dedicated Runtime11/12/13 launches with Particle Rain and Aether; packaged optional mixin registration; fresh pre/post worldgen and exclusion evidence. Interactive visual appearance remains not verified because no Minecraft window was exposed. |
| CONFIG-01 | The active Forge default for `enableWeatherCommand` differed from the loader-agnostic/root defaults. | MEDIUM | A later config commit updated only one of the duplicated source trees. | Aligned the active Forge default to `false` and synchronized the legacy mirror. | Full build and packaged config metadata succeed. |
| CONFIG-02 | Live config reload values were published through non-volatile primitive fields. | MEDIUM | `/primalwinterReloadConfig` can update common/client holders while the render or tick thread is reading them. | Made scalar holders volatile and kept the exclusion sets immutable snapshots. | Java compilation passes; reload reads now have cross-thread visibility. |
| CI-01 | Release automation attempted to upload a nonexistent Fabric artifact, used the wrong Forge 1.20 filename, depended on archived release actions, and did not declare release-write permission. | HIGH | The repository has no Fabric project, the archive name includes `1.20.1`, the old create/upload actions are no longer a durable CI path, and repository defaults may be read-only. | CI now uses maintained checkout/setup/script actions, declares `contents: write`, and uses the runner's GitHub CLI to create a draft release with only the actual Forge jar. | Workflow reviewed against `Forge/build/libs`; YAML structure, permissions, and artifact path checked. |
| RUN-01 | `runServer` failed on a fresh checkout before Java started because `Forge/run` did not exist. | MEDIUM | ForgeGradle’s run setup did not create the configured working directory. | Added a tracked `Forge/run/.gitkeep` and a JavaExec directory safeguard. | A second run reaches ModLauncher, Forge discovery, and Mixin initialization. |
| WINTER-01 | The Forge biome modifier rewrote climate and water colors while datapacks were baked, before a level/day existed. | HIGH | `primalwinter:instance` previously forced precipitation, temperature, and special effects globally. | Removed those metadata mutations; runtime level-aware predicates now provide Primal Winter precipitation only after the start day. Vanilla freeze-top-layer generation is no longer removed. | Source trace and clean build; dedicated runtime launch below. |
| WINTER-02 | The custom disk configured feature could place snow, powder snow, or ice without consulting `WeatherHelper`. | CRITICAL | The registered `primalwinter:disk` was the vanilla `DiskFeature`, whose `place` method writes its state directly. | Added `ImprovedDiskFeature`, registered it under the same feature ID, and gate its entry point with `WeatherHelper.isWinterActive`. | Java compilation, registry/resource validation, and packaged-jar inspection. |
| WINTER-03 | Seasonal checks were duplicated between weather, accumulation, worldgen, and client rendering. | HIGH | Several paths compared raw day time independently or only checked rain/biome state. | Added the single `WeatherHelper.isWinterActive(Level)` predicate using persisted world time (`dayTime / 24000 >= snowStartDay`) and routed all winter paths through it. | Exact boundary reasoning plus source search for remaining raw start-day comparisons. |
| WINTER-04 | Feature entry and tree replacement paths could still do work in pre-start chunks even when individual block placement was guarded. | HIGH | The freeze-top-layer scan and `WorldGenRegion#setBlock` replacement were reached before the per-position helper. | Added entry guards before the chunk scan, spike writes, and tree/log replacement. | Source trace; pre-start paths return before writes or expensive scans. |
| WINTER-05 | Winter mob spawn entries were attached to configured biome data for all dates. | MEDIUM | Biome spawn metadata is baked without level context. | Kept the intended Polar Bear/Stray entries for active winter, and deny their Forge `MobSpawnEvent.PositionCheck` before the authoritative start day (including spawner attempts). | Java compilation and event registration review; live spawn test remains pending EULA/world setup. |

## Static validation

- `./gradlew.bat :Forge:clean :Forge:build --stacktrace` — **PASS** after the Winter Start Day and
  precipitation-pipeline changes. The build host used JDK 21; the project remains configured for
  Java 17 bytecode/runtime compatibility and the actual external client instance uses Java 17.
- Java compilation — **PASS**; only upstream deprecation notes remain.
- Mixin annotation processing, reobfuscation, slim jar, and shadow jar — **PASS**.
- Source JSON validation — **381 files across the active and mirrored source trees, PASS**.
- Packaged-jar JSON validation — **192 files, PASS**.
- Model, texture, particle, and sound reference checks — **PASS**.
- `python -m py_compile Data/main.py` — **PASS** (Python 3.14.7); the generator now targets the active Forge/Common layout.
- Gradle test task — **NO-SOURCE**; the repository contains no automated test sources.
- Packaged jar contains the biome modifier, common/Forge mixin descriptors, the optional
  `primalwinter.particlerain.mixins.json`, refmap, `WeatherHelper`, `BiomeMixin`, `LevelMixin`,
  `ParticleRainVersionUtilMixin`, and `ImprovedDiskFeature`; the removed SurfaceSystem mixin is
  absent. The final `primalwinter-forge-1.20.1-0.0.0-indev.jar` is 461,705 bytes and was produced
  under `Forge/build/libs/` (SHA-256
  `1287A00E740AA85CD7BD770F28EB21EDC11C6EF2147EA3AA11ABDA71B4025BF0`). The same hash was copied
  to the external test instance and tested against Forge 47.4.10. A direct Java 17 client launch
  with that exact external copy reached an integrated single-player world.

## Runtime validation

A dedicated Forge 47.4.10 server harness was run with Minecraft 1.20.1, the final jar above,
Particle Rain `4.0.0-beta.11+1.20.1-forge`, and The Aether
`1.20.1-1.5.2-neoforge` all present and enabled. The required jars were also retained in the
external test instance at `E:/Minecraft/Instances/Primal Winter Test/mods`; nothing was removed,
disabled, or replaced with a minimal mod list. Runtime11 reached `Done`, crossed `/time add 10d`,
generated 3,349 Overworld chunks and 841 Aether chunks with the default configuration, saved,
restarted, and stopped cleanly. Runtime12 was a fresh day-0 pre-winter world. Runtime13 repeated
the Aether generation with `aether:the_aether` explicitly configured as a non-winter dimension.
Runtime14 set `nonWinterBiomes = ["minecraft:plains"]`, jumped directly to day 5, located a plains
biome at `[-96, 74, -64]`, force-loaded the surrounding range, and generated 2,025 chunks with no
Primal Winter snow/ice/powder-snow/custom terrain palette entries; the disposable config was then
restored to the default list.

The supplied crash was reproduced as a Mixin injection failure, then cleared by the
`WorldGenRegionMixin` redirect described in CRASH-01. The runtime matrix used fresh worlds with
the configured `snowStartDay = 5`, generated substantial pre-start terrain in both the Overworld
and `aether:the_aether`, advanced the exact boundary, generated new post-start terrain in both
dimensions, stopped and restarted the server, and inspected the persisted level data. The external
client also reached a single-player world with both compatibility mods and applied the optional
Particle Rain precipitation mixin.

### Precipitation pipeline correction

The gameplay bug was in the client precipitation decision, not in the server storm flag. In
Minecraft 1.20.1, `LevelRenderer#renderSnowAndRain` calls
`Biome#getPrecipitationAt(BlockPos)`: `RAIN` selects the rain geometry and rain atlas, while
`SNOW` selects the snow geometry and snow atlas. `LevelRenderer#tickRain` uses the same enum for
vanilla particle selection. Particle Rain's configured `compat.renderDefaultWeather=false` path
cancels those vanilla methods and instead calls
`pigcart.particlerain.VersionUtil#getPrecipitationAt(Level, Holder<Biome>, BlockPos)` directly.
That direct call was the reason the earlier LevelRenderer-only fix still showed rain in a fresh
world.

`LevelRendererMixin` now redirects both vanilla call sites, while the optional
`ParticleRainVersionUtilMixin` redirects Particle Rain's direct query. Both return `SNOW` only
for an active, configured dimension/biome position; every other position delegates to the real
biome. `BiomeMixin` supplies the same seasonal cold result to vanilla `shouldFreeze` and
`shouldSnow`, retaining the normal light, water, neighbour, height, and survival checks. This is
a dynamic rendering/world-state decision: it does not mutate `Biome` definitions, climate data,
or global precipitation metadata, and it does not hardcode the Overworld or Aether dimension IDs.
The fix is mirrored in the active Common source and the retained legacy source mirror.

### Required compatibility matrix

| Required check | Result | Evidence |
|---|---|---|
| Particle Rain tested | **PASS** | Particle Rain `4.0.0-beta.11+1.20.1-forge` remained present and enabled in every dedicated-runtime launch and in the external Forge instance. The final external Java 17 client reached an integrated world and logged `ParticleRainVersionUtilMixin` into `pigcart.particlerain.VersionUtil`, alongside Particle Rain's `WeatherEffectRendererMixin` and Primal Winter's `LevelRendererMixin`. Runtime11/12/13 also loaded it beside Aether without a Primal Winter crash. Visual interaction with its particles is separately **NOT VERIFIED**. |
| The Aether tested | **PASS** | `aether:the_aether` `1.20.1-1.5.2-neoforge` loaded with its required dependencies, generated new chunks in Runtime11 before/after winter, survived save/reload, and generated the explicit-exclusion Runtime13 range. Visual precipitation behavior inside the dimension is separately **NOT VERIFIED**. |
| Overworld tested | **PASS** | Fresh Overworld day-0 world, pre-start generation, exact day-5 transition, post-start generation, and restart were completed. |
| Modded-dimension worldgen tested | **PASS** | Runtime11 generated 841 Aether chunks with both mods enabled after the winter transition; no `WorldGenRegion` crash occurred. |
| Pre-winter worldgen tested | **PASS** | Fresh Runtime12 started at day 0 with clear pre-start weather and generated 3,349 Overworld chunks; its region palettes contained no `minecraft:snow`, `snow_block`, `ice`, `powder_snow`, or `primalwinter:*` blocks. |
| Exact winter-start transition tested | **PASS** | Runtime10 tested `time set 119999`/day 4 then one tick to day 5, and direct `time set 120000`/day 5. Runtime13 repeated direct `time set 120000` while Aether was excluded. The authoritative state switched at the configured boundary. |
| `/time add 10d` transition tested | **PASS** | Runtime11 used `/time add 10d`; the server reported day 10 with Particle Rain and Aether still loaded and the winter state active. |
| Post-winter worldgen tested | **PASS** | Runtime11 generated 3,349 Overworld and 841 Aether chunks; palettes contained vanilla snow/powder snow/ice/snow blocks and Primal Winter snowy terrain/tree blocks. |
| Configured exclusion/dimension rules tested | **PASS** | Runtime13 added `aether:the_aether` to the disposable `nonWinterDimensions` list; its 841 Aether chunks contained Aether icestone but no `minecraft:snow` or `primalwinter:*` blocks. Runtime14 added `minecraft:plains` to `nonWinterBiomes`, located that biome, generated its surrounding range after day 5, and found no winter palette entries. No hardcoded dimension ID was added. |
| Dynamic precipitation enum routing tested | **PASS (static/runtime load)** | The final packaged mixins route vanilla and optional Particle Rain precipitation queries to `SNOW` only for active eligible positions; the exact artifact registered all three configs and loaded in the required dedicated runtime. This does not substitute for visual observation. |
| Eligible-biome snow precipitation | **NOT VERIFIED (visual)** | The corrected runtime code returns `Biome.Precipitation.SNOW` for active eligible positions and supplies cold `shouldFreeze`/`shouldSnow` behavior, but no controllable client window was available to observe the rendered snow directly. |
| Client precipitation/rendering tested | **NOT VERIFIED** | The final external Java 17 client reached an integrated world and the runtime mixin hook applied, but the desktop automation surface exposed no controllable Minecraft window or screenshot. Interactive snow geometry/atlas selection, Particle Rain custom snow particles, fog, ambience, and texture appearance were therefore not visually confirmed and are not claimed as passing. |
| Save/reload tested | **PASS** | Runtime11 was stopped and restarted with both required mods; the post-start weather/time state persisted and the second startup completed. Runtime12/13 also saved and stopped cleanly. |

The default-config run left Aether eligible for winter participation, proving that the logic works
in a modded sky-bearing dimension. A separate test-only config variant added `aether:the_aether`
to `nonWinterDimensions`; that run confirmed the configured exclusion rule and, after restart,
showed no Aether weather mutation. This changed only the disposable harness config; the external
test instance's configuration and both required mod jars were left intact.

## Remaining limitations

1. There is no repository test suite. The dedicated runtime matrix above covers the requested
   worldgen, transition, dimension, and persistence checks, but it does not replace a longer
   multiplayer/performance soak or a visual QA pass.
2. Forge biome modifiers are evaluated without a dimension or level argument. The modifier now
   leaves climate and special-effect metadata untouched; its feature and spawn entries are inert
   until runtime gates inspect the actual level. Dimension-specific validation should still be
   performed with any mod that reuses the same biome across enabled and excluded dimensions.
3. The legacy top-level source mirror remains tracked because it is part of the upstream layout;
   the active build does not compile it. Shared fixes were mirrored there to prevent the two layouts
   from silently diverging, but consolidating/removing that historical tree would be a separate
   repository-organization change.
4. The final Java 17 client did reach an integrated world and applied the Particle Rain
   precipitation hook, but interactive rendering could not be visually verified because the
   available desktop automation surface did not expose the running Minecraft window. The final
   report intentionally marks client precipitation/rendering as **NOT VERIFIED** rather than
   claiming visual compatibility from startup or mixin logs alone.
5. `Data/main.py` was syntax-checked and statically audited, but its external `mcresources` Python
   dependency is not declared by this repository and is not installed in the audit environment, so
   a live regeneration run was not performed. The checked-in generated outputs were validated
   directly and are what the Gradle build packages.
6. One exploratory test attempted a very large, high-coordinate Aether forceload in a single
   synchronous command and exceeded the dedicated server's 60-second watchdog while Aether was
   generating that range. This was a test-harness watchdog limit in Aether's `ForceLoadCommand`,
   not a Primal Winter crash or a reason to remove either required mod. The staged 16x16/8x8
   Overworld and Aether ranges, plus the final-artifact Runtime11 smoke, completed without it.

## Winter Start Day / Pre-Winter Worldgen Audit

### World Generation

Primal Winter can place winter terrain through four active paths:

* `ImprovedFreezeTopLayerFeature` scans a generated chunk and may place vanilla snow layers,
  ice, obsidian, or a Primal Winter snowy terrain block. Its `place` method returns immediately
  unless `WeatherHelper.isWinterActive(level.getLevel())` is true, before the height scan begins.
* `ImprovedIceSpikeFeature` can seed a snow block before delegating to the vanilla spike logic. It
  requires the same active state and configured winter biome before writing.
* `ImprovedDiskFeature` replaces the vanilla `DiskFeature` registration used by the `ice_patch`,
  `snow_patch`, and `powder_snow_patch` placed features. Its entry guard prevents the configured
  state provider from writing anything before winter.
* `WorldGenRegionMixin` replaces generated tree/log/leaf blocks with Primal Winter snowy blocks.
  It checks the active level and the actual configured biome before changing the `setBlock`
  arguments.

The Forge biome modifier still attaches the placed features to configured biomes at datapack bake
time, but it cannot know a level or day. Attachment alone performs no writes: each feature is
runtime-gated. The modifier no longer changes precipitation, temperature, water colors, or
removes vanilla `FREEZE_TOP_LAYER`, so vanilla snow is not globally removed by a block/worldgen
ban. The existing grace-period design still intentionally clears precipitation in enabled,
sky-bearing dimensions before the start day; configured exclusions and no-sky dimensions retain
their normal weather behavior. No SurfaceSystem or global ChunkGenerator hook remains.

### Snow Textures

The custom snowy block models and textures are referenced only by the registered
`primalwinter:snowy_*` blocks. They are not a replacement for `minecraft:block/snow` and no
resource-pack/global texture override is installed. Worldgen and tree replacement are the only
automatic paths that create those blocks, and both use the active gate. The custom particle
textures (`assets/primalwinter/textures/particle/snow_0.png` through `_3.png`) are emitted only
from the active snow-rendering path in `LevelRendererMixin`; registering their provider at client
startup does not spawn a particle by itself. Explicitly placing a registered decorative snowy
block from an inventory remains an intentional player action, not an environmental winter path.

### Seasonal State

`WeatherHelper.isWinterActive(Level)` is the single seasonal predicate. It requires a configured
dimension with sky light and evaluates the persisted level time as:

```text
active = dayTime / 24000L >= Config.INSTANCE.snowStartDay
```

Therefore day `N - 1`, including its final tick (`N * 24000 - 1`), is inactive; the first tick of
day `N` (`N * 24000`) is active. Server weather initialization, chunk accumulation, all custom
worldgen features, tree replacement, mob-spawn denial, fog, sunrise suppression, precipitation
rendering, particles, and wind sounds consume this predicate directly or through
`canSnowAt`/`isSnowingAt`.

### Existing Chunks

No hook regenerates or rewrites a chunk when the date changes. `ServerLevelMixin#tickChunk` only
attempts normal active-weather accumulation after the shared predicate is true. Existing terrain
therefore remains as generated; it can receive later snow layers through the normal active storm.

### New Chunks

Before the start day, every Primal Winter feature entry point returns before scanning or writing,
the disk state provider is never run, and tree replacement is skipped. After the start day, the
same placed features run in configured biomes and the server-level gates permit their intended
snow/ice/terrain writes. Vanilla and other-mod worldgen is not intercepted by a global snow block
ban.

### Client/Server

The server owns `Level#getDayTime` and synchronizes it through vanilla level-time packets. Client
rendering uses the received `ClientLevel#getDayTime` through the same `WeatherHelper` predicate;
there is no client wall-clock or system-time decision. On level load, the server reapplies the
appropriate clear/pre-start or permanent-storm/post-start weather state, preventing reconnects
and restarts from retaining stale Primal Winter weather. Dimension checks are performed against
the current level on every query, so dimension travel naturally resets the visual decision.

### Modded Dimensions

No dimension ID whitelist is used. `Config.isWinterDimension`, the configured biome exclusions,
and `DimensionType.hasSkyLight()` are evaluated against the current registered level. A compatible
modded dimension is inactive before the start day and participates after it; Nether-like/no-sky
dimensions and configured exclusions remain untouched.

### Tests

The following checks were performed for this follow-up:

* Source-level tracing covered every `Blocks.SNOW`, `Blocks.SNOW_BLOCK`, `Blocks.ICE`, custom
  feature registration, biome modifier, `WorldGenRegion`, server tick, fog, precipitation,
  particle, and snow-texture path found by repository search.
* The configured default boundary was checked arithmetically and in fresh worlds: day 4's final
  tick (`119999`) is inactive, day 5 tick `120000` is active, and day 6 remains active. Runtime10
  also applied `time set 120000` directly and reported day 5 immediately.
* `./gradlew.bat :Forge:clean :Forge:build --stacktrace` was run after the implementation changes
  with the Java 21 build host; the project targets Java 17 and the external client runtime used
  Java 17. The result is recorded in the final verification section below.
* Source and packaged-jar JSON, model/texture/particle/sound references, Mixin descriptors, and
  the absence of the removed SurfaceSystem mixin were rechecked after packaging.
* A full EULA-accepted Forge 47.4.10 runtime matrix was run with Particle Rain and The Aether
  enabled. It covered fresh day-0 generation, the `119999`/day-4 to day-5 boundary, new chunks
  after winter began, Aether travel and generation, the configured Aether and plains exclusion
  variants, and stop/restart persistence. The exact required results are recorded in the matrix
  above.
* The final external Java 17 client launched with Particle Rain and The Aether enabled, reached an
  integrated single-player world, and logged `ParticleRainVersionUtilMixin` applied to
  `pigcart.particlerain.VersionUtil`. The interactive visual pass was not available in this
  environment; the client precipitation/rendering row is explicitly marked **NOT VERIFIED**.
