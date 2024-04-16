package org.esa.snap.idepix.meris;

import com.bc.ceres.core.ProgressMonitor;
import eu.esa.opt.meris.l2auxdata.L2AuxData;
import eu.esa.opt.meris.l2auxdata.L2AuxDataException;
import eu.esa.opt.meris.l2auxdata.L2AuxDataProvider;
import eu.esa.opt.processor.rad2refl.Rad2ReflConstants;
import eu.esa.opt.util.math.FractIndex;
import eu.esa.opt.util.math.Interp;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.RectangleExtender;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.dataio.envisat.EnvisatConstants;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.seaice.LakeSeaIceAuxdata;
import org.esa.snap.idepix.core.seaice.LakeSeaIceClassification;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

/**
 * MERIS pixel classification operator.
 * Only water pixels are classified from NN approach (following BEAM Cawa and OC-CCI algorithm).
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Meris.Water",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "IdePix water pixel classification operator for OLCI.")
public class IdepixMerisWaterClassificationOp extends Operator {

    private Band cloudFlagBand;
    private Band landWaterBand;
    private Band nnOutputBand;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "rhotoa")
    private Product rhoToaProduct;
    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "false",
            description = "Check for sea/lake ice also outside Sea Ice Climatology area.",
            label = "Check for sea/lake ice also outside Sea Ice Climatology area"
    )
    private boolean ignoreSeaIceClimatology;

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "2.0",
            label = " NN cloud ambiguous lower boundary ",
            description = " NN cloud ambiguous lower boundary ")
    private double schillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
            label = " NN cloud ambiguous/sure separation value ",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value ")
    private double schillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
            label = " NN cloud sure/snow separation value ",
            description = " NN cloud ambiguous cloud sure/snow separation value ")
    private double schillerNNCloudSureSnowSeparationValue;

    private static final String MERIS_ALL_NET_NAME = "11x8x5x3_1409.7_all.net";

    private ThreadLocal<SchillerNeuralNetWrapper> merisAllNeuralNet;

    private L2AuxData auxData;

    private static final double SEA_ICE_CLIM_THRESHOLD = 10.0;

    private RectangleExtender rectExtender;

    private LakeSeaIceClassification lakeSeaIceClassification;

    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (L2AuxDataException e) {
            throw new OperatorException("Could not load L2Auxdata", e);
        }

        readSchillerNets();
        createTargetProduct();

        initLakeSeaIceClassification();

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");

        rectExtender = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight()), 1, 1);
    }

    private void readSchillerNets() {
        try (InputStream isAll = getClass().getResourceAsStream(MERIS_ALL_NET_NAME)) {
            merisAllNeuralNet = SchillerNeuralNetWrapper.create(isAll);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Neural Nets: " + e.getMessage());
        }
    }

    private void initLakeSeaIceClassification() {
        final ProductData.UTC startTime = l1bProduct.getStartTime();
        final int monthIndex = startTime.getAsCalendar().get(Calendar.MONTH);
        lakeSeaIceClassification = new LakeSeaIceClassification(null, LakeSeaIceAuxdata.AUXDATA_DIRECTORY, monthIndex + 1);
    }

    private void createTargetProduct() {
        targetProduct = IdepixIO.createCompatibleTargetProduct(l1bProduct, "MER", "MER_L2", true);

        cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixMerisUtils.createMerisFlagCoding();
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        if (outputSchillerNNValue) {
            nnOutputBand = targetProduct.addBand(IdepixConstants.NN_OUTPUT_BAND_NAME,
                    ProductData.TYPE_FLOAT32);
        }
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        try {
            final Rectangle sourceRectangle = rectExtender.extend(targetRectangle);

            Tile[] rhoToaTiles = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                final int suffixStart = Rad2ReflConstants.MERIS_REFL_BAND_NAMES[i].indexOf("_");
                final String reflBandname = Rad2ReflConstants.MERIS_REFL_BAND_NAMES[i].substring(0, suffixStart);
                final Band rhoToaBand = rhoToaProduct.getBand(reflBandname + "_" + (i + 1));
                rhoToaTiles[i] = getSourceTile(rhoToaBand, sourceRectangle);
            }

            Tile l1FlagsTile = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME),
                    sourceRectangle);
            Tile waterFractionTile = getSourceTile(landWaterBand, sourceRectangle);

            Tile szaTile = null;
            Tile vzaTile = null;
            Tile saaTile = null;
            Tile vaaTile = null;
            Tile windUTile = null;
            Tile windVTile = null;
            if (band == cloudFlagBand) {
                szaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME),
                        sourceRectangle);
                vzaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME),
                        sourceRectangle);
                saaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME),
                        sourceRectangle);
                vaaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME),
                        sourceRectangle);
                windUTile = getSourceTile(l1bProduct.getTiePointGrid("zonal_wind"), sourceRectangle);
                windVTile = getSourceTile(l1bProduct.getTiePointGrid("merid_wind"), sourceRectangle);
            }

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    if (!l1FlagsTile.getSampleBit(x, y, IdepixMerisConstants.L1_F_INVALID)) {
                        final int waterFraction = waterFractionTile.getSampleInt(x, y);

                        if (IdepixMerisUtils.isLandPixel(x, y, getGeoPos(x, y), l1FlagsTile, waterFraction)) {
                            if (band == cloudFlagBand) {
                                targetTile.setSample(x, y, IdepixMerisConstants.L1_F_LAND, true);
                            } else {
                                targetTile.setSample(x, y, Float.NaN);
                            }
                        } else {
                            if (band == cloudFlagBand) {
                                classifyCloud(x, y, rhoToaTiles, windUTile, windVTile, szaTile, vzaTile, saaTile, vaaTile,
                                        targetTile, waterFraction);
                            }
                            if (outputSchillerNNValue && band == nnOutputBand) {
                                final double[] nnOutput = getMerisNNOutput(x, y, rhoToaTiles);
                                targetTile.setSample(x, y, nnOutput[0]);
                            }
                        }
                    } else {
                        targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, true);
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    private void classifyCloud(int x, int y, Tile[] rhoToaTiles, Tile winduTile, Tile windvTile,
                               Tile szaTile, Tile vzaTile, Tile saaTile, Tile vaaTile, Tile targetTile,
                               int waterFraction) {

        GeoPos geoPos = getGeoPos(x, y);
        final boolean isCoastline = IdepixMerisUtils.isCoastlinePixel(geoPos, waterFraction);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, isCoastline);

        boolean is_glint_risk = !isCoastline &&
                isGlintRisk(x, y, rhoToaTiles, winduTile, windvTile, szaTile, vzaTile, saaTile, vaaTile);

        final boolean classifiedAsLakeSeaIce = isPixelClassifiedAsLakeSeaIce(geoPos);
        // glint makes sense only if we have no sea ice
        is_glint_risk = is_glint_risk && !classifiedAsLakeSeaIce;

        boolean isCloudSure = false;

        double[] nnOutput = getMerisNNOutput(x, y, rhoToaTiles);
        if (!targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
            if (nnOutput[0] > schillerNNCloudAmbiguousLowerBoundaryValue &&
                    nnOutput[0] <= schillerNNCloudAmbiguousSureSeparationValue) {
                // this would be as 'CLOUD_AMBIGUOUS'...
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, true);
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
            }
            // check for snow_ice separation below if needed, first set all to cloud
            isCloudSure = nnOutput[0] > schillerNNCloudAmbiguousSureSeparationValue;
            if (isCloudSure) {
                // this would be as 'CLOUD_SURE'...
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, true);
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
            }

            if (ignoreSeaIceClimatology || classifiedAsLakeSeaIce) {
                if (nnOutput[0] > schillerNNCloudSureSnowSeparationValue) {
                    // this would be as 'SNOW/ICE'...
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, true);
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);

                }
            }
        }
        targetTile.setSample(x, y, IdepixMerisConstants.IDEPIX_GLINT_RISK, is_glint_risk && !isCloudSure);
    }

    private double[] getMerisNNOutput(int x, int y, Tile[] rhoToaTiles) {
        return getMerisNNOutputImpl(x, y, rhoToaTiles, merisAllNeuralNet.get());
    }

    private double[] getMerisNNOutputImpl(int x, int y, Tile[] rhoToaTiles, SchillerNeuralNetWrapper nnWrapper) {
        double[] nnInput = nnWrapper.getInputVector();
        for (int i = 0; i < nnInput.length; i++) {
            nnInput[i] = Math.sqrt(rhoToaTiles[i].getSampleFloat(x, y));
        }
        return nnWrapper.getNeuralNet().calc(nnInput);
    }

    private boolean isGlintRisk(int x, int y, Tile[] rhoToaTiles, Tile winduTile, Tile windvTile,
                                Tile szaTile, Tile vzaTile, Tile saaTile, Tile vaaTile) {
        final float rhoGlint = (float) computeRhoGlint(x, y, winduTile, windvTile, szaTile, vzaTile, saaTile, vaaTile);
        return (rhoGlint >= 0.2 * rhoToaTiles[12].getSampleFloat(x, y));
    }

    static double computeChiW(double windU, double windV, double saa) {
        final double phiw = MathUtils.RTOD * Math.atan2(windU, windV);
        final double delta = (720.0 + saa - phiw) % 360.0;
        if (delta > 180.0) {
            return 360.0 - delta;
        } else {
            return delta;
        }
    }
    private double computeRhoGlint(int x, int y, Tile winduTile, Tile windvTile,
                                   Tile szaTile, Tile vzaTile, Tile saaTile, Tile vaaTile) {
        final double chiw = computeChiW(winduTile.getSampleFloat(x, y), windvTile.getSampleFloat(x, y), saaTile.getSampleFloat(x, y));
        final float vaa = vaaTile.getSampleFloat(x, y);
        final float saa = saaTile.getSampleFloat(x, y);
        final double deltaAzimuth = (float) IdepixUtils.computeAzimuthDifference(vaa, saa);
        final float windU = winduTile.getSampleFloat(x, y);
        final float windV = windvTile.getSampleFloat(x, y);
        final double windm = Math.sqrt(windU * windU + windV * windV);
        /* allows to retrieve Glint reflectance for current geometry and wind */
        return glintRef(szaTile.getSampleFloat(x, y), vzaTile.getSampleFloat(x, y), deltaAzimuth, windm, chiw);
    }

    private double glintRef(double thetas, double thetav, double delta, double windm, double chiw) {
        FractIndex[] rogIndex = FractIndex.createArray(5);

        Interp.interpCoord(chiw, auxData.rog.getTab(0), rogIndex[0]);
        Interp.interpCoord(thetav, auxData.rog.getTab(1), rogIndex[1]);
        Interp.interpCoord(delta, auxData.rog.getTab(2), rogIndex[2]);
        Interp.interpCoord(windm, auxData.rog.getTab(3), rogIndex[3]);
        Interp.interpCoord(thetas, auxData.rog.getTab(4), rogIndex[4]);
        return Interp.interpolate(auxData.rog.getJavaArray(), rogIndex);
    }

    private boolean isPixelClassifiedAsLakeSeaIce(GeoPos geoPos) {
        final int lakeSeaIceMaskX = (int) (180.0 + geoPos.lon);
        final int lakeSeaIceMaskY = (int) (90.0 - geoPos.lat);
        final float monthlyMaskValue = lakeSeaIceClassification.getMonthlyMaskValue(lakeSeaIceMaskX, lakeSeaIceMaskY);
        return monthlyMaskValue >= SEA_ICE_CLIM_THRESHOLD;
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = getSourceProduct().getSceneGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(IdepixMerisWaterClassificationOp.class);
        }
    }

}
