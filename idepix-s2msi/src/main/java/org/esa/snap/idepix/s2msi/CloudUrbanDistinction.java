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
public class CloudUrbanDistinction {

    private final Product l1cProduct;

    /**
     * Creates a new instance of the Cloud-Urban-Distinction algorithm
     * @param l1cProduct the product which is used to get the necessary bands from and also a owner
     *                   of temporary virtual bands.
     */
    public CloudUrbanDistinction(Product l1cProduct) {
        this.l1cProduct = l1cProduct;
    }

    /**
     * Cloud Displacement Index (CDI). The calculation makes use of the three highly correlated near infrared
     * bands that are observed with different view angles.
     *
     * @return the CDI band
     */
    public Band calculateCdiBand() {
        Band ratio7_8A = getRatio7_8A();
        Band ratio8_8A = getRatio8_8A();
        Band stdDev7_ratio7_8A = computeStdDev7(ratio7_8A);
        Band stdDev7_ratio8_8A = computeStdDev7(ratio8_8A);
        final String expression = String.format("(pow(%1$s,2) - pow(%2$s,2)) / (pow(%1$s,2) + pow(%2$s,2))",
                                                stdDev7_ratio7_8A.getName(), stdDev7_ratio8_8A);
        return createVirtualBand("__cdi", expression, l1cProduct);
    }

    /**
     * Filters the given cloud flag mask by a 11x11 mean filter
     *
     * @return the filtered cloud flag
     */
    public Band filterIdepixCloudFlag() {
        final Mask cloudMask = l1cProduct.getMaskGroup().get("IDEPIX_CLOUD)");
        return computeMean11(cloudMask);
    }

    private Band computeStdDev7(Band band) {
        final int kernelSize = 7;
        final Kernel kernel = new Kernel(kernelSize, kernelSize, new double[kernelSize * kernelSize]);
        final GeneralFilterBand filterBand = new GeneralFilterBand("__stddev7_" + band.getName(), band, GeneralFilterBand.OpType.STDDEV, kernel, 1);
        filterBand.setOwner(l1cProduct);
        return filterBand;
    }

    private Band computeMean11(Band band) {
        final int kernelSize = 11;
        final Kernel kernel = new Kernel(kernelSize, kernelSize, new double[kernelSize * kernelSize]);
        final GeneralFilterBand filterBand = new GeneralFilterBand("__mean11_" + band.getName(), band, GeneralFilterBand.OpType.MEAN, kernel, 1);
        filterBand.setOwner(l1cProduct);
        return filterBand;
    }

    private Band getRatio8_8A() {
        return createRatioBand(l1cProduct.getBand("B8"), l1cProduct.getBand("B8A"), l1cProduct);
    }

    private Band getRatio7_8A() {
        return createRatioBand(l1cProduct.getBand("B7"), l1cProduct.getBand("B8A"), l1cProduct);
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
        virtBand.setOwner(owner);
        return virtBand;
    }
}
