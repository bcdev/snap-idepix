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
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.image.ImageManager;
import org.esa.snap.core.util.ImageUtils;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.core.util.math.Range;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import javax.imageio.ImageIO;
import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.KernelJAI;
import javax.media.jai.OpImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ConvolveDescriptor;
import javax.media.jai.operator.CropDescriptor;
import javax.media.jai.operator.FormatDescriptor;
import javax.media.jai.operator.MosaicDescriptor;
import javax.media.jai.operator.MultiplyConstDescriptor;
import javax.media.jai.operator.ScaleDescriptor;
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
import java.util.logging.Level;

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
    private final static int CLOUD_TOP_HEIGHT = 6000; // in meter

    @SourceProduct(label = "AATSR L1b product",
            description = "The AATSR L1b source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // overall parameters

    @Override
    public void initialize() throws OperatorException {
        validate(sourceProduct); // 1.1)
        // 1.2) validateParameters(); // if any

        // 2) create TargetProduct
        targetProduct = createCompatibleProduct(sourceProduct, sourceProduct.getName() + "_idepix");
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        // 2.1) copy source bands (todo - which source bands to include?)
        // 2.2) create flag band compatible with other IdePix processors but only
        final Band cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
        cloudFlagBand.setSampleCoding(flagCoding);
        IdepixFlagCoding.setupDefaultClassifBitmask(targetProduct);

    }

    @Override
    public void doExecute(ProgressMonitor pm) throws OperatorException {
        pm.beginTask("Preparing cloud shadow detection.", 4);
        try {
            final int sceneWidth = sourceProduct.getSceneRasterWidth();
            final int sceneHeight = sourceProduct.getSceneRasterHeight();

            // 1) detect day time area where cloud shadow can occur
            final Mask dayMask = sourceProduct.getMaskGroup().get("confidence_in_day");
            // test sun elevation and find first and last row with SZA<85°, daylight zone
            // day flag is necessary because there can be an area with SZA<85° but it is not marked as DAY.
            final Mask szaDayTest = Mask.BandMathsType.create("__SZA_DAY_TEST", "", sceneWidth, sceneHeight,
                                                              "solar_zenith_tn < 85 && confidence_in_day",
                                                              Color.yellow, 0.5f);
            szaDayTest.setOwner(sourceProduct);
            final Range dayRange = detectMaskedPixelRangeInColumn(szaDayTest, sceneWidth - 1);
            pm.worked(1);

            // 2) create north-corrected orientation on a sub-sampled (100) grid then scale back
            // to original size using bicubic interpolation
            final RasterDataNode latRaster = sourceProduct.getRasterDataNode("latitude_tx"); // todo - better use latitude_in?
            final RasterDataNode lonRaster = sourceProduct.getRasterDataNode("longitude_tx"); // todo - better use longitude_in?
            final Interpolation nearest = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
            final Interpolation bicubic = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
            final float downScaleFactor = 1 / 100F;
            final RenderedOp lowResLats = ScaleDescriptor.create(latRaster.getSourceImage(), downScaleFactor, downScaleFactor, 0.0F, 0.0F, nearest, null);
            final RenderedOp lowResLons = ScaleDescriptor.create(lonRaster.getSourceImage(), downScaleFactor, downScaleFactor, 0.0F, 0.0F, nearest, null);
            final OpImage lowResOrientation = new OrientationOpImage(lowResLats, lowResLons);
            final float upScaleWidthFactor = sceneWidth / (float) lowResOrientation.getWidth();
            final float upScaleHeightFactor = sceneHeight / (float) lowResOrientation.getHeight();
            final RenderedOp orientationImage = ScaleDescriptor.create(lowResOrientation, upScaleWidthFactor, upScaleHeightFactor, 0.0F, 0.0F, bicubic, null);
            pm.worked(1);

            // 3) create cloudMaskImage and landMaskImage
            // as alternative the bayesian_in and confidence_in could be used. See TechNote.
            // But currently the bayes_in.no_bayesian_probabilities_available is always set. so it makes no sense to use it.
            final Mask cloudMask = Mask.BandMathsType.create("__cloud_mask", "", sceneWidth, sceneHeight,
                                                             "cloud_in.visible or cloud_in.12_gross_cloud or cloud_in.11_12_thin_cirrus or cloud_in.3_7_12_medium_high",
                                                             Color.white, 0.5f);
            cloudMask.setOwner(sourceProduct);
            writeDebugImage(cloudMask.getSourceImage(), "images/cloudMask.png");

            final Mask landMask = Mask.BandMathsType.create("__land_mask", "", sceneWidth, sceneHeight,
                                                            "confidence_in.coastline or confidence_in.tidal or confidence_in.land or confidence_in.inland_water",
                                                            Color.green, 0.5f);
            landMask.setOwner(sourceProduct);
            writeDebugImage(landMask.getSourceImage(), "images/landMask.png");

            pm.worked(1);


            // 4) create startSearchMask using cloudMaskImage, landMaskImage and search radius
            // splitting cloudMask image into 2000 y-pixel slices
            final List<Rectangle> slices = sliceRect(cloudMask.getSourceImage().getBounds(), 2000);
            final List<RenderedImage> convolvedCloudSlices = new ArrayList<>();
            final List<RenderedImage> convolvedLandSlices = new ArrayList<>();
            final RasterDataNode sza = sourceProduct.getRasterDataNode("solar_zenith_tn");
            // convolution shall take place with float data type. We need to format the source images of cloudMask and landMask.
            final RenderedImage floatCloudMaskImage = FormatDescriptor.create(cloudMask.getSourceImage(), DataBuffer.TYPE_FLOAT,null);
            final RenderedImage floatLandMaskImage = FormatDescriptor.create(landMask.getSourceImage(), DataBuffer.TYPE_FLOAT,null);;
            for (Rectangle slice : slices) {
                final RenderedOp curCloudSlice = CropDescriptor.create(floatCloudMaskImage, (float) slice.x, (float) slice.y, (float) slice.width, (float) slice.height, null);
                final RenderedOp curLandSlice = CropDescriptor.create(floatLandMaskImage, (float) slice.x, (float) slice.y, (float) slice.width, (float) slice.height, null);
                // convolve
                double radius = computeKernelRadiusForSlice(sza, slice);
                final KernelJAI jaiKernel = createJaiKernel(radius, new Dimension(1000, 1000));
                // Todo - Convolution needs to be done on the full image
                // Todo - why does convolution generate blocks?
                final RenderedImage convCloudImage = createConvolvedImage(curCloudSlice, jaiKernel, null);
                writeDebugImage(convCloudImage, "images/convCloudImage_" + slice.y + ".png");
                final RenderedImage convLandImage = createConvolvedImage(curLandSlice, jaiKernel, null);
                writeDebugImage(convLandImage, "images/convLandImage_" + slice.y + ".png");
                convolvedCloudSlices.add(convCloudImage);
                convolvedLandSlices.add(convLandImage);
                // if the images need to be translated in order to be mosaicked do this:
//                convolvedCloudSlices.add(TranslateDescriptor.create(convolvedCloudImage,
//                                                                    (float) slice.getMinX(), (float) slice.getMinY(),
//                                                                    new InterpolationNearest(), null));
//                convolvedLandSlices.add(TranslateDescriptor.create(convolvedLandImage,
//                                                                   (float) slice.getMinX(), (float) slice.getMinY(),
//                                                                   new InterpolationNearest(), null));
            }
            // first mosaic slices
            final Product tempMaskProduct = new Product("temp", "tempType", sceneWidth, sceneHeight);
            final Band convCloudMaskBand = new Band("__convCloudMask", ProductData.TYPE_FLOAT32, sceneWidth, sceneHeight);
            convCloudMaskBand.setSourceImage(createMosaic(slices, convolvedCloudSlices, floatCloudMaskImage));
            writeDebugImage(convCloudMaskBand.getSourceImage(), "images/convCloudMosaicked.png");
//            convCloudMaskBand.setOwner(sourceProduct);
            tempMaskProduct.addBand(convCloudMaskBand);

            final Band convLandMaskBand = new Band("__convLandMask", ProductData.TYPE_FLOAT32, sceneWidth, sceneHeight);
            convLandMaskBand.setSourceImage(createMosaic(slices, convolvedLandSlices, floatLandMaskImage));
            writeDebugImage(convLandMaskBand.getSourceImage(), "images/convLandMosaicked.png");
//            convLandMaskBand.setOwner(sourceProduct);
            tempMaskProduct.addBand(convLandMaskBand);

            final Mask startSearchMask = Mask.BandMathsType.create("__startSearch", "", sceneWidth, sceneHeight,
                                                                   "__convCloudMask > 0.001 && __convCloudMask < 0.998 && __convLandMask > 0.001",
                                                                   Color.BLUE, 0.0);
//            startSearchMask.setOwner(sourceProduct);
            tempMaskProduct.addBand(startSearchMask);

            writeDebugImage(startSearchMask.getSourceImage(), "images/startSearchMask.png");

            // use band math to combine mosaics to startSearchMask
            pm.worked(1);
            // used later
            final RasterDataNode saa = sourceProduct.getRasterDataNode("solar_azimuth_tn");
            final RasterDataNode oza = sourceProduct.getRasterDataNode("sat_zenith_tn");
            final RasterDataNode x_tx = sourceProduct.getRasterDataNode("x_tx");
        } catch (IOException e) {
            throw new OperatorException("Could not read source data", e);
        } finally {
            pm.done();
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        // 1) compute flag data
        //    use max distance from 2000-blocks for getting source data
        // 1.1) compute illuPathSteps, illuPathHeight, threshHeight
        // 1.2) compute cloud shadow
        // 2) set flags cloud_shadow and cloud
    }

    private void writeDebugImage(RenderedImage image, String relPathname) throws IOException {
        if (DEBUG) {
            final File output = new File(relPathname);
            Files.createDirectories(output.toPath().getParent());
            if (!ImageIO.write(image, "PNG", output)) {
                SystemUtils.LOG.log(Level.WARNING, "No writer found for image '" + relPathname + "', trying to reformat the image");
                final RenderedOp scaledImage = MultiplyConstDescriptor.create(image, new double[]{100}, null);
                final RenderedOp formattedImage = FormatDescriptor.create(scaledImage, DataBuffer.TYPE_BYTE, null);
                if(!ImageIO.write(formattedImage, "PNG", output)){
                    SystemUtils.LOG.log(Level.WARNING, "Retry of writing if image '" + relPathname + "'did not work too. Giving up!");
                }
            }
        }
    }

    private RenderedImage createMosaic(List<Rectangle> slices, List<RenderedImage> imageSlices, RenderedImage referenceImage) {
        double[][] sourceThresholds = new double[slices.size()][1];
        for (int i = 0; i < sourceThresholds.length; i++) {
            sourceThresholds[i][0] = Double.MIN_VALUE;
        }
        final ImageLayout mosaicImageLayout = ImageManager.createSingleBandedImageLayout(DataBuffer.TYPE_FLOAT,
                                                                                         referenceImage.getWidth(),
                                                                                         referenceImage.getHeight(),
                                                                                         referenceImage.getTileWidth(),
                                                                                         referenceImage.getTileHeight());
        final double[] backgroundValue = {Double.NaN};
        return MosaicDescriptor.create(imageSlices.toArray(new RenderedImage[0]),
                                       MosaicDescriptor.MOSAIC_TYPE_OVERLAY,
                                       null,
                                       null,
                                       sourceThresholds,
                                       backgroundValue,
                                       new RenderingHints(JAI.KEY_IMAGE_LAYOUT, mosaicImageLayout));
    }

    private double computeKernelRadiusForSlice(RasterDataNode sza, Rectangle slice) throws IOException {
        final float[] szaData = new float[slice.width * slice.height];
        sza.readPixels(slice.x, slice.y, slice.width, slice.height, szaData);
        Arrays.sort(szaData);
        float szaMedian;
        if (szaData.length % 2 == 0)
            szaMedian = (szaData[szaData.length / 2] + szaData[szaData.length / 2 - 1]) / 2;
        else
            szaMedian = szaData[szaData.length / 2];
        return Math.abs(CLOUD_TOP_HEIGHT * Math.tan(szaMedian * Math.PI / 180.0));
    }

    static List<Rectangle> sliceRect(Rectangle sourceBounds, int sliceHeight) {
        List<Rectangle> slices = new ArrayList<>();
        for (int i = 0; i < sourceBounds.height; i += sliceHeight) {
            Rectangle curRect = new Rectangle(0, i, sourceBounds.width, sliceHeight);
            curRect = sourceBounds.intersection(curRect);
            if (!curRect.isEmpty()) {
                slices.add(curRect);
            }
        }
        return slices;
    }

    private RenderedImage createConvolvedImage(RenderedImage sourceImage, KernelJAI jaiKernel, RenderingHints rh) {
        return ConvolveDescriptor.create(sourceImage, jaiKernel, rh);
    }

    private KernelJAI createJaiKernel(double radius, Dimension spacing) {
        Dimension kernelHalfDim = new Dimension(MathUtils.ceilInt(radius / spacing.width),
                                                MathUtils.ceilInt(radius / spacing.height));
        int[] xDist = createDistanceArray(kernelHalfDim.width, spacing.width);
        int[] yDist = createDistanceArray(kernelHalfDim.height, spacing.height);
        return createKernelData(xDist, yDist, radius);
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

    static int[] createDistanceArray(int kernelHalfDim, int spacing) {
        int[] xDist = new int[kernelHalfDim * 2 + 1];
        for (int i = 0; i < xDist.length; i++) {
            xDist[i] = -kernelHalfDim * spacing + i * spacing;
        }
        return xDist;
    }


    private Product createCompatibleProduct(Product sourceProduct, String name) {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();
        Product targetProduct = new Product(name, "AATSR_IDEPIX", sceneWidth, sceneHeight);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        return targetProduct;
    }

    static Range detectMaskedPixelRangeInColumn(Mask mask, int column) {
        final int rasterHeight = mask.getRasterHeight();
        final Raster data = mask.getSourceImage().getData(new Rectangle(column, 0, 1, rasterHeight));
        // masks have byte data type
        final byte[] dataBufferArray = (byte[]) ImageUtils.createDataBufferArray(data.getTransferType(), rasterHeight);
        data.getDataElements(column, 0, 1, rasterHeight, dataBufferArray);
        final Range range = new Range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

        // todo - can be further optimised and parallelized
        for (int i = 0; i < dataBufferArray.length; i++) {
            if (Double.isInfinite(range.getMin())) {
                if (dataBufferArray[i] == -1) {
                    range.setMin(i);
                }
            }
        }
        for (int i = dataBufferArray.length - 1; i >= 0; i--) {
            if (!Double.isInfinite(range.getMin()) && Double.isInfinite(range.getMax())) {
                if (dataBufferArray[i] == -1) {
                    range.setMax(i);
                }
            }
        }
        return range;
    }

    void validate(Product sourceProduct) throws OperatorException {
        // todo - test for the used data.
        if (!"ENV_AT_1_RBT".equals(sourceProduct.getProductType())) {
            throw new OperatorException("An AATSR product from the 4th reprocessing is needed as input");
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
}
