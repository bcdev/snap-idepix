/*
 * Copyright (C) 2022 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.idepix.aatsr;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.snap.core.dataio.geocoding.ComponentGeoCoding;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.image.ImageManager;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.ImageUtils;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import javax.imageio.ImageIO;
import javax.media.jai.BorderExtender;
import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.KernelJAI;
import javax.media.jai.OpImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ClampDescriptor;
import javax.media.jai.operator.ConvolveDescriptor;
import javax.media.jai.operator.CropDescriptor;
import javax.media.jai.operator.DivideByConstDescriptor;
import javax.media.jai.operator.ExtremaDescriptor;
import javax.media.jai.operator.FormatDescriptor;
import javax.media.jai.operator.MosaicDescriptor;
import javax.media.jai.operator.MultiplyConstDescriptor;
import javax.media.jai.operator.ScaleDescriptor;
import javax.media.jai.operator.SubtractConstDescriptor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.IntStream;

/**
 * The IdePix pixel classification operator for AATSR products (4th repro).
 */
@OperatorMetadata(alias = "Idepix.Aatsr",
        category = "Optical/Preprocessing/Masking",
        version = "1.0",
        authors = "Dagmar Mueller, Marco Peters",
        copyright = "(c) 2022 by Brockmann Consult",
        description = "Pixel identification and classification for AATSR 4th repro data.")
public class IdepixAatsrOp extends Operator {

    private static final boolean DEBUG = true;
    private final static int SPATIAL_RESOLUTION = 1000; // in meter // better to get it from product

    @SourceProduct(label = "AATSR L1b product",
            description = "The AATSR L1b source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Parameter(label = "Copy source bands", defaultValue = "false")
    private boolean copySourceBands;

    @Parameter(label = "Assumed cloud top height", defaultValue = "6000")
    private int cloudTopHeight;
    private Mask startSearchMask;
    private Rectangle dayTimeROI;
    private RenderedOp orientationImage;
    private double minSurfaceAltitude;
    private int maxShadowDistance;
    private Band idepixFlagBand;

    // overall parameters

    @Override
    public void initialize() throws OperatorException {
        // 1)
        validate(sourceProduct);

        // 2) create TargetProduct
        final String targetProductName = sourceProduct.getName() + "_idepix";
        targetProduct = createCompatibleProduct(sourceProduct, targetProductName);

        // init ComponentGeoCoding is necessary for SNAP8.x, can be removed for SNAP9
        final GeoCoding sceneGeoCoding = sourceProduct.getSceneGeoCoding();
        if (sceneGeoCoding instanceof ComponentGeoCoding) {
            ComponentGeoCoding compGC = (ComponentGeoCoding) sceneGeoCoding;
            compGC.initialize();
        }

        if (copySourceBands) {
            ProductUtils.copyProductNodes(sourceProduct, targetProduct);
            for (Band band : sourceProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    ProductUtils.copyBand(band.getName(), sourceProduct, targetProduct, true);
                }
            }
        } else {
            ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
            targetProduct.setStartTime(sourceProduct.getStartTime());
            targetProduct.setEndTime(sourceProduct.getEndTime());
        }

        // 2.1) copy source bands (todo - which source bands to include?)
        // 2.2) create flag band compatible with other IdePix processors but only
        idepixFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        idepixFlagBand.setSampleCoding(flagCoding);
        IdepixFlagCoding.setupDefaultClassifBitmask(targetProduct);
    }

    @Override
    public void doExecute(ProgressMonitor pm) throws OperatorException {
        pm.beginTask("Executing cloud shadow detection...", 10);
        try {
            final int sceneWidth = sourceProduct.getSceneRasterWidth();
            final int sceneHeight = sourceProduct.getSceneRasterHeight();

            // 1) detect day time area where cloud shadow can occur
            dayTimeROI = getDayTimeArea(sourceProduct);

            // 2) create north-corrected orientation image
            orientationImage = computeOrientationImage(sourceProduct);
//            writeDebugImage(orientationImage, "orientationImage.png");

            // 3) create cloudMaskImage and landMaskImage
            // as alternative the bayesian_in and confidence_in could be used. See TechNote.
            // But currently the bayes_in.no_bayesian_probabilities_available is always set. so it makes no sense to use it.
            final Mask cloudMask = Mask.BandMathsType.create("__cloud_mask", "", sceneWidth, sceneHeight,
                                                             "cloud_in.visible or cloud_in.12_gross_cloud or cloud_in.11_12_thin_cirrus or cloud_in.3_7_12_medium_high",
                                                             Color.white, 0.5f);
            cloudMask.setOwner(sourceProduct);
            final Mask landMask = Mask.BandMathsType.create("__land_mask", "", sceneWidth, sceneHeight,
                                                            "confidence_in.coastline or confidence_in.tidal or confidence_in.land or confidence_in.inland_water",
                                                            Color.green, 0.5f);
            landMask.setOwner(sourceProduct);

//            writeDebugImage(cloudMask.getSourceImage(), "cloud_mask.png");
//            writeDebugImage(landMask.getSourceImage(), "land_mask.png");

            // 4) create startSearchMask using cloudMaskImage, landMaskImage and search radius
            // splitting cloudMask image into 2000 y-pixel slices but only in dayTimeArea
            final List<RenderedImage> convolvedCloudSlices = new ArrayList<>();
            final List<RenderedImage> convolvedLandSlices = new ArrayList<>();
            final List<Rectangle> slices = sliceRect(dayTimeROI, 2000);
            // merge slices
//            final Rectangle remove1 = slices.remove(slices.size() - 2);
//            final Rectangle remove2 = slices.remove(slices.size() - 1);
//            slices.add(remove1.union(remove2));

            convolveLandAndCloudSlices(slices, dayTimeROI, cloudMask, convolvedCloudSlices, landMask, convolvedLandSlices);

            // mosaic the generated slices
            final Rectangle mosaicBounds = new Rectangle(sceneWidth, sceneHeight);
            final Rectangle mosaicTileSize = new Rectangle(cloudMask.getSourceImage().getTileWidth(), cloudMask.getSourceImage().getTileHeight());
            final RenderedImage mosaicCloudImage = createMosaic(slices, convolvedCloudSlices, mosaicBounds, mosaicTileSize);
            final RenderedImage mosaicLandImage = createMosaic(slices, convolvedLandSlices, mosaicBounds, mosaicTileSize);
            writeDebugImage(mosaicCloudImage, "mosaicCloudImage.png");
            writeDebugImage(mosaicLandImage, "mosaicLandImage.png");


            // create temporary product to compute the start-search-mask
            final Product tempMaskProduct = new Product("temp", "tempType", sceneWidth, sceneHeight);
            final Band convCloudMaskBand = new Band("__convCloudMask", ProductData.TYPE_FLOAT32, sceneWidth, sceneHeight);
            convCloudMaskBand.setSourceImage(mosaicCloudImage);
            tempMaskProduct.addBand(convCloudMaskBand);
            final Band convLandMaskBand = new Band("__convLandMask", ProductData.TYPE_FLOAT32, sceneWidth, sceneHeight);
            convLandMaskBand.setSourceImage(mosaicLandImage);
            tempMaskProduct.addBand(convLandMaskBand);

            // use band math to combine mosaics to startSearchMask
            startSearchMask = Mask.BandMathsType.create("__startSearch", "", sceneWidth, sceneHeight,
                                                        "__convCloudMask > 0.001 && __convCloudMask < 0.998 && __convLandMask > 0.001",
                                                        Color.BLUE, 0.0);
            tempMaskProduct.addBand(startSearchMask);
            writeDebugImage(startSearchMask.getSourceImage(), "startSearchMask.png");

            final RasterDataNode elevationRaster = sourceProduct.getRasterDataNode("elevation_in");
            final RenderedOp croppedElev = CropDescriptor.create(elevationRaster.getSourceImage(), (float) dayTimeROI.x, (float) dayTimeROI.y, (float) dayTimeROI.width, (float) dayTimeROI.height, null);
            final RenderedOp clampedElev = ClampDescriptor.create(croppedElev, new double[]{0}, new double[]{Short.MAX_VALUE}, null);
            final RenderedOp extrema = ExtremaDescriptor.create(clampedElev, null, 10, 10, Boolean.FALSE, 1, null);
            minSurfaceAltitude = ((double[]) extrema.getProperty("minimum"))[0];

            pm.worked(1);

            doShadowDetectionPerSlice(cloudMask, landMask, slices, new SubProgressMonitor(pm, 9));
            // force creation of source image to prevent creation of new image by GPF
            idepixFlagBand.getSourceImage();
        } catch (IOException e) {
            throw new OperatorException("Could not read source data", e);
        } finally {
            pm.done();
        }
    }

    private void doShadowDetectionPerSlice(Mask cloudMask, Mask landMask, List<Rectangle> slices, ProgressMonitor pm) {
        final int shadowValue = BitSetter.setFlag(0, IdepixConstants.IDEPIX_CLOUD_SHADOW);
        idepixFlagBand.setRasterData(idepixFlagBand.createCompatibleRasterData());

        final ExecutorService executorService = Executors.newFixedThreadPool((int) (Runtime.getRuntime().availableProcessors() * 0.8)); // Use 80% use cores; is this good?

        final Rectangle extendedDayTimeRoi = (Rectangle) dayTimeROI.clone();
        extendedDayTimeRoi.grow(0, maxShadowDistance);
        final Tile elevation = getSourceTile(sourceProduct.getRasterDataNode("elevation_in"), extendedDayTimeRoi);
        final Tile cloudMaskData = getSourceTile(cloudMask, extendedDayTimeRoi);

        final ArrayList<Callable<Object>> tasks = new ArrayList<>();
        for (Rectangle slice : slices) {
            tasks.add(() -> {
                final Tile sza = getSourceTile(sourceProduct.getRasterDataNode("solar_zenith_tn"), slice);
                final Tile saa = getSourceTile(sourceProduct.getRasterDataNode("solar_azimuth_tn"), slice);
                final Tile oza = getSourceTile(sourceProduct.getRasterDataNode("sat_zenith_tn"), slice);
                final Tile x_tx = getSourceTile(sourceProduct.getRasterDataNode("x_tx"), slice);
                final Raster orientation = orientationImage.getData(slice);
                final Tile landMaskData = getSourceTile(landMask, slice);
                final Raster startData = ((RenderedImage) startSearchMask.getSourceImage()).getData(slice);

                for (int y = slice.y; y < slice.y + slice.height; ++y) {
                    for (int x = slice.x; x < slice.x + slice.width; ++x) {
                        if (startData.getSample(x, y, 0) > 0 && cloudMaskData.getSampleInt(x, y) > 0) {
                            final PathAndHeightInfo pathAndHeightInfo = calcPathAndTheoreticalHeight(sza.getSampleFloat(x, y), saa.getSampleFloat(x, y),
                                                                                                     oza.getSampleFloat(x, y), x_tx.getSampleFloat(x, y),
                                                                                                     orientation.getSampleFloat(x, y, 0),
                                                                                                     SPATIAL_RESOLUTION,
                                                                                                     cloudTopHeight,
                                                                                                     minSurfaceAltitude);

                            final int[][] indexArray = pathAndHeightInfo.illuPathSteps.clone();
                            for (int i = 0; i < indexArray.length; i++) {
                                indexArray[i][0] += x;
                                indexArray[i][1] += y;
                            }

                            // find cloud free positions along the search path:
                            final Boolean[] id = Arrays.stream(indexArray).parallel().map(
                                    ints -> ints[0] >= 0 && ints[0] < slice.x + slice.width &&
                                            ints[1] >= 0 && ints[1] < slice.y + slice.height).toArray(Boolean[]::new);

                            final int sum = Arrays.stream(id).parallel().mapToInt(aBoolean -> aBoolean ? 1 : 0).sum();
                            if (sum > 3) { // path positions

                                final int[][] curIndexArray = IntStream.range(0, indexArray.length).parallel().filter(i -> id[i]).mapToObj(i -> indexArray[i]).toArray(int[][]::new);
                                final double[] illuPathHeight = pathAndHeightInfo.illuPathHeight;
                                final double[] baseHeightArray = IntStream.range(0, curIndexArray.length).parallel().filter(i -> id[i]).mapToDouble(i -> illuPathHeight[i]).toArray();
                                final double[] elevPath = Arrays.stream(curIndexArray).parallel().mapToDouble(index -> elevation.getSampleFloat(index[0], index[1])).toArray();
                                final Boolean[] cloudPath = Arrays.stream(curIndexArray).parallel().map(index -> cloudMaskData.getSampleInt(index[0], index[1]) > 0).toArray(Boolean[]::new);

                                final Boolean[] id2 = IntStream.range(0, baseHeightArray.length).parallel().mapToObj(value -> (Math.abs(baseHeightArray[value] - elevPath[value]) < pathAndHeightInfo.threshHeight) && !cloudPath[value]).toArray(Boolean[]::new);

                                IntStream.range(0, baseHeightArray.length).parallel().filter(i -> id2[i]).forEach(
                                        i -> idepixFlagBand.setPixelInt(curIndexArray[i][0], curIndexArray[i][1], shadowValue));
                            }
                        }

                        int flagValue = idepixFlagBand.getPixelInt(x, y);
                        if (cloudMaskData.getSampleInt(x, y) > 0) {
                            flagValue = BitSetter.setFlag(flagValue, IdepixConstants.IDEPIX_CLOUD);
                        }
                        if (landMaskData.getSampleInt(x, y) > 0) {
                            flagValue = BitSetter.setFlag(flagValue, IdepixConstants.IDEPIX_LAND);
                        }

                        idepixFlagBand.setPixelInt(x, y, flagValue);
                    }
                }
                return slice;
            });
        }

        try {
            final List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executorService.submit(task));
            }

            pm.beginTask("Detecting clouds shadows...", futures.size());
            try {
                executorService.shutdown();
                int stillRunning = futures.size();
                while (!executorService.isTerminated()) {
                    synchronized (cloudMask) {
                        cloudMask.wait(100);
                    }

                    int numRunning = numRunningFutures(futures);
                    pm.worked(stillRunning - numRunning);
                    stillRunning = numRunning;
                }
            } finally {
                pm.done();
            }
        } catch (InterruptedException e) {
            throw new OperatorException(e);
        }

    }

    private int numRunningFutures(List<Future<Object>> futures) throws InterruptedException {
        int numRunning = 0;
        for (int i = 0; i < futures.size(); i++) {
            Future<Object> future = futures.get(i);
            numRunning = numRunning + (future.isDone() ? 0 : 1);
            if (future.isDone()) {
                final Future<Object> doneFuture = futures.remove(i);
                try {
                    doneFuture.get();
                } catch (ExecutionException e) {
                    throw new OperatorException("Error during calculation of cloud shadow", e);
                }
            }
        }
        return numRunning;
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        // We need to place the data into the targetTile even though it is already in the raster.
        // Otherwise, the data is not written to the product by GPF.
        final Rectangle rect = targetTile.getRectangle();
        final int[] pixels = new int[rect.width * rect.height];
        final int[] samplesInt = idepixFlagBand.getPixels(rect.x, rect.y, rect.width, rect.height, pixels);
        targetTile.setSamples(samplesInt);
    }

    @SuppressWarnings("SameParameterValue")
    static PathAndHeightInfo calcPathAndTheoreticalHeight(double sza, double saa, double oza, double xtx, double orientation, int spatialRes, int maxObjectAltitude, double minSurfaceAltitude) {
        sza = correctSzaForOzaInfluence(sza, saa, oza, xtx, orientation);

        final double shadowAngle = ((saa - orientation) + 180) * Math.PI / 180 - Math.PI / 2;
        double cosSaa = Math.cos(shadowAngle);
        double sinSaa = Math.sin(shadowAngle);

        double deltaProjX = ((maxObjectAltitude - minSurfaceAltitude) * Math.tan(sza * Math.PI / 180) * cosSaa) / spatialRes;
        double deltaProjY = ((maxObjectAltitude - minSurfaceAltitude) * Math.tan(sza * Math.PI / 180) * sinSaa) / spatialRes;

        double x0 = 0;
        double y0 = 0;
        double x1 = x0 + deltaProjX + Math.signum(deltaProjX) * 1.5;
        double y1 = y0 + deltaProjY + Math.signum(deltaProjY) * 1.5;

        // create index steps
        // Path touches which pixels?
        // setup all pixel centers from x0/y0 to x1/y1.
        // calculate distance between pixel center and line (X0, X1)
        // all distances below/equal sqrt(2*0.5^2): the pixel is touched by the line and a potential shadow pixel.
        double[] xCenters;
        double[] yCenters;
        if (x0 < x1) {
            xCenters = arange(x0 + 0.5, Math.round(x1) + 0.5, 1.0);
        } else {
            xCenters = arange(Math.round(x1) + 0.5, x0 + 0.5, 1.0);
        }
        if (y0 < y1) {
            yCenters = arange(y0 + 0.5, Math.round(y1) + 0.5, 1.0);
        } else {
            yCenters = arange(Math.round(y1) + 0.5, y0 + 0.5, 1.0);
        }

        double[][] distance = new double[xCenters.length][yCenters.length];
        int[][] xIndex = new int[xCenters.length][yCenters.length];
        int[][] yIndex = new int[xCenters.length][yCenters.length];
        int nxCenter = xCenters.length;
        int nyCenter = yCenters.length;

        final double divider = Math.pow(x0 - x1, 2) + Math.pow(y0 - y1, 2);
        for (int i = 0; i < nxCenter; i++) {
            for (int j = 0; j < nyCenter; j++) {
                double r = -((x0 - xCenters[i]) * (x0 - x1) + (y0 - yCenters[j]) * (y0 - y1)) / divider;
                double d = Math.sqrt(Math.pow((x0 - xCenters[i]) + r * (x0 - x1), 2) + Math.pow((y0 - yCenters[j]) + r * (y0 - y1), 2));
                distance[i][j] = d;
                xIndex[i][j] = deltaProjX < 0 ? i - (nxCenter - 1) : i;
                yIndex[i][j] = deltaProjY < 0 ? j - (nyCenter - 1) : j;
            }
        }
        double halfPixelDistance = 0.5 * Math.sqrt(2);
        int[][] id = new int[xCenters.length][yCenters.length];
        for (int x = 0; x < id.length; x++) {
            for (int y = 0; y < id[x].length; y++) {
                id[x][y] = distance[x][y] <= halfPixelDistance ? 1 : 0;
            }
        }
        final int numCoords = Arrays.stream(id).flatMapToInt(Arrays::stream).sum();
        int[][] stepIndex = new int[numCoords][2];
        int s = 0;
        for (int i = 0; i < id.length; i++) {
            for (int j = 0; j < id[i].length; j++) {
                if (id[i][j] == 1) {
                    stepIndex[s++] = new int[]{xIndex[i][j], yIndex[i][j]};
                }
            }
        }

        double[] theoretHeight = new double[stepIndex.length];
        final double tanSza = Math.tan(sza * Math.PI / 180.);
        for (int i = 0; i < theoretHeight.length; i++) {
            theoretHeight[i] = maxObjectAltitude - Math.sqrt(Math.pow(stepIndex[i][0], 2) + Math.pow(stepIndex[i][1], 2)) * spatialRes / tanSza;
        }
        double threshHeight = 1000. / tanSza;

        return new PathAndHeightInfo(stepIndex, theoretHeight, threshHeight);
    }

    @SuppressWarnings("SameParameterValue")
    static double[] arange(double startValue, double endValue, double step) {
        final int numValues = (int) (Math.ceil((endValue - startValue) / step));
        return IntStream.range(0, numValues).mapToDouble(x -> x * step + startValue).toArray();
    }

    private static double correctSzaForOzaInfluence(double sza, double saa, double oza, double xtx, double orientation) {
        if (saa - orientation < 180) {
            sza = xtx < 0 ? correctSzaNegative(sza, oza) : correctSzaPositive(sza, oza);
        } else {
            sza = xtx < 0 ? correctSzaPositive(sza, oza) : correctSzaNegative(sza, oza);
        }
        return sza;
    }

    private static double correctSzaPositive(double sza, double oza) {
        return Math.atan(Math.tan(sza * Math.PI / 180) + Math.tan(oza * Math.PI / 180)) * 180 / Math.PI;
    }

    private static double correctSzaNegative(double sza, double oza) {
        return Math.atan(Math.tan(sza * Math.PI / 180) - Math.tan(oza * Math.PI / 180)) * 180 / Math.PI;
    }

    void validate(Product sourceProduct) throws OperatorException {
        if (!"ENV_AT_1_RBT".equals(sourceProduct.getProductType())) {
            throw new OperatorException("An AATSR product from the 4th reprocessing is needed as input");
        }
        String[] usedRasterNames = {"solar_zenith_tn", "solar_azimuth_tn", "sat_zenith_tn", "latitude_tx", "longitude_tx", "x_tx", "elevation_in", "confidence_in", "cloud_in"};
        for (String usedRasterName : usedRasterNames) {
            if (!sourceProduct.containsRasterDataNode(usedRasterName)) {
                throw new OperatorException(String.format("Missing raster '%s' in source product", usedRasterName));
            }
        }
    }

    /**
     * Returns the daytime area of the scene and where the sun angles are usable
     *
     * @param scene the scene to compute the daytime area
     * @return a rectangle specifying the daytime area with usable sun angles
     */
    private Rectangle getDayTimeArea(Product scene) {
        final int sceneWidth = scene.getSceneRasterWidth();
        final int sceneHeight = scene.getSceneRasterHeight();

        // test sun elevation and find first and last row with SZA<85°, daylight zone
        // day flag is necessary because there can be an area with SZA<85° but it is not marked as DAY.
        final Mask szaMask = Mask.BandMathsType.create("__SZA_DAY_Mask", "", sceneWidth, sceneHeight,
                                                       "solar_zenith_tn * (confidence_in.day ? 1 : NaN) < 85",
                                                       Color.yellow, 0.5f);
        szaMask.setOwner(scene);
        final int[] szaRange0 = detectMaskedPixelRangeInColumn(szaMask, 0);
        final int[] szaRange1 = detectMaskedPixelRangeInColumn(szaMask, sceneWidth - 1);
        // free used memory
        szaMask.setOwner(null);
        szaMask.dispose();

        int szaUpperLimit = Math.max((szaRange0[0]), szaRange1[0]);
        int szaLowerLimit = Math.min((szaRange0[1]), szaRange1[1]);

        return new Rectangle(0, szaUpperLimit, sceneWidth, szaLowerLimit - szaUpperLimit);
    }

    /**
     * Return the min and maximum line number in the where pixels are masked.
     *
     * @param mask   the mask
     * @param column the column to check masked values
     * @return an integer array containing the minimum (index=0) and maximum (index=1)
     */
    static int[] detectMaskedPixelRangeInColumn(Mask mask, int column) {
        final int rasterHeight = mask.getRasterHeight();
        final Raster data = mask.getSourceImage().getData(new Rectangle(column, 0, 1, rasterHeight));
        // masks have byte data type
        final byte[] dataBufferArray = (byte[]) ImageUtils.createDataBufferArray(data.getTransferType(), rasterHeight);
        data.getDataElements(column, 0, 1, rasterHeight, dataBufferArray);
        int[] minMax = new int[2];
        // todo - can be further optimised and parallelized
        for (int i = 0; i < dataBufferArray.length; i++) {
            if (dataBufferArray[i] == -1) {
                minMax[0] = i;
                break;
            }
        }
        for (int i = dataBufferArray.length - 1; i >= 0; i--) {
            if (dataBufferArray[i] == -1) {
                minMax[1] = i;
                break;
            }
        }
        return minMax;
    }

    /**
     * Creates a north-corrected orientation image on a sub-sampled grid. The result is scale back to the original size
     * using bicubic interpolation. The computation is based on 'latitude_tx' and 'longitude_tx'.
     *
     * @param scene the scene to compute the north-corrected orientation image for.
     * @return the north-corrected orientation image
     */
    private RenderedOp computeOrientationImage(Product scene) {
        final RasterDataNode latRaster = scene.getRasterDataNode("latitude_tx"); // todo - better use latitude_in, but this gives strange results
        final RasterDataNode lonRaster = scene.getRasterDataNode("longitude_tx"); // todo - better use longitude_in, but this gives strange results
        final Interpolation nearest = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
        final float downScaleFactor = 1 / 10F;
        final RenderedOp lowResLats = ScaleDescriptor.create(latRaster.getSourceImage(), downScaleFactor, downScaleFactor, 0.0F, 0.0F, nearest, null);
        final RenderedOp lowResLons = ScaleDescriptor.create(lonRaster.getSourceImage(), downScaleFactor, downScaleFactor, 0.0F, 0.0F, nearest, null);
        final OpImage lowResOrientation = new OrientationOpImage(lowResLats, lowResLons);
        final float upScaleWidthFactor = scene.getSceneRasterWidth() / (float) lowResOrientation.getWidth();
        final float upScaleHeightFactor = scene.getSceneRasterHeight() / (float) lowResOrientation.getHeight();
        return ScaleDescriptor.create(lowResOrientation, upScaleWidthFactor, upScaleHeightFactor, 0.0F, 0.0F,
                                      Interpolation.getInstance(Interpolation.INTERP_BICUBIC),
                                      new RenderingHints(JAI.KEY_BORDER_EXTENDER, BorderExtender.createInstance(BorderExtender.BORDER_COPY)));
    }

    private RenderedImage createMosaic(List<Rectangle> slices, List<RenderedImage> imageSlices, Rectangle mosaicBounds, Rectangle mosaicTileSize) {
        double[][] sourceThresholds = new double[slices.size()][1];
        for (int i = 0; i < sourceThresholds.length; i++) {
            sourceThresholds[i][0] = Double.MIN_VALUE;
        }
        final ImageLayout mosaicImageLayout = ImageManager.createSingleBandedImageLayout(DataBuffer.TYPE_FLOAT,
                                                                                         mosaicBounds.width,
                                                                                         mosaicBounds.height,
                                                                                         mosaicTileSize.width, mosaicTileSize.height);
        return MosaicDescriptor.create(imageSlices.toArray(new RenderedImage[0]),
                                       MosaicDescriptor.MOSAIC_TYPE_OVERLAY,
                                       null,
                                       null,
                                       sourceThresholds,
                                       new double[]{0.0},//backgroundValue,
                                       new RenderingHints(JAI.KEY_IMAGE_LAYOUT, mosaicImageLayout));
    }

    private void convolveLandAndCloudSlices(List<Rectangle> slices, Rectangle dayTimeROI, Mask cloudMask, List<RenderedImage> convolvedCloudSlices, Mask landMask, List<RenderedImage> convolvedLandSlices) throws IOException {
        final RasterDataNode sza = sourceProduct.getRasterDataNode("solar_zenith_tn");
        // convolution shall take place with float data type. We need to format the source images of cloudMask and landMask.
        final RenderedImage floatCloudMaskImage = FormatDescriptor.create(cloudMask.getSourceImage(), DataBuffer.TYPE_FLOAT, null);
        final RenderedImage floatLandMaskImage = FormatDescriptor.create(landMask.getSourceImage(), DataBuffer.TYPE_FLOAT, null);
        // convolve
        for (Rectangle slice : slices) {
            if (slice.intersects(dayTimeROI)) {
                // only convolve slices intersecting with dayTimeROI. Areas outside are handled by default background value when creating the mosaic.
                double radius = computeKernelRadiusForSlice(sza, slice);
                System.out.printf(Locale.ENGLISH, "slize[%d], radius[%f]%n", slice.y, radius);
                maxShadowDistance = MathUtils.ceilInt(Math.max(radius, maxShadowDistance));
                final KernelJAI jaiKernel = createJaiKernel(radius, new Dimension(1000, 1000));
                convolveSlice(floatCloudMaskImage, slice, jaiKernel, convolvedCloudSlices, "convCloudImage");
                convolveSlice(floatLandMaskImage, slice, jaiKernel, convolvedLandSlices, "convLandImage");
            }
        }
    }

    @SuppressWarnings("unused")
    private void convolveSlice(RenderedImage sourceImage, Rectangle slice, KernelJAI jaiKernel, List<RenderedImage> convolvedSlices, String debugId) {
        final RenderedOp curSlice = CropDescriptor.create(sourceImage, (float) slice.x, (float) slice.y, (float) slice.width, (float) slice.height, null);
        final RenderedImage convImage = createConvolvedImage(curSlice, jaiKernel);
        convolvedSlices.add(convImage);
        writeDebugImage(convImage, String.format("%s_%d_%dx%d.png", debugId, slice.y, jaiKernel.getWidth(), jaiKernel.getWidth()));
    }

    private double computeKernelRadiusForSlice(RasterDataNode sza, Rectangle slice) throws IOException {
        final float[] szaData = new float[slice.width * slice.height];
        sza.readPixels(slice.x, slice.y, slice.width, slice.height, szaData);
        Arrays.sort(szaData);
        float szaMedian;
        if (szaData.length % 2 == 0) {
            szaMedian = (szaData[szaData.length / 2] + szaData[szaData.length / 2 - 1]) / 2;
        } else {
            szaMedian = szaData[szaData.length / 2];
        }
        return Math.abs(cloudTopHeight * Math.tan(szaMedian * Math.PI / 180.0));
    }

    @SuppressWarnings("SameParameterValue")
    static List<Rectangle> sliceRect(Rectangle sourceBounds, int sliceHeight) {
        List<Rectangle> slices = new ArrayList<>();
        for (int i = sourceBounds.y; i < sourceBounds.y + sourceBounds.height; i += sliceHeight) {
            Rectangle curRect = new Rectangle(0, i, sourceBounds.width, sliceHeight);
            curRect = sourceBounds.intersection(curRect);
            if (!curRect.isEmpty()) {
                slices.add(curRect);
            }
        }
        return slices;
    }

    private RenderedImage createConvolvedImage(RenderedImage sourceImage, KernelJAI jaiKernel) {
        final RenderedOp convImage = ConvolveDescriptor.create(sourceImage, jaiKernel, new RenderingHints(JAI.KEY_BORDER_EXTENDER, BorderExtender.createInstance(BorderExtender.BORDER_COPY)));
        // due to convolution value can be higher than 255, so we clamp
        final RenderedOp clampImage = ClampDescriptor.create(convImage, new double[]{0}, new double[]{255}, null);
        // normalise to [0,1.0] value range
        return DivideByConstDescriptor.create(clampImage, new double[]{255}, null);
    }

    private KernelJAI createJaiKernel(double radius, Dimension spacing) {
        Dimension kernelHalfDim = new Dimension(MathUtils.ceilInt(radius / spacing.width),
                                                MathUtils.ceilInt(radius / spacing.height));
        int[] xDist = createDistanceArray(kernelHalfDim.width, spacing.width);
        int[] yDist = createDistanceArray(kernelHalfDim.height, spacing.height);
        return createKernelData(xDist, yDist, radius);
    }

    static int[] createDistanceArray(int kernelHalfDim, int spacing) {
        int[] xDist = new int[kernelHalfDim * 2 + 1];
        for (int i = 0; i < xDist.length; i++) {
            xDist[i] = -kernelHalfDim * spacing + i * spacing;
        }
        return xDist;
    }

    private KernelJAI createKernelData(final int[] xDist, final int[] yDist, double radius) {
        final double[][] kernel = new double[yDist.length][xDist.length];
        for (int y = 0; y < kernel.length; y++) {
            double[] rowData = kernel[y];
            for (int x = 0; x < rowData.length; x++) {
                rowData[x] = Math.sqrt(Math.pow(yDist[y], 2) + Math.pow(xDist[x], 2)) <= radius ? 1.0 : 0.0;
            }
        }
        final double kernelSum = Arrays.stream(kernel).flatMapToDouble(Arrays::stream).sum();
        for (double[] rowData : kernel) {
            for (int x = 0; x < rowData.length; x++) {
                rowData[x] = rowData[x] / kernelSum;
            }
        }
        final double[] oneDimKernel = Arrays.stream(kernel).flatMapToDouble(Arrays::stream).toArray();
        final float[] oneDimFloatKernel = new float[oneDimKernel.length];
        for (int i = 0; i < oneDimKernel.length; i++) {
            oneDimFloatKernel[i] = (float) oneDimKernel[i];
        }

        return new KernelJAI(xDist.length, yDist.length, oneDimFloatKernel);
    }

    private Product createCompatibleProduct(Product sourceProduct, String name) {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();
        return new Product(name, "AATSR_IDEPIX", sceneWidth, sceneHeight);
    }

    @SuppressWarnings("might be used for debug purpose")
    private void writeDebugImage(RenderedImage image, String filename) {
        if (DEBUG) {
            final File outputDir = new File("target/images");
            final File output = new File(outputDir, filename);
            try {
                Files.createDirectories(output.toPath().getParent());
                if (!ImageIO.write(image, "PNG", output)) {
                    SystemUtils.LOG.log(Level.WARNING, "No writer found for image '" + filename + "', trying to reformat the image");
                    final RenderedOp extrema = ExtremaDescriptor.create(image, null, 10, 10, Boolean.FALSE, 1, null);
                    final double[] minimum = (double[]) extrema.getProperty("minimum");
                    final double[] maximum = (double[]) extrema.getProperty("maximum");
                    final RenderedOp step1 = SubtractConstDescriptor.create(image, minimum, null);
                    final RenderedOp normImage = DivideByConstDescriptor.create(step1, new double[]{maximum[0] - minimum[0]}, null);
                    final RenderedOp scaledImage = MultiplyConstDescriptor.create(normImage, new double[]{255}, null);
                    final RenderedOp formattedImage = FormatDescriptor.create(scaledImage, DataBuffer.TYPE_BYTE, null);
                    if (!ImageIO.write(formattedImage, "PNG", output)) {
                        SystemUtils.LOG.log(Level.WARNING, "Retry of writing if image '" + filename + "'did not work too. Giving up!");
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixAatsrOp.class);
        }
    }

    static class PathAndHeightInfo {
        final int[][] illuPathSteps;
        final double[] illuPathHeight;
        final double threshHeight;

        public PathAndHeightInfo(int[][] stepIndex, double[] theoretHeight, double threshHeight) {
            this.illuPathSteps = stepIndex;
            this.illuPathHeight = theoretHeight;
            this.threshHeight = threshHeight;
        }
    }
}
