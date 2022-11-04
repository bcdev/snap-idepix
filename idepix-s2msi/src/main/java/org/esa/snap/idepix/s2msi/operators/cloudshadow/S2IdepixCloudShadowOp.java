package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import com.bc.ceres.core.ProgressMonitor;
import com.sun.media.jai.util.SunTileCache;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.datamodel.StxFactory;
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
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;
import org.opengis.referencing.operation.MathTransform;

import javax.media.jai.CachedTile;
import javax.media.jai.JAI;
import javax.media.jai.PointOpImage;
import javax.media.jai.TileCache;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observer;
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

    @Parameter(description = "The digital elevation model.", defaultValue = "SRTM 3Sec", label = "Digital Elevation Model")
    private String demName = "SRTM 3Sec";

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

        int sourceResolution = determineSourceResolution(l1cProduct);

        Product[] internalSourceProducts = getInternalSourceProducts(sourceResolution);

        Product classificationProduct = internalSourceProducts[0];
        final Product s2BandsProduct = internalSourceProducts[1];
        float sunZenithMean = getGeometryMean(s2BandsProduct, S2IdepixConstants.SUN_ZENITH_BAND_NAME);
        float sunAzimuthMean = getGeometryMean(s2BandsProduct, S2IdepixConstants.SUN_AZIMUTH_BAND_NAME);
        float viewZenithMean = getGeometryMean(s2BandsProduct, S2IdepixConstants.VIEW_ZENITH_BAND_NAME);
        float viewAzimuthMean = getGeometryMean(s2BandsProduct, S2IdepixConstants.VIEW_AZIMUTH_BAND_NAME);
        sunAzimuthMean = convertToApparentSunAzimuth(sunAzimuthMean, viewZenithMean, viewAzimuthMean);

        HashMap<String, Product> preInput = new HashMap<>();
        preInput.put("s2ClassifProduct", classificationProduct);
        preInput.put("s2BandsProduct", s2BandsProduct);
        Map<String, Object> preParams = new HashMap<>();
        preParams.put("sunZenithMean", sunZenithMean);
        preParams.put("sunAzimuthMean", sunAzimuthMean);

        //todo: test resolution of granule. Resample necessary bands to 60m. calculate cloud shadow on 60m.
        //todo: let mountain shadow benefit from higher resolution in DEM. Adjust sun zenith according to smoothing.

        //Preprocessing:
        // No flags are created, only statistics generated to find the best offset along the illumination path.
        final String operatorAlias = OperatorSpi.getOperatorAlias(S2IdepixPreCloudShadowOp.class);
        final S2IdepixPreCloudShadowOp cloudShadowPreProcessingOperator =
                (S2IdepixPreCloudShadowOp) GPF.getDefaultInstance().createOperator(operatorAlias, preParams, preInput, null);

        //trigger computation of all tiles
        LOGGER.info("Executing Cloud Shadow Preprocessing");
        if (tileCache instanceof SunTileCache) {
            ((SunTileCache) tileCache).enableDiagnostics();
            ((SunTileCache) tileCache).addObserver(observer);
        }
        final OperatorExecutor operatorExecutor = OperatorExecutor.create(cloudShadowPreProcessingOperator);
        operatorExecutor.execute(ProgressMonitor.NULL);
        if (tileCache instanceof SunTileCache) {
            ((SunTileCache) tileCache).deleteObserver(observer);
        }
        LOGGER.info("Executed Cloud Shadow Preprocessing");

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
        postInput.put("s2ClassifProduct", classificationProduct);
        postInput.put("s2BandsProduct", s2BandsProduct);
        //put in here the input products that are required by the post-processing operator
        Map<String, Object> postParams = new HashMap<>();
        postParams.put("computeMountainShadow", computeMountainShadow);
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

        setTargetProduct(prepareTargetProduct(sourceResolution, postProduct));
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

    private int determineSourceResolution(Product product) throws OperatorException {
        final GeoCoding sceneGeoCoding = product.getSceneGeoCoding();
        if (sceneGeoCoding instanceof CrsGeoCoding) {
            final MathTransform imageToMapTransform = sceneGeoCoding.getImageToMapTransform();
            if (imageToMapTransform instanceof AffineTransform) {
                return (int) ((AffineTransform) imageToMapTransform).getScaleX();
            }
        } else {
            return (int) S2IdepixUtils.determineResolution(product);
        }
        throw new OperatorException("Invalid product");
    }

    private Product[] getInternalSourceProducts(int resolution) {
        if (resolution == 60) {
            return new Product[]{s2ClassifProduct, s2BandsProduct};
        }

        HashMap<String, Product> resamplingInput = new HashMap<>();
        resamplingInput.put("sourceProduct", l1cProduct);
        Map<String, Object> resamplingParams = new HashMap<>();
        resamplingParams.put("upsampling", "Nearest");
        resamplingParams.put("downsampling", "First");
        resamplingParams.put("targetResolution", 60);
        Product resampledProduct = GPF.createProduct("Resample", resamplingParams, resamplingInput);

        HashMap<String, Product> classificationInput = new HashMap<>();
        classificationInput.put("sourceProduct", resampledProduct);
        Map<String, Object> classificationParams = new HashMap<>();
        classificationParams.put("computeMountainShadow", false);
        classificationParams.put("computeCloudShadow", false);
        classificationParams.put("computeCloudBuffer", computeCloudBuffer);
        classificationParams.put("cloudBufferWidth", cloudBufferWidth);
        classificationParams.put("computeCloudBufferForCloudAmbiguous", computeCloudBufferForCloudAmbiguous);
        classificationParams.put("cwThresh", cwThresh);
        classificationParams.put("gclThresh", gclThresh);
        classificationParams.put("clThresh", clThresh);
        classificationParams.put("demName", demName);

        Product resampledClassifProduct = GPF.createProduct("Idepix.S2", classificationParams, classificationInput);
        return new Product[]{resampledClassifProduct, resampledClassifProduct};
    }

    private Product prepareTargetProduct(int resolution, Product postProcessedProduct) {
        if (resolution == 60) {
            return postProcessedProduct;
        }

        HashMap<String, Product> resamplingInput = new HashMap<>();
        resamplingInput.put("sourceProduct", postProcessedProduct);
        Map<String, Object> resamplingParams = new HashMap<>();
        resamplingParams.put("upsampling", "Nearest");
        resamplingParams.put("downsampling", "First");
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
