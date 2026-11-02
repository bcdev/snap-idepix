package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import com.bc.ceres.core.ProgressMonitor;
import com.sun.media.jai.util.SunTileCache;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.datamodel.StxFactory;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.dataop.barithm.BandArithmetic;
import org.esa.snap.core.jexp.ParseException;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.gpf.internal.OperatorExecutor;
import org.esa.snap.core.image.VectorDataMaskOpImage;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;

import javax.media.jai.CachedTile;
import javax.media.jai.JAI;
import javax.media.jai.PointOpImage;
import javax.media.jai.TileCache;
import java.awt.Dimension;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

@OperatorMetadata(alias = "Idepix.S2.CloudShadow",
        category = "Optical",
        authors = "Grit Kirches, Michael Paperin, Olaf Danne, Tonio Fincke, Dagmar Mueller",
        copyright = "(c) Brockmann Consult GmbH",
        version = "2.0",
        internal = true,
        description = "Algorithm detecting cloud shadow...")
public class S2IdepixCloudShadowOp extends Operator {

    private static final Logger LOGGER = SystemUtils.LOG;
    private static final boolean PROFILE = Boolean.getBoolean("snap.idepix.s2msi.cloudshadow.profile");

    @SourceProduct(description = "The original input product")
    private Product l1cProduct;

    @SourceProduct(description = "The classification product from which to take the classification band.")
    private Product s2ClassifProduct;

    @SourceProduct(description = "The product from which to take other bands than the classification band.")
    private Product s2BandsProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The mode by which clouds are detected. There are three options: Land/Water, Multiple Bands" +
            "or Single Band", valueSet = {"LandWater", "MultiBand", "SingleBand"}, defaultValue = "LandWater")
    private String mode;

    @Parameter(description = "Whether to also compute mountain shadow", defaultValue = "true")
    private boolean computeMountainShadow;

    @Parameter(description = "Whether to search a local cloud-shadow offset for each connected cloud",
            defaultValue = "true")
    private boolean usePerCloudShadowMatching;

    @Parameter(description = "Whether cloud-buffer pixels cast cloud shadows", defaultValue = "false")
    private boolean includeCloudBufferForShadow;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer also for cloud ambiguous pixels")
    private boolean computeCloudBufferForCloudAmbiguous;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int cloudBufferWidth;

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

    public final static String BAND_NAME_CLOUD_SHADOW = "FlagBand";

    private Map<Integer, double[][]> meanReflPerTile = new HashMap<>();
    private Map<Integer, Integer> NCloudOverLand = new HashMap<>();
    private Map<Integer, Integer> NCloudOverWater = new HashMap<>();

    @Override
    public void initialize() throws OperatorException {
        final TileCache tileCache = JAI.getDefaultInstance().getTileCache();
        final Observer observer = (o, arg) -> {
            if (arg instanceof CachedTile && ((CachedTile) arg).getAction() == 0) {
                CachedTile tile = (CachedTile) arg;
                RenderedImage owner = tile.getOwner();
                if (owner instanceof VectorDataMaskOpImage || owner instanceof PointOpImage) {
                    int tileX = Math.round((float) tile.getTile().getMinX() / (float) owner.getTileWidth());
                    int tileY = Math.round((float) tile.getTile().getMinY() / (float) owner.getTileHeight());
                    tileCache.remove(owner, tileX, tileY);
                }
            }
        };

        int sourceResolution = S2IdepixUtils.determineResolution(l1cProduct);

        Product[] internalSourceProducts = getInternalSourceProducts(sourceResolution);

        Product classificationProduct = internalSourceProducts[0];
        final Product s2BandsProduct = internalSourceProducts[1];
        final long geometryRetrievalStart = profileStart();
        float sunZenithMean = getGeometryMean(s2BandsProduct, S2IdepixConstants.SUN_ZENITH_BAND_NAME);
        float sunAzimuthMean = getGeometryMean(s2BandsProduct, S2IdepixConstants.SUN_AZIMUTH_BAND_NAME);
        float viewZenithMean = getGeometryMean(s2BandsProduct, S2IdepixConstants.VIEW_ZENITH_BAND_NAME);
        float viewAzimuthMean = getGeometryMean(s2BandsProduct, S2IdepixConstants.VIEW_AZIMUTH_BAND_NAME);
        logProfileDuration("geometry-mean retrieval", geometryRetrievalStart);
        sunAzimuthMean = convertToApparentSunAzimuth(sunAzimuthMean, viewZenithMean, viewAzimuthMean);

        final long classificationMaterializationStart = profileStart();
        Product preClassificationProduct = materializeCloudShadowClassificationProduct(classificationProduct);
        logProfileDuration("classification-flag materialization", classificationMaterializationStart);

        HashMap<String, Product> preInput = new HashMap<>();
        preInput.put("s2ClassifProduct", preClassificationProduct);
        preInput.put("s2BandsProduct", s2BandsProduct);
        Map<String, Object> preParams = new HashMap<>();
        preParams.put("sunZenithMean", sunZenithMean);
        preParams.put("sunAzimuthMean", sunAzimuthMean);
        preParams.put("includeCloudBufferForShadow", includeCloudBufferForShadow);

        //todo: test resolution of granule. Resample necessary bands to 60m. calculate cloud shadow on 60m.
        //todo: let mountain shadow benefit from higher resolution in DEM. Adjust sun zenith according to smoothing.

        //Preprocessing:
        // No flags are created, only statistics generated to find the best offset along the illumination path.
        final String operatorAlias = OperatorSpi.getOperatorAlias(S2IdepixPreCloudShadowOp.class);
        final S2IdepixPreCloudShadowOp cloudShadowPreProcessingOperator =
                (S2IdepixPreCloudShadowOp) GPF.getDefaultInstance().createOperator(operatorAlias, preParams, preInput, null);

        //trigger computation of all tiles
        LOGGER.fine("Executing Cloud Shadow Preprocessing");
        final long preprocessingExecutionStart = profileStart();
        if (tileCache instanceof SunTileCache) {
            ((SunTileCache) tileCache).enableDiagnostics();
            ((SunTileCache) tileCache).addObserver(observer);
        }
        final OperatorExecutor operatorExecutor = OperatorExecutor.create(cloudShadowPreProcessingOperator);
        operatorExecutor.execute(ProgressMonitor.NULL);
        if (tileCache instanceof SunTileCache) {
            ((SunTileCache) tileCache).deleteObserver(observer);
        }
        logProfileDuration("preprocessing executor", preprocessingExecutionStart);
        LOGGER.fine("Executed Cloud Shadow Preprocessing");
        cloudShadowPreProcessingOperator.logProfile();

        NCloudOverLand = cloudShadowPreProcessingOperator.getNCloudOverLandPerTile();
        NCloudOverWater = cloudShadowPreProcessingOperator.getNCloudOverWaterPerTile();
        meanReflPerTile = cloudShadowPreProcessingOperator.getMeanReflPerTile();
        //writingMeanReflAlongPath(); // for development of minimum analysis.

        int[] bestOffsets = findOverallMinimumReflectance();

        int bestOffset = chooseBestOffset(bestOffsets);
        LOGGER.fine("bestOffset all " + bestOffsets[0]);
        LOGGER.fine("bestOffset land " + bestOffsets[1]);
        LOGGER.fine("bestOffset water " + bestOffsets[2]);
        LOGGER.fine("chosen Offset " + bestOffset);


        HashMap<String, Product> postInput = new HashMap<>();
        // The postprocessor consumes only pixel_classif_flags. Reuse the exact 60 m flags already
        // materialized for preprocessing instead of reopening the lazy resampled classification branch.
        postInput.put("s2ClassifProduct", preClassificationProduct);
        postInput.put("s2BandsProduct", s2BandsProduct);
        //put in here the input products that are required by the post-processing operator
        Map<String, Object> postParams = new HashMap<>();
        postParams.put("computeMountainShadow", computeMountainShadow);
        postParams.put("usePerCloudShadowMatching", usePerCloudShadowMatching);
        postParams.put("includeCloudBufferForShadow", includeCloudBufferForShadow);
        postParams.put("bestOffset", bestOffset);
        postParams.put("mode", mode);
        postParams.put("sunZenithMean", sunZenithMean);
        postParams.put("sunAzimuthMean", sunAzimuthMean);
        //put in here any parameters that might be requested by the post-processing operator

        //
        //Postprocessing
        //
        //Generation of all cloud shadow flags
        Product postProduct = GPF.createProduct("Idepix.S2.CloudShadow.Postprocess", postParams, postInput);
        // Retain only the compact 60 m result, preventing repeated evaluation during output resampling.
        LOGGER.fine("Materializing cloud-shadow postprocessing flags");
        final long postFlagMaterializationStart = profileStart();
        postProduct = materializeCloudShadowPostFlags(postProduct);
        logProfileDuration("post-shadow-flag materialization", postFlagMaterializationStart);

        setTargetProduct(prepareTargetProduct(sourceResolution, postProduct));
    }

    /**
     * Materializes the already resampled cloud-shadow classification flags once. Preprocessing requests
     * overlapping, path-expanded rectangles; reading its lazy source band per rectangle otherwise repeats
     * the much more expensive upstream classification evaluation. The source is read in its preferred
     * tiles so that upstream classification can run concurrently without overlap.
     */
    static Product materializeCloudShadowClassificationProduct(Product sourceProduct) {
        return materializeIntFlagBandProduct(sourceProduct, "pixel_classif_flags", "cloud_shadow_flags");
    }

    static Product materializeCloudShadowPostFlags(Product sourceProduct) {
        return materializeIntFlagBandProduct(sourceProduct, BAND_NAME_CLOUD_SHADOW, "cloud_shadow_post_flags");
    }


    private static Product materializeIntFlagBandProduct(Product sourceProduct, String bandName, String nameSuffix) {
        final Band sourceBand = sourceProduct.getBand(bandName);
        if (sourceBand == null) {
            throw new OperatorException("Missing cloud-shadow flag band '" + bandName + "'.");
        }
        if (sourceBand.getDataType() != ProductData.TYPE_INT32) {
            throw new OperatorException("Cloud-shadow flag band '" + bandName + "' must be INT32.");
        }

        final int width = sourceProduct.getSceneRasterWidth();
        final int height = sourceProduct.getSceneRasterHeight();
        final int[] samples = new int[width * height];
        final Dimension tileSize = sourceProduct.getPreferredTileSize();
        final int tileWidth = tileSize != null && tileSize.width > 0 ? tileSize.width : 512;
        final int tileHeight = tileSize != null && tileSize.height > 0 ? tileSize.height : 512;
        final int tileCountX = (width + tileWidth - 1) / tileWidth;
        final int tileCountY = (height + tileHeight - 1) / tileHeight;
        final int tileCount = tileCountX * tileCountY;
        final int parallelism = Math.max(1, Math.min(tileCount,
                Integer.getInteger("snap.parallelism", Runtime.getRuntime().availableProcessors())));
        final ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        final List<Future<?>> futures = new ArrayList<>(tileCount);
        try {
            for (int y = 0; y < height; y += tileHeight) {
                final int sourceY = y;
                final int readHeight = Math.min(tileHeight, height - sourceY);
                for (int x = 0; x < width; x += tileWidth) {
                    final int sourceX = x;
                    final int readWidth = Math.min(tileWidth, width - sourceX);
                    futures.add(executor.submit(() -> {
                        try {
                            readFlagTile(sourceBand, sourceX, sourceY, readWidth, readHeight, width, samples);
                        } catch (IOException e) {
                            throw new OperatorException("Failed to read cloud-shadow flag band '" + bandName + "'.", e);
                        }
                    }));
                }
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OperatorException("Interrupted while materializing cloud-shadow flag band '" + bandName + "'.", e);
        } catch (ExecutionException e) {
            throw new OperatorException("Failed to materialize cloud-shadow flag band '" + bandName + "'.", e.getCause());
        } finally {
            executor.shutdownNow();
        }

        final Product materializedProduct = new Product(sourceProduct.getName() + "_" + nameSuffix,
                sourceProduct.getProductType(), width, height);
        ProductUtils.copyGeoCoding(sourceProduct, materializedProduct);
        final Band materializedBand = materializedProduct.addBand(sourceBand.getName(), sourceBand.getDataType());
        materializedBand.setRasterData(ProductData.createInstance(samples));
        return materializedProduct;
    }

    private static void readFlagTile(Band sourceBand, int sourceX, int sourceY, int readWidth, int readHeight,
                                     int targetWidth, int[] targetSamples) throws IOException {
        final int[] tileSamples = sourceBand.readPixels(sourceX, sourceY, readWidth, readHeight,
                new int[readWidth * readHeight], ProgressMonitor.NULL);
        for (int row = 0; row < readHeight; row++) {
            System.arraycopy(tileSamples, row * readWidth, targetSamples,
                    (sourceY + row) * targetWidth + sourceX, readWidth);
        }
    }

    private static long profileStart() {
        return PROFILE ? System.nanoTime() : 0L;
    }

    private static void logProfileDuration(String stage, long startNanos) {
        if (PROFILE) {
            LOGGER.fine(String.format("S2 cloud-shadow setup profile: %s=%.3fs", stage,
                    (System.nanoTime() - startNanos) / 1.0e9));
        }
    }

    private float getGeometryMean(Product classificationProduct, String rdnName) {
        // the author of these lines is aware that at no point the mean is computed,
        // for historic reasons we will stick to the name, though
        RasterDataNode node = classificationProduct.getRasterDataNode(rdnName);
        float mean = getRasterNodeValueAtCenter(node, classificationProduct.getSceneRasterWidth(),
                classificationProduct.getSceneRasterHeight());
        if (Float.isNaN(mean)) {
            mean = (float) new StxFactory().create(node, ProgressMonitor.NULL).getMedian();
        }
        return mean;
    }

    private float getRasterNodeValueAtCenter(RasterDataNode var, int width, int height) {
        return var.getSampleFloat((int) (0.5 * width), (int) (0.5 * height));
    }

    private float convertToApparentSunAzimuth(float sunAzimuthMean, float viewZenithMean, float viewAzimuthMean) {
        // here: cloud path is calculated for center pixel sunZenith and sunAzimuth.
        // after correction of sun azimuth angle into apparent sun azimuth angle.
        // Due to projection of the cloud at view_zenith>0 the position of the cloud becomes distorted.
        // The true position still causes the shadow - and it cannot be determined without the cloud top height.
        // So instead, the apparent sun azimuth angle is calculated and used to find the cloudShadowRelativePath.

        double diff_phi = sunAzimuthMean - viewAzimuthMean;
        if (diff_phi < 0) diff_phi = 180 + diff_phi;
        if (diff_phi > 90) diff_phi = diff_phi - 90;
        diff_phi = diff_phi * Math.tan(viewZenithMean * MathUtils.DTOR);
        if (viewAzimuthMean > 180) diff_phi = -1. * diff_phi;
        return (float) (sunAzimuthMean + diff_phi);
    }

     private Product[] getInternalSourceProducts(int resolution) {
        if (resolution == 60) {
            return new Product[]{s2ClassifProduct, s2BandsProduct};
        }

        // Keep shadow detection tied to the classification delivered at the input resolution. Reclassifying
        // resampled reflectances here is expensive and can produce a different cloud population.
        final Product resampledClassifProduct = resampleForCloudShadow(
                createCloudShadowResamplingSource(s2ClassifProduct, l1cProduct, "pixel_classif_flags"));
        final Product resampledBandsProduct = resampleForCloudShadow(
                createCloudShadowResamplingSource(s2BandsProduct, l1cProduct, "B8A", "B3",
                        S2IdepixConstants.ELEVATION_BAND_NAME,
                        S2IdepixConstants.SUN_ZENITH_BAND_NAME, S2IdepixConstants.SUN_AZIMUTH_BAND_NAME,
                        S2IdepixConstants.VIEW_ZENITH_BAND_NAME, S2IdepixConstants.VIEW_AZIMUTH_BAND_NAME));
        return new Product[]{resampledClassifProduct, resampledBandsProduct};
    }

    /** A lazy internal view containing only required bands and their valid-pixel dependencies. */
    static Product createCloudShadowResamplingSource(Product source, Product original, String... bandNames) {
        Product view = new Product(source.getName() + "_shadow_source", source.getProductType(),
                source.getSceneRasterWidth(), source.getSceneRasterHeight());
        ProductUtils.copyGeoCoding(source, view);
        view.setPreferredTileSize(source.getPreferredTileSize());
        view.setStartTime(source.getStartTime());
        view.setEndTime(source.getEndTime());
        for (String name : bandNames) {
            Band band = source.getBand(name);
            if (band == null) {
                throw new OperatorException("Missing cloud-shadow source band '" + name + "'.");
            }
            copyResamplingRaster(band, source, original, view);
        }
        return view;
    }

    private static void copyResamplingRaster(RasterDataNode raster, Product source, Product original, Product view) {
        if (view.getRasterDataNode(raster.getName()) != null) {
            return; // Also breaks self references and cycles in validity expressions.
        }
        if (raster instanceof Band) {
            ProductUtils.copyBand(raster.getName(), raster.getProduct(), view, true);
        } else if (raster instanceof TiePointGrid) {
            view.addTiePointGrid(((TiePointGrid) raster).cloneTiePointGrid());
        } else {
            throw new OperatorException("Unsupported cloud-shadow validity raster '" + raster.getName() + "'.");
        }
        String expression = raster.getValidPixelExpression();
        if (expression == null || expression.trim().isEmpty()) {
            return;
        }
        RasterDataNode[] references;
        try {
            references = BandArithmetic.getRefRasters(expression, raster.getProduct());
        } catch (ParseException incompleteIntermediateProduct) {
            // Recent S2 readers attach validity expressions referencing quality bands which the
            // classifier does not carry forward. Resolve them against the untouched L1C product.
            try {
                references = BandArithmetic.getRefRasters(expression, original);
            } catch (ParseException missingOriginalDependency) {
                throw new OperatorException("Cannot resolve validity dependencies for cloud-shadow band '" +
                        raster.getName() + "': " + expression, missingOriginalDependency);
            }
        }
        for (RasterDataNode reference : references) {
            RasterDataNode preferred = source.getRasterDataNode(reference.getName());
            copyResamplingRaster(preferred != null ? preferred : reference, source, original, view);
        }
    }

    private Product resampleForCloudShadow(Product sourceProduct) {
        final HashMap<String, Product> resamplingInput = new HashMap<>();
        resamplingInput.put("sourceProduct", sourceProduct);
        return GPF.createProduct("Resample", createCloudShadowResamplingParameters(), resamplingInput);
    }

    static Map<String, Object> createCloudShadowResamplingParameters() {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("upsampling", "Nearest");
        parameters.put("downsampling", "Mean");
        parameters.put("flagDownsampling", "FlagOr");
        parameters.put("targetResolution", 60);
        return parameters;
    }

    private Product prepareTargetProduct(int resolution, Product postProcessedProduct) {
        if (resolution == 60) {
            return postProcessedProduct;
        }

        HashMap<String, Product> resamplingInput = new HashMap<>();
        resamplingInput.put("sourceProduct", postProcessedProduct);
        Map<String, Object> resamplingParams = new HashMap<>();
        resamplingParams.put("upsampling", "Nearest");
        resamplingParams.put("downsampling", "Mean");
        resamplingParams.put("flagDownsampling", "FlagOr");
        resamplingParams.put("targetResolution", resolution);
        return GPF.createProduct("Resample", resamplingParams, resamplingInput);
    }

    private int chooseBestOffset(int[] bestOffset) {
        int NCloudWater = 0;
        int NCloudLand = 0;
        int out;
        if (NCloudOverWater.size() > 0) {
            for (int index : NCloudOverWater.keySet()) {
                NCloudWater += NCloudOverWater.get(index);
            }
        }
        if (NCloudOverLand.size() > 0) {
            for (int index : NCloudOverLand.keySet()) {
                NCloudLand += NCloudOverLand.get(index);
            }
        }
        int Nall = NCloudLand + NCloudWater;
        float relCloudLand = (float) NCloudLand / Nall;
        float relCloudWater = (float) NCloudWater / Nall;
        if (relCloudLand > 2 * relCloudWater) {
            out = bestOffset[1];
        } else if (relCloudWater > 2 * relCloudLand) {
            out = bestOffset[2];
        } else out = bestOffset[0];
        return out;
    }

    private int[] findOverallMinimumReflectance() {
        // catch cases of tiles completely invalid are skipped
        if (meanReflPerTile.keySet().size() == 0) {
            return new int[3];
        }
        // we need to account for that not all mean values in meanReflPerTile are of the same length
        int pathLength = 0;
        for (double[][] meanRefls : meanReflPerTile.values()) {
            pathLength = Math.max(pathLength, meanRefls[0].length);
        }
        double[][] scaledTotalReflectance = new double[3][pathLength];
        for (int j = 0; j < 3; j++) {
            /*Checking the meanReflPerTile:
                - if it has no relative minimum other than the first or the last value, it is excluded.
                - if it contains NaNs, it is excluded.
                Exclusion works by setting values to NaN.
            */
            for (int key : meanReflPerTile.keySet()) {
                double[][] meanValues = meanReflPerTile.get(key);
                boolean exclude = false;
                List<Integer> relativeMinimum = indecesRelativMaxInArray(meanValues[j]);
                if (relativeMinimum.contains(0)) relativeMinimum.remove(relativeMinimum.indexOf(0));
                if (relativeMinimum.contains(meanValues[j].length - 1))
                    relativeMinimum.remove(relativeMinimum.indexOf(meanValues[j].length - 1));

                //smallest relative minimum is in second part of the path -> exclude
                if (relativeMinimum.indexOf(0) > meanValues[j].length / 2.) exclude = true;
                if (relativeMinimum.size() == 0) exclude = true;
                if (exclude) {
                    Arrays.fill(meanValues[j], Double.NaN);
                }
            }
            //Finding the minimum in brightness in the scaled mean function.
            for (int key : meanReflPerTile.keySet()) {
                double[][] meanValues = meanReflPerTile.get(key);
                double[] maxValue = new double[3];
                for (int i = 0; i < meanValues[j].length; i++) {
                    if (!Double.isNaN(meanValues[j][i])) {
                        if (meanValues[j][i] > maxValue[j]) {
                            maxValue[j] = meanValues[j][i];
                        }
                    }
                }
                for (int i = 0; i < meanValues[j].length; i++) {
                    if (!Double.isNaN(meanValues[j][i]) && maxValue[j] > 0) {
                        scaledTotalReflectance[j][i] += meanValues[j][i] / maxValue[j];
                    }
                }
            }
        }
        int[] offset = new int[3];
        for (int j = 0; j < 3; j++) {
            List<Integer> test = indecesRelativMaxInArray(scaledTotalReflectance[j]);
            if (test.contains(0)) test.remove(test.indexOf(0));
            if (test.contains(scaledTotalReflectance[j].length - 1))
                test.remove(test.indexOf(scaledTotalReflectance[j].length - 1));

            if (test.size() > 0) {
                offset[j] = test.get(0);
            }
        }
        return offset;
    }

    private List<Integer> indecesRelativMaxInArray(double[] x) {
        int lx = x.length;
        List<Integer> ID = new ArrayList<>();
        boolean valid = true;
        int i = 0;
        while (i < lx && valid) {
            if (Double.isNaN(x[i])) valid = false;
            i++;
        }
        if (lx == 0) {
            LOGGER.fine("indecesRelativMaxInArray x.length=" + lx);
        } else if (lx == 1) {
            LOGGER.fine("indecesRelativMaxInArray x.length=" + lx);
            ID.add(0);
        } else if (valid) {
            double fac = -1.;

            if (fac * x[0] > fac * x[1]) ID.add(0);
            if (fac * x[lx - 1] > fac * x[lx - 2]) ID.add(lx - 1);

            for (i = 1; i < lx - 1; i++) {
                if (fac * x[i] > fac * x[i - 1] && fac * x[i] > fac * x[i + 1]) ID.add(i);
            }
        } else {
            ID.add(0);
            ID.add(lx - 1);
        }

        return ID;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(S2IdepixCloudShadowOp.class);
        }
    }

}
