SNAP IdePix Sentinel-2 MSI shadow improvements for SNAP 14
===========================================================

Purpose
-------
This note describes the differences between the current official SNAP 14 IdePix S2 MSI module
(`upstream/14.x`) and the evaluated improved version. It is intended for the SNAP IdePix
development team as a review and handover aid, not as a replacement for the algorithm theoretical
basis document.

Implementation identity
-----------------------
Earlier evaluated branch: codex/s2-shadow-final-14
Earlier evaluated commit: 5166b520  Finalize materialized cloud shadow input
Final branch:     codex/s2-shadow-final-14-ready
Finalization:     2026-10-07; based on the tested clean candidate, with quiet logging/offline Help.
Base revision:    SNAP 14.x / SNAP_release_14.0.0 lineage
Candidate NBM:    idepix-s2msi/target/nbm/idepix-s2msi-14.0.1-SNAPSHOT.nbm

The work was deliberately performed on a local branch. No pull request was created and the NBM
is not an official SNAP release.

Release-note candidate (for maintainer review)
----------------------------------------------
Use or adapt the following bullets only after maintainer review and final version assignment:

  - Improved Sentinel-2 MSI cloud-shadow detection by reusing the input-resolution IdePix cloud
    classification and preparing the established 60 m shadow grid with mean resampling for
    continuous data and FlagOr resampling for flags.
  - Added local per-cloud shadow matching and resolution-aware diamond-shaped cloud-buffer and
    final cloud-shadow dilation.
  - Improved S2 cloud-shadow processing performance by materializing compact intermediate flag
    products, avoiding repeated upstream classification during overlapping shadow-path reads.
  - Replaced the S2 mountain-shadow result with a directional terrain cast-shadow calculation
    using a 5 km surrounding-DEM margin.
  - Changed the default S2 IdePix elevation model to Copernicus 30m Global DEM; other installed
    DEMs, including SRTM 3Sec, remain selectable.
  - Simplified public output by retaining the final cloud-shadow and mountain-shadow flags while
    keeping intermediate potential/clustered cloud-shadow masks internal.
  - Expanded offline Help with the S2 MSI algorithm description and practical tile-cache/thread
    performance guidance for GPT and SNAP Desktop.

Scope
-----
The changes concern the S2 MSI cloud-shadow and mountain-shadow pathways. Core cloud
classification and the externally visible default of `computeCloudShadow=false` are unchanged.
The default DEM is now `Copernicus 30m Global DEM`; `SRTM 3Sec` and other installed DEMs remain
available as explicit user choices.

The official SNAP 14 module and the improved module both retain the lifecycle-safe cloud-shadow
execution required to avoid the incomplete-shadow-tile problem observed after SNAP 12. The
improved implementation keeps the cloud-shadow operator and target product alive for the complete
post-processing lifecycle, while allowing target-tile requests to drive evaluation. It does not
restore the earlier unsafe invocation/disposal behaviour.

Algorithm changes
-----------------
1. Reuse the first, input-resolution cloud classification

Official SNAP 14 behaviour for a 10 m or 20 m input was:

  a. resample the L1C source to 60 m using `First` downsampling;
  b. run a second IdePix S2 classification on that 60 m product; and
  c. use that second classification for cloud-shadow processing.

The improved implementation instead uses the classification already produced for the requested
input resolution. It resamples only the products required by cloud-shadow processing to its
existing 60 m working grid:

  - continuous bands: `Mean` downsampling;
  - flag bands: `FlagOr` downsampling;
  - upsampling: `Nearest`.

This avoids a second full IdePix classification, avoids `First`-sample downsampling of physical
reflectances, and retains cloud evidence already obtained on the requested input grid. At 60 m,
no additional resampling is performed.

2. Diamond-shaped cloud buffer and cloud-shadow dilation

The square/box cloud buffer was replaced by a Manhattan-distance (diamond/star) structuring
element. `cloudBufferWidth` remains the user-facing parameter and default (2 pixels), but its
meaning is now the Manhattan radius rather than a square half-width.

The final cloud-shadow mask is also dilated on the target product grid using the same
diamond-shaped geometry. The radius is resolution-dependent:

  - 10 m input: 4 pixels;
  - 20 m input: 2 pixels (40 m physical reach along rows/columns);
  - 60 m input: 1 pixel.

The dilation never adds a cloud-shadow flag on pixels already classified as cloud or invalid.
This makes the output edge more isotropic than a box filter while retaining the intended safety
margin.

3. Per-cloud local shadow matching

The existing scene-wide best cloud-shadow displacement is retained as a prior. The improved
processor identifies connected cloud objects and searches locally around that prior for a better
offset per cloud object. Candidate offsets are scored against clustered spectral shadow evidence;
the global offset remains the fallback when a local match is not sufficiently supported.

This reduces the failure mode in which spatially separated clouds with different effective heights
or viewing geometry are forced to use one scene-wide displacement. The user-visible parameter
`usePerCloudShadowMatching` defaults to `true`.

4. Optional cloud-buffer shadow casters

`includeCloudBufferForShadow` was added. When enabled, cloud-buffer pixels are treated as
additional cloud-shadow casting objects. It requires cloud-buffer computation and defaults to
`false`, preserving a conservative default. This is useful when the cloud edge is deliberately
buffered before shadow projection. The benchmark below enabled it for the improved run.

5. Existing cloud-gap recovery retained

Cloud-gap recovery remains enabled. It allows the geometrically shifted cloud mask to supply
shadow pixels in cloud-gap regions where spectral clustering cannot provide an observation. An
experiment to make this optional found fewer commissions but also omitted correct shadows; it was
therefore not retained as a user-facing option.

6. Development-only cloud-shadow flags are no longer public

`IDEPIX_POTENTIAL_SHADOW` and `IDEPIX_CLUSTERED_CLOUD_SHADOW` remain internal workflow masks. They
are no longer copied into the public `pixel_classif_flags` band, flag coding, or the SNAP mask
list. The public `IDEPIX_CLOUD_SHADOW` flag remains the final, user-facing cloud-shadow result.

Mountain-shadow changes
-----------------------
The former S2 mountain-shadow result was based on local terrain illumination/slope behaviour. The
final implementation instead uses a terrain cast-shadow calculation at the input product grid
(10, 20, or 60 m). For each terrain scanline in the solar direction, it maintains the maximum
up-sun terrain elevation angle; a pixel is terrain-shadowed when higher terrain blocks the direct
solar ray. Fractional upstream sampling is used to avoid directional striping artefacts.

The elevation raster is expanded with a fixed 5 km surrounding-DEM margin before the calculation.
This permits terrain outside the Sentinel-2 scene footprint to cast shadows into the scene. Solar
azimuth and zenith use the scene-centre geometry, which was tested against local 3x3 geometry and
retained because the local variant gave only minor visual differences at additional cost. The final
`IDEPIX_MOUNTAIN_SHADOW` flag represents this terrain cast-shadow result.

The earlier projected/legacy cast-shadow experiment and the slope-only runtime path were evaluated
for comparison but are intentionally disconnected from the public IdePix operator and service
registration. They remain source-history material only, not selectable processor modes. The final
method has no experimental algorithm selector or diagnostic shadow flags.

Deliberately not included
-------------------------
The following experiments were evaluated but rejected from the final NBM because they did not
produce a consistent quality benefit commensurate with their cost on the test scenes:

  - native 20 m cloud-shadow processing rather than the established 60 m working grid;
  - cloud-object parallax correction;
  - terrain correction of the cloud-shadow projection;
  - B8/B11 spectral replacement and the investigated Fmask-style flood-fill variant.

Thus, the final method remains a 60 m cloud-shadow analysis with target-grid output dilation; it
is not a wholesale replacement of the existing shadow methodology.

Performance implementation changes
----------------------------------
The dominant SNAP 14 cost was not the offset calculation itself. The preprocessor requests
overlapping, cloud-path-expanded rectangles from the resampled `pixel_classif_flags` source.
When that source remains lazy, each request can re-trigger expensive upstream classification work.

The final code materializes the already-resampled 60 m `pixel_classif_flags` band once into an
in-memory integer raster before cloud-shadow preprocessing. It reads non-overlapping preferred
source tiles concurrently, bounded by `snap.parallelism`, copies the source geocoding, and uses
the materialized product as both the preprocessor's and postprocessor's classification input. B8A/B3 and the rest of
the graph remain lazy as before. This prevents repeated flag evaluation for overlapping rectangles
without changing the preprocessing algorithm.

The final candidate also materializes the compact 60 m post-shadow FlagBand once before
resampling to the output grid, preventing repeated post-shadow computation during output writing.
Both 60 m materializations are mandatory; no bypass switches remain. Native-resolution flags
are not retained. Standard SNAP FlagOr is used, not the experimental custom native-grid aggregator.
The original 610x610 preferred layout is restored, with no tile-size multiplier parameter.

Internal resampling now uses only the required flags, B8A, B3, elevation and four geometry bands,
plus recursively resolved valid-pixel dependencies. Modern reader detector-footprint/no-data
references are preserved from the original L1C rather than discarded. The original input is not
mutated by this source preparation, and no validity expression is cleared. This fixes the T32TQT
undefined detector-footprint symbol failure without resampling unrelated reflectance bands.

This implementation was validated by comparing full `pixel_classif_flags.img` SHA-256 hashes:

  - T20NRL: materialized and lazy input outputs were bit-identical;
  - T01KHT: materialized output was bit-identical and retained complete shadows in the historical
    missing-tile case;
  - T11XMD same-runtime control: materialized output was bit-identical to a deliberately enabled
    lazy-input control, while taking 129.037 s instead of 213.019 s (1.65x faster).

The diagnostic bypass used for the T11XMD control was removed from the final code. Materialization
is mandatory in the final NBM. Detailed preprocessing timing remains opt-in through the existing
`snap.idepix.s2msi.cloudshadow.profile` JVM property and Java logging at FINE level.

Main changed source areas
-------------------------
  - S2IdepixCloudShadowOp: input classification/band resampling, one-time parallel flag
    materialization, cloud-shadow orchestration.
  - S2IdepixPostProcessOp: lifecycle-safe lazy consumption, target-grid star dilation, parameter
    forwarding.
  - S2IdepixCloudBuffer and S2IdepixCloudPostProcessOp: Manhattan cloud-buffer construction.
  - S2IdepixPostCloudShadowOp and cloudshadow helpers: per-cloud matching and score/model types.
  - CloudShadowDilation: resolution-dependent star structuring element.
  - S2IdepixPerformanceAdvice and S2IdepixOp: effective-settings summary and non-blocking advice.
  - Cloud-shadow setup/pre/post/outer profiles, FFT helpers and resolution reporting: routine
    diagnostic messages use FINE logging; genuine errors/warnings and SNAP progress remain visible.
  - CastMountainShadow and S2IdepixCastMountainShadowOp: native-grid directional terrain cast
    shadow with a fixed 5 km DEM margin; legacy/slope methods no longer form part of the public
    IdePix mountain-shadow execution.
  - S2IdepixOp, S2IdepixPostProcessOp and operator descriptors: Copernicus 30m Global DEM is the
    default DEM consistently for the GUI, GPT and nested shadow operators.
  - Operator help/command-line help and unit tests: parameter documentation and regression tests.

Benchmark protocol
------------------
Date: 2026-10-01
Runtime: SNAP 14.0.0 with the official IdePix module downloaded through SNAP Desktop, followed by
the improved local NBM. Each condition processed the same five 20 m, resampled Sentinel-2 L1C
BEAM-DIMAP products sequentially.

Shared processing settings:

  - `computeCloudShadow=true`, `computeMountainShadow=false`;
  - Copernicus 30 m Global DEM;
  - cloud buffer enabled, width 2;
  - GPT `-q 16`, `-c 4G`;
  - BEAM-DIMAP output; no copied TOA reflectances or feature values;
  - profiling disabled;
  - DEM cache already warm; products with variable remote DEM 404/retry behaviour excluded.

Improved-only settings:

  - `usePerCloudShadowMatching=true` (the final default);
  - `includeCloudBufferForShadow=true` (explicit benchmark choice; final default is false).

Excluded, due to variable Copernicus DEM 404/retry timing:

  - S2A_MSIL1C_20200210T142721_N0209_R053_T21NUH_20200210T160724_s2resampled.dim
  - S2B_MSIL1C_20190514T173909_N0207_R098_T12QZM_20190514T211115_s2resampled.dim

Measured runtime
----------------
Product / tile                         Official SNAP 14     Improved NBM   Saving   Speed-up
S2A ... T01KHT ...                         110.488 s          45.978 s    58.4 %    2.40x
S2A ... T32TQT ...                         275.312 s         135.004 s    51.0 %    2.04x
S2B ... T20NRL ...                         341.887 s          96.879 s    71.7 %    3.53x
S2B ... T31QFB ...                         281.394 s         158.715 s    43.6 %    1.77x
S2B ... T11XMD ...                         182.434 s         155.936 s    14.5 %    1.17x
------------------------------------------------------------------------------------------------
Total                                    1191.515 s         592.512 s    50.3 %    2.01x
Mean                                      238.303 s         118.502 s

All ten invocations completed with exit code 0. No DEM 404, exception, or other error was found
in either benchmark log set. The improvement varies by scene because the avoided repeated lazy
evaluation depends on the cloud-path rectangles and scene content.

The comparison measures the complete desired final configuration, not an isolated microbenchmark.
It therefore includes both result-preserving performance work and intentional algorithm/mask
changes. It must not be interpreted as proof that the official and improved cloud-shadow masks
are identical; they are intentionally different in the areas affected by matching, casting, and
dilation. The one-time flag materialization itself was separately proven output-identical above.

Benchmark workstation and runtime configuration
-----------------------------------------------
  - Manufacturer/model: Lenovo 21H5CTO1WW
  - CPU: AMD Ryzen 7 PRO 7730U with Radeon Graphics (8 physical cores, 16 logical processors)
  - RAM: 30.8 GiB
  - OS: Microsoft Windows 11 Pro, version 10.0.26200, build 26200
  - Output storage: SKHynix_HFS001TEJ4X113N NVMe, 953.87 GiB
  - Power plan: Balanced
  - SNAP: 14.0.0
  - SNAP Java runtime reported by GPT: OpenJDK 21.0.6 LTS, 64-bit

GPT VM options used during the benchmark:

  -Xms18G
  -Xmx21G
  -XX:+UseG1GC
  -Dsnap.jai.defaultTileCacheSize=4096M
  -Dsnap.parallelism=16

Interpretation and reproducibility notes
-----------------------------------------
Wall-clock measurements depend on processor, RAM, tile-cache size, Java heap, disk I/O, DEM cache
state, scene content, and concurrent system load. The reported 2.01x aggregate speed-up is valid
for the configuration above, not a universal guarantee. First-use DEM downloads and variable
remote 404/retry delays must be excluded from processor-cost comparisons.

For a 20 m S2 input, a tile cache materially larger than the traditional default is important.
The benchmark uses 4 GiB cache with a 21 GiB maximum heap. Cache should be selected as a prudent
fraction of the configured heap, leaving memory for the Java heap, source products, operators, and
the operating system; it should not be copied blindly to smaller machines.

User performance guidance (added 2026-10-05)
--------------------------------------------
Routine runs: detailed per-stage/per-tile timing reports are disabled by default in the plugin.
The benchmark explicitly sets profiling false unless -ProfileCloudShadow is supplied, overriding
any persistent profile=true entry in gpt.vmoptions. Progress, startup settings and genuine warnings
remain visible. One Completed in ... seconds message per product covers the complete GPT process,
including output writing. For direct GPT use, remove -Dsnap.idepix.s2msi.cloudshadow.profile=true
from gpt.vmoptions or override with -J-Dsnap.idepix.s2msi.cloudshadow.profile=false.
Timing reports and routine internal stage messages now use Java logging FINE level, so they
remain hidden at normal INFO level even if an old configuration still enables profile counters.
Developer diagnostics need the profiling property plus FINE logging; algorithm output is unchanged.

IdePix logs effective cache capacity, maximum heap, input resolution/dimensions, tile-scheduler
threads, available processors and the internal flag-worker limit. Advice is non-blocking: no
automatic changes to global settings and no GUI popup. Desktop messages appear in the application
log; the Help links to the configuration instructions. Low-cache thresholds are conservative
heuristics, not required minima or guaranteed optimums. Large-scene shadow warnings apply below
4 GiB at 10 m, 2 GiB at 20 m, or 1 GiB at 60 m; small subsets are exempt. Disabled shadow caches,
caches using at least 75% of maximum heap, and more tile threads than available processors are
also flagged. Single-thread tile scheduling is informational, not an error.

Prefer explicit GPT per-run flags for reproducible processing:
  gpt Idepix.S2 -Sl1cProduct=input.dim -PcomputeCloudShadow=true -c 8G -q 16 -t output.dim
-c selects the in-memory tile-cache capacity, -q SNAP tile-scheduler parallelism. IdePix's internal
flag-reader worker limit currently comes from the snap.parallelism JVM property, or available
processors if unset; -q does not independently constrain every internal worker pool.

Persistent GPT settings: <SNAP installation>\bin\gpt.vmoptions, one JVM option per line, for example:
  -Xmx21G
  -Dsnap.jai.defaultTileCacheSize=8192M
  -Dsnap.parallelism=16
No -J prefix here. Start a new GPT process. Record -c/-q explicitly and verify the startup summary.

Desktop: Tools > Options > Performance, Cache Size (MB) and Number of Threads. Enter 8192 for
8 GiB. Cache Path is a disk directory, not the in-memory capacity. Alternatively, append inside
the existing quoted default_options in <SNAP installation>\etc\snap.conf:
  -J-Dsnap.jai.defaultTileCacheSize=8192M -J-Dsnap.parallelism=16
Desktop heap uses -J-Xmx, not the GPT vmoptions file. Preserve all other launcher arguments and
avoid duplicate entries. Back up configuration files, restart Desktop, and check effective values;
saved Desktop preferences can override defaults. Updates may replace launcher files.

The cache is part of the heap, not additional RAM. Leave heap headroom for products and operators,
and physical RAM for native libraries, the OS and concurrent jobs. More threads are not always
faster. Reduce per-job memory/threads for concurrent GPT processes. Close unused Desktop products
before benchmarking; large initial heaps (-Xms) can exhaust Windows commit/page-file capacity.
No global tile-size changes are recommended as an IdePix performance-tuning step.

Recent 10 m experiments (flags-only, q16, multiplier 1, warm Copernicus 30 m, tested host above):
T20NRL: cache 4 GiB / retention off 664.916 s; cache 8 GiB / retention off 325.176 s;
cache 8 GiB / retention on 290.420 s. The larger-cache pair reduced elapsed time by 51.1%.
T32TQT (validity-dependency repair installed): cache 8 GiB / retention off 352.639 s;
retention on 367.402 s (4.2% longer). All within-scene retention-pair flag hashes are identical.
One run per setting does not establish repeatability. Native retention has no consistent benefit
demonstrated; retention and the custom native-grid aggregator are removed from this final candidate.
These timings are not the earlier official-vs-final 20 m benchmark and must not be pooled with it.
Clean final candidate matched resolution test (2026-10-05, Alps T32TQT, flags-only output):
  20 m: 120.181 s (run snap14_clean_final_20m_43)
  10 m: 402.780 s (run snap14_clean_final_10m_42)
Effective settings in both logs: cache 8192 MiB, maximum heap 21504 MiB, tile threads 16,
internal flag-worker limit 16. Copernicus 30 m, per-cloud matching and cloud-buffer casting enabled.
Both successful; no DEM download/404 messages found. 10 m is 3.35x the elapsed time for 4x pixels.
Classification-flag materialization (including upstream classification/refinement/resampling)
was 95.916 s at 20 m and 353.769 s at 10 m; post-shadow flag materialization 5.048/4.656 s.
10 m flags are bit-identical to the earlier native-FlagOr/retention experiment outputs on this scene.
10 m SHA-256: 00A76FD386E49B749DE4A75C6AA8DF326EB33FE38E46BCAF5F6C1BB2EED2B683
20 m SHA-256: 99D56710426ECB9414EEB8EC41949171BEF2CA5C02FC6C035364AE102FE20F52
Cross-resolution hashes are not expected to match. One run per resolution is not a repeatability
estimate. This clean 10 m run was 14.2% longer than the earlier custom-aggregator off-retention run;
implementation changes and runtime variability cannot be separated by that single comparison.
These are measured clean-candidate results; earlier experiment times are not interchangeable.

Additional clean-candidate constrained configuration (same Alps scene and flags-only protocol):
                          16 threads / 8 GiB       6 threads / 4 GiB
  20 m total                 120.181 s                107.793 s
  10 m total                 402.780 s                907.663 s
Both tile-scheduler and internal flag-worker limits are confirmed in startup logs. The benchmark
now sets -J-Dsnap.parallelism as well as -q to keep the limits aligned. No DEM download/404
messages found; both resolutions are bit-identical to their respective clean reference outputs.
20 m was 10.3% faster with the constrained configuration; 10 m was 125.4% longer (2.25x).
10 m post-shadow flag materialization rose from 4.656 s to 118.974 s; substantial additional
time also occurred outside the setup timers. This is consistent with cache-sensitive repeated
evaluation, but cache and worker counts changed together. One run per setting cannot separate
their contributions or establish universal optimal values; a q6/cache 8 GiB 10 m control is suggested.

Recommended review points
-------------------------
  1. Review the 60 m resampling semantics (`Mean` continuous data, `FlagOr` flags) and whether
     they should become the accepted physical definition for the cloud-shadow working grid.
  2. Review the intended defaults: per-cloud matching is true; buffered cloud casters are false.
  3. Review target-grid 20 m dilation as a methodological change, separately from the
     output-preserving materialization optimisation.
  4. Re-run the benchmark on a maintained SNAP test host with warm DEM cache and repeated runs;
     include output-quality evaluation against a labelled shadow reference set.
  5. Keep the existing incomplete-tile regression scene (T01KHT) in any migration validation.
  6. Review the terrain-envelope cast-shadow definition and fixed 5 km DEM margin against a
     representative mountain reference set, including high terrain casting across lower terrain.
  7. Confirm that changing the default DEM to Copernicus 30m Global DEM is appropriate for the
     supported SNAP installations and document DEM-download/network behaviour where relevant.
