package org.esa.snap.idepix.s2msi;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.idepix.s2msi.operators.cloudshadow.S2IdepixCloudShadowOp;
import org.esa.snap.idepix.s2msi.operators.cloudshadow.S2IdepixPreCloudShadowOp;
import org.esa.snap.idepix.s2msi.operators.mountainshadow.S2IdepixCastMountainShadowOp;
import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLASSIF_FLAGS;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_BUFFER;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_SHADOW;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_INVALID;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_MOUNTAIN_SHADOW;

/**
 * Operator used to consolidate cloud flag for Sentinel-2:
 * - coastline refinement? (tbd)
 * - cloud shadow (tbd)
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.S2.Postprocess",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Refines the Sentinel-2 MSI pixel classification.")
public class S2IdepixPostProcessOp extends Operator {

    private static final boolean PROFILE = Boolean.getBoolean("snap.idepix.s2msi.cloudshadow.profile");
    private static final int PROFILE_INTERVAL = Math.max(1,
            Integer.getInteger("snap.idepix.s2msi.cloudshadow.profile.interval", 10));

    private final AtomicInteger profileCloudShadowTiles = new AtomicInteger();
    private final LongAdder profileCloudShadowSourceNanos = new LongAdder();
    private final LongAdder profileCloudShadowDilationNanos = new LongAdder();
    private final LongAdder profileCloudShadowIntegrationNanos = new LongAdder();
    private final LongAdder profileCloudShadowTotalNanos = new LongAdder();

    @SourceProduct(alias = "l1c")
    private Product l1cProduct;

    @SourceProduct(alias = "s2Classif")
    private Product s2ClassifProduct;

    @SourceProduct(alias = "s2CloudBuffer", optional = true)
    private Product s2CloudBufferProduct;      // has only classifFlagBand with buffer added

    @Parameter(defaultValue = "true", label = "Compute mountain shadow", description = "Whether to compute mountain shadow")
    private boolean computeMountainShadow;

    @Parameter(defaultValue = "true", label = "Compute cloud shadow", description = "Compute cloud shadow")
    private boolean computeCloudShadow;

    @Parameter(defaultValue = "true", label = "Use per-cloud shadow matching")
    private boolean usePerCloudShadowMatching;

    @Parameter(defaultValue = "false", label = "Include cloud buffer in cloud-shadow detection")
    private boolean includeCloudBufferForShadow;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer also for cloud ambiguous pixels")
    private boolean computeCloudBufferForCloudAmbiguous;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int cloudBufferWidth;

    @Parameter(description = "The mode by which clouds are detected. There are three options: Land/Water, Multiple Bands" +
            "or Single Band", valueSet = {"LandWater", "MultiBand", "SingleBand"}, defaultValue = "LandWater")
    private String mode;

    @Parameter(defaultValue = "0.01",
            label = " Threshold CW_THRESH",
            description = " Threshold CW_THRESH")
    private double cwThresh;

    @Parameter(defaultValue = "-0.11",
            label = " Threshold GCL_THRESH",
            description = " Threshold GCL_THRESH")
    private double gclThresh;

    @Parameter(defaultValue = "0.01",
            label = " Threshold CL_THRESH",
            description = " Threshold CL_THRESH")
    private double clThresh;

    @Parameter(description = "The digital elevation model.", defaultValue = "Copernicus 30m Global DEM", label = "Digital Elevation Model")
    private String demName = "Copernicus 30m Global DEM";

    private Band cloudBufferFlagBand;
    private Band mountainShadowFlagBand;
    private Product cloudShadowProduct;
    private Operator cloudShadowOp;
    private int cloudShadowDilationRadius;
    private int[][] cloudShadowDilationOffsets;

    @Override
    public void dispose() {
        logCloudShadowProfile("final");
        if (cloudBufferFlagBand != null) {
            cloudBufferFlagBand.dispose();
            cloudBufferFlagBand = null;
        }
        disposeBand(mountainShadowFlagBand);
        mountainShadowFlagBand = null;
        if (cloudShadowOp != null) {
            cloudShadowOp.dispose();
            cloudShadowOp = null;
            cloudShadowProduct = null; // is disposed with operator.dispose()
        }
        super.dispose();
    }

    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = createTargetProduct(s2ClassifProduct.getName(),
                                                                s2ClassifProduct.getProductType());

        if (s2CloudBufferProduct != null) {
            cloudBufferFlagBand = s2CloudBufferProduct.getBand(IDEPIX_CLASSIF_FLAGS);
        }

        if (computeMountainShadow) {
            Map<String, Object> mountainShadowParameters = new HashMap<>();
            mountainShadowParameters.put("demName", demName);
            Product castShadowProduct = GPF.createProduct(
                    OperatorSpi.getOperatorAlias(S2IdepixCastMountainShadowOp.class), mountainShadowParameters,
                    s2ClassifProduct);
            mountainShadowFlagBand = castShadowProduct.getBand(
                    S2IdepixCastMountainShadowOp.MOUNTAIN_SHADOW_FLAG_BAND_NAME);
        }

        if (computeCloudShadow) {
            cloudShadowDilationRadius = CloudShadowDilation.getRadius(
                    S2IdepixUtils.determineResolution(l1cProduct));
            cloudShadowDilationOffsets = CloudShadowDilation.createStarOffsets(cloudShadowDilationRadius);
            HashMap<String, Product> input = new HashMap<>();
            input.put("l1cProduct", l1cProduct);
            input.put("s2ClassifProduct", s2CloudBufferProduct);
            input.put("s2BandsProduct", s2ClassifProduct);
            Map<String, Object> params = new HashMap<>();
            // we have decided not to use the mountain shadow as implemented in
            // to include it again
            //      - set the value to computeMountainShadow, change
            //      - add || computeMountainShadow to the condition above
            //      - consider the flag results in computeTile
            params.put("computeMountainShadow", false);
            params.put("usePerCloudShadowMatching", usePerCloudShadowMatching);
            params.put("includeCloudBufferForShadow", includeCloudBufferForShadow);
            params.put("computeCloudBuffer", computeCloudBuffer);
            params.put("cloudBufferWidth", cloudBufferWidth);
            params.put("computeCloudBufferForCloudAmbiguous", computeCloudBufferForCloudAmbiguous);
            params.put("mode", mode);
            params.put("cwThresh", cwThresh);
            params.put("gclThresh", gclThresh);
            params.put("clThresh", clThresh);
            params.put("demName", demName);

            final GPF gpf = GPF.getDefaultInstance();
            cloudShadowOp = gpf.createOperator(OperatorSpi.getOperatorAlias(S2IdepixCloudShadowOp.class),
                    params, input, null);

            cloudShadowProduct = cloudShadowOp.getTargetProduct();
            // Keep the operator and its target product alive for the complete post-processing lifecycle.
            // Evaluation remains demand-driven when the target tiles request this source band.
        }

        ProductUtils.copyBand(IDEPIX_CLASSIF_FLAGS, s2ClassifProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }


    private Product createTargetProduct(String name, String type) {
        final int sceneWidth = s2ClassifProduct.getSceneRasterWidth();
        final int sceneHeight = s2ClassifProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(s2ClassifProduct, targetProduct);
        targetProduct.setStartTime(s2ClassifProduct.getStartTime());
        targetProduct.setEndTime(s2ClassifProduct.getEndTime());

        return targetProduct;
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();

        if (s2CloudBufferProduct != null) {
            Tile cloudBufferFlagTile = getSourceTile(cloudBufferFlagBand, targetRectangle);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    if (targetRectangle.contains(x, y)) {
                        boolean isInvalid = targetTile.getSampleBit(x, y, IDEPIX_INVALID);
                        if (!isInvalid) {
                            combineFlags(x, y, cloudBufferFlagTile, targetTile);
                        }
                    }
                }
            }
        }

        if (computeMountainShadow) {
            final Tile mountainShadowFlagTile = mountainShadowFlagBand == null ? null :
                    getSourceTile(mountainShadowFlagBand, targetRectangle);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    final boolean mountainShadow = mountainShadowFlagTile != null &&
                            mountainShadowFlagTile.getSampleInt(x, y) > 0;
                    targetTile.setSample(x, y, IDEPIX_MOUNTAIN_SHADOW, mountainShadow);
                }
            }
        }
        if (computeCloudShadow) {
            final long cloudShadowStart = profileStart();
            Band band = cloudShadowProduct.getBand(S2IdepixCloudShadowOp.BAND_NAME_CLOUD_SHADOW);
            final Rectangle sourceRectangle = CloudShadowDilation.extend(targetRectangle, cloudShadowDilationRadius,
                    band.getRasterWidth(), band.getRasterHeight());
            final long sourceStart = profileStart();
            final Tile flagTile = getSourceTile(band, sourceRectangle);
            profileAdd(profileCloudShadowSourceNanos, sourceStart);
            int cloudBufferFlag = (int) Math.pow(2, S2IdepixPreCloudShadowOp.F_CLOUD_BUFFER);
            int recommendedCloudShadow = (int) Math.pow(2, S2IdepixPreCloudShadowOp.F_RECOMMENDED_CLOUD_SHADOW);
            final long dilationStart = profileStart();
            final boolean[] dilatedCloudShadow = dilateCloudShadow(flagTile, sourceRectangle, targetRectangle,
                    recommendedCloudShadow);
            profileAdd(profileCloudShadowDilationNanos, dilationStart);
            final long integrationStart = profileStart();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    final int flagValue = flagTile.getSampleInt(x, y);
                    final boolean originalCloudShadow = hasFlag(flagValue, recommendedCloudShadow);
                    final int targetIndex = (y - targetRectangle.y) * targetRectangle.width + x - targetRectangle.x;
                    final boolean mayAddDilatedShadow = !targetTile.getSampleBit(x, y, IDEPIX_INVALID) &&
                            !targetTile.getSampleBit(x, y, IDEPIX_CLOUD);
                    if (originalCloudShadow || (dilatedCloudShadow[targetIndex] && mayAddDilatedShadow)) {
                        targetTile.setSample(x, y, IDEPIX_CLOUD_SHADOW, true);
                    }
                    if (hasFlag(flagValue, cloudBufferFlag)) {
                        targetTile.setSample(x, y, IDEPIX_CLOUD_BUFFER, true);
                    }
                }
            }
            profileAdd(profileCloudShadowIntegrationNanos, integrationStart);
            profileAdd(profileCloudShadowTotalNanos, cloudShadowStart);
            logCloudShadowProfile(null);
        }
    }

    private static void disposeBand(Band band) {
        if (band != null) {
            band.dispose();
        }
    }

    private static long profileStart() {
        return PROFILE ? System.nanoTime() : 0L;
    }

    private static void profileAdd(LongAdder accumulator, long startNanos) {
        if (PROFILE) {
            accumulator.add(System.nanoTime() - startNanos);
        }
    }

    private void logCloudShadowProfile(String reason) {
        if (!PROFILE) {
            return;
        }
        final int completed = reason == null ? profileCloudShadowTiles.incrementAndGet() : profileCloudShadowTiles.get();
        if (completed == 0 || (reason == null && completed % PROFILE_INTERVAL != 0)) {
            return;
        }
        final String suffix = reason == null ? "" : " " + reason;
        SystemUtils.LOG.fine(String.format("S2 cloud-shadow outer profile%s: tiles=%d, source=%.3fs, dilation=%.3fs, " +
                        "integration=%.3fs, total=%.3fs",
                suffix, completed,
                profileCloudShadowSourceNanos.doubleValue() / 1.0e9,
                profileCloudShadowDilationNanos.doubleValue() / 1.0e9,
                profileCloudShadowIntegrationNanos.doubleValue() / 1.0e9,
                profileCloudShadowTotalNanos.doubleValue() / 1.0e9));
    }

    private boolean[] dilateCloudShadow(Tile flagTile, Rectangle sourceRectangle, Rectangle targetRectangle,
                                        int recommendedCloudShadowFlag) {
        final boolean[] dilatedCloudShadow = new boolean[targetRectangle.width * targetRectangle.height];
        for (int sourceY = sourceRectangle.y; sourceY < sourceRectangle.y + sourceRectangle.height; sourceY++) {
            for (int sourceX = sourceRectangle.x; sourceX < sourceRectangle.x + sourceRectangle.width; sourceX++) {
                if (!hasFlag(flagTile.getSampleInt(sourceX, sourceY), recommendedCloudShadowFlag)) {
                    continue;
                }
                CloudShadowDilation.addStar(dilatedCloudShadow, targetRectangle.width, targetRectangle.height,
                        sourceX - targetRectangle.x, sourceY - targetRectangle.y, cloudShadowDilationOffsets);
            }
        }
        return dilatedCloudShadow;
    }

    private static boolean hasFlag(int flagValue, int flagMask) {
        return (flagValue & flagMask) == flagMask;
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(S2IdepixPostProcessOp.class);
        }
    }
}
