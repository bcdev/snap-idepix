/*
 * Copyright (c) 2021.  Brockmann Consult GmbH (info@brockmann-consult.de)
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
 *
 */

package org.esa.snap.idepix.s2msi;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeneralFilterBand;
import org.esa.snap.core.datamodel.Kernel;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.VirtualBand;
import org.esa.snap.core.gpf.Tile;

import java.awt.Color;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CIRRUS_AMBIGUOUS;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CIRRUS_SURE;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLASSIF_FLAGS;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS_NAME;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_DESCR_TEXT;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_NAME;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_SURE;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_SURE_NAME;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_WATER;

/**
 * <p>Currently, bright urban areas are flagged within the Idepix MSI cloud flag. To distinguish urban areas from clouds
 * the Cloud Displacement Index (CDI) is used, which makes use of the three highly correlated near infrared bands that
 * are observed with different view angles. Hence, elevated objects like clouds are observed under a parallax and can
 * be reliably separated from bright ground objects (Frantz et al., 2018 - Uni Trier). Additionally, a spatial filter
 * is applied on the Idepix_Cloud mask to correct gaps in the cloud mask, which ensures that the CDI condition is not
 * applied if a pixel area of 11x11 is all masked as cloud. Furthermore, this correction is only applied for land
 * pixel.<br>
 * The following steps are a first approach for the corrected mask:
 * </p>
 * <br>
 * <p>
 * Band Arithmetic:
 * <pre>
 *     R7_8A = B7/B8A
 *     R8_8A = B8/B8A
 * </pre>
 * <p>
 * Band Filter Standard Deviation 7x7
 * <pre>
 *     R8A7_stddev7 = stddev7(R8A7)
 *     R8A8_stddev7 = stddev7(R8A8)
 * </pre>
 * Calculate CDI
 * <pre>
 *     CDI = (pow(R8A7_stddev7,2) - pow(R8A8_stddev7,2)) /(pow(R8A7_stddev7,2) + pow(R8A8_stddev7,2))
 * </pre>
 * Spatial Filter 11x11 Mean
 * <pre>
 *     cloud_filter = mean11(IDEPIX_CLOUD)
 * </pre>
 * Expression for corrected cloud mask:
 * <pre>
 *     IDEPIX_CLOUD_new =
 *     if IDEPIX_CIRRUS_SURE or IDEPIX_CIRRUS_AMIGUOUS or IDEPIX_WATER then
 *     IDEPIX_CLOUD else if cloud_filter = 255 then
 *     IDEPIX_CLOUD else
 *     CDI<-0.5 and IDEPIX_CLOUD
 * </pre>
 *
 * @author Jorrit Scholze (Algo), Marco Peters(Impl)
 */
public class UrbanCloudDistinction {

    private static final double CDI_THRESHOLD = -0.5;

    private final Product s2ClassifProduct;
    private final Band filteredIdepixCloudFlag;
    private final Band cdiBand;

    private boolean debugBandsEnabled = false;
    private Band ucd_oldCloud_debug;
    private Band ucd_cdi_debug;
    private Band ucd_cloudMean11_debug;

    /**
     * Creates a new instance of the Urban-Cloud-Distinction algorithm
     *
     * @param s2ClassifProduct the already classified product and where the the IDEPIX_CLOUD flag shall be refined
     */
    public UrbanCloudDistinction(Product s2ClassifProduct) {
        this.s2ClassifProduct = s2ClassifProduct;
        filteredIdepixCloudFlag = createFilteredIdepixCloudFlag();
        cdiBand = createCdiBand();
    }

    /**
     * returns if output of debug bands is enabled.
     *
     * @return state of enablement (enabled is {@link #addDebugBandsToTargetProduct was called})
     */
    public boolean areDebugBandsEnabled() {
        return debugBandsEnabled;
    }

    public void correctCloudFlag(int x, int y, Tile classifFlagTile, Tile targetFlagTile) {
        // if IDEPIX_CIRRUS_SURE or IDEPIX_CIRRUS_AMIBGUOUS or IDEPIX_WATER then IDEPIX_CLOUD else if cloud_filter = 255 then IDEPIX_CLOUD else CDI<-0.5 and IDEPIX_CLOUD
        //Note: IDEPIX_CLOUD == flag.IDEPIX_CLOUD || flag.IDEPIX_CLOUD_AMBIGUOUS || flag.IDEPIX_CLOUD_SURE

        final boolean cirrusSureFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_CIRRUS_SURE);
        final boolean cirrusAmbiguousFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_CIRRUS_AMBIGUOUS);
        final boolean waterFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_WATER);
        final boolean cloudAmbiguousFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_CLOUD_AMBIGUOUS);
        final boolean cloudFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_CLOUD);
        final boolean cloudSureFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_CLOUD_SURE);
        targetFlagTile.setSample(x, y, classifFlagTile.getSampleInt(x, y));
        float cdiValue = Float.NaN;

        final float cloudMean11 = filteredIdepixCloudFlag.getSampleFloat(x, y);
        if (cirrusSureFlag || cirrusAmbiguousFlag || waterFlag) {
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD, cloudFlag);
        } else if (cloudMean11 == 255) {
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD, cloudFlag);
        } else {
            cdiValue = cdiBand.getSampleFloat(x, y);
            final boolean cdiLower = cdiValue < CDI_THRESHOLD;
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD, cdiLower && cloudFlag);
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD_AMBIGUOUS, cdiLower && cloudAmbiguousFlag);
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD_SURE, cdiLower && cloudSureFlag);
        }


        if (areDebugBandsEnabled()) {
            final boolean oldCloudMask = cloudFlag || cloudSureFlag || cloudAmbiguousFlag;
            ucd_oldCloud_debug.setPixelInt(x, y, oldCloudMask ? Byte.MAX_VALUE : 0);
            ucd_cdi_debug.setPixelFloat(x, y, cdiValue);
            ucd_cloudMean11_debug.setPixelFloat(x, y, cloudMean11);
        }

    }


    /**
     * Adds debug band to the target product.
     */
    public void addDebugBandsToTargetProduct() {
        ucd_oldCloud_debug = s2ClassifProduct.addBand("__ucd_oldCloud__", ProductData.TYPE_INT8);
        ucd_oldCloud_debug.ensureRasterData();
        ucd_cdi_debug = s2ClassifProduct.addBand("__ucd_cdi__", ProductData.TYPE_FLOAT32);
        ucd_cdi_debug.setNoDataValue(Float.NaN);
        ucd_cdi_debug.ensureRasterData();
        ucd_cloudMean11_debug = s2ClassifProduct.addBand("__ucd_cloudMean11__", ProductData.TYPE_FLOAT32);
        ucd_cloudMean11_debug.setNoDataValue(Float.NaN);
        ucd_cloudMean11_debug.ensureRasterData();
        debugBandsEnabled = true;
    }

    /**
     * Cloud Displacement Index (CDI). The calculation makes use of the three highly correlated near infrared
     * bands that are observed with different view angles.
     *
     * @return the CDI band
     */
    private Band createCdiBand() {
        Band ratio7_8A = getRatio7_8A(s2ClassifProduct);
        Band ratio8_8A = getRatio8_8A(s2ClassifProduct);
        Band stdDev7_ratio7_8A = computeStdDev7(ratio7_8A);
        Band stdDev7_ratio8_8A = computeStdDev7(ratio8_8A);
        final String expression = String.format("(pow(%1$s, 2) - pow(%2$s, 2)) / (pow(%1$s, 2) + pow(%2$s, 2))",
                                                stdDev7_ratio7_8A.getName(), stdDev7_ratio8_8A.getName());
        return createVirtualBand("__cdi", expression, s2ClassifProduct);
    }

    /**
     * Filters the given cloud flag mask by a 11x11 mean filter
     *
     * @return the filtered cloud flag
     */
    private Band createFilteredIdepixCloudFlag() {
        final Mask cloudMask = Mask.BandMathsType.create(IDEPIX_CLOUD_NAME, IDEPIX_CLOUD_DESCR_TEXT,
                                                         s2ClassifProduct.getSceneRasterWidth(), s2ClassifProduct.getSceneRasterHeight(),
                                                         String.format("%1$s.%2$s || %1$s.%3$s || %1$s.%4$s", IDEPIX_CLASSIF_FLAGS,
                                                                       IDEPIX_CLOUD_NAME, IDEPIX_CLOUD_SURE_NAME, IDEPIX_CLOUD_AMBIGUOUS_NAME),
                                                         new Color(178, 178, 0), 0.5f);
        cloudMask.setOwner(s2ClassifProduct);

        return computeMean11(cloudMask);
    }

    private Band computeStdDev7(Band band) {
        final int kernelSize = 7;
        final Kernel kernel = new Kernel(kernelSize, kernelSize, new double[kernelSize * kernelSize]);
        final GeneralFilterBand filterBand = new GeneralFilterBand("__stddev7_" + band.getName(), band, GeneralFilterBand.OpType.STDDEV, kernel, 1);
        s2ClassifProduct.addBand(filterBand);
        return filterBand;
    }

    private Band computeMean11(Band band) {
        final int kernelSize = 11;
        final Kernel kernel = new Kernel(kernelSize, kernelSize, new double[kernelSize * kernelSize]);
        final GeneralFilterBand filterBand = new GeneralFilterBand("__mean11_" + band.getName(), band, GeneralFilterBand.OpType.MEAN, kernel, 1);
        s2ClassifProduct.addBand(filterBand);
        return filterBand;
    }

    private Band getRatio8_8A(Product s2ClassifProduct) {
        return createRatioBand(s2ClassifProduct.getBand("B8"), s2ClassifProduct.getBand("B8A"), s2ClassifProduct);
    }

    private Band getRatio7_8A(Product s2ClassifProduct) {
        return createRatioBand(s2ClassifProduct.getBand("B7"), s2ClassifProduct.getBand("B8A"), s2ClassifProduct);
    }

    private static Band createRatioBand(Band numerator, Band denominator, Product owner) {
        final String name = String.format("__ratio_%s_%s", numerator.getName(), denominator.getName());
        final String expression = String.format("%s / %s", numerator.getName(), denominator.getName());
        return createVirtualBand(name, expression, owner);
    }

    private static VirtualBand createVirtualBand(String name, String expression, Product owner) {
        final VirtualBand virtBand = new VirtualBand(name,
                                                     ProductData.TYPE_FLOAT32,
                                                     owner.getSceneRasterWidth(),
                                                     owner.getSceneRasterHeight(),
                                                     expression);
        owner.addBand(virtBand);
        return virtBand;
    }

}
