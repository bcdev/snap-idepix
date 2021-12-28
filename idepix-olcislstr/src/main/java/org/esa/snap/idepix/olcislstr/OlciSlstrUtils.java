package org.esa.snap.idepix.olcislstr;

import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.util.Guardian;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflOp;
import org.esa.s3tbx.processor.rad2refl.Sensor;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for IdePix OLCI
 *
 * @author olafd
 */

class OlciSlstrUtils {

    /**
     * Provides OLCI pixel classification flag coding
     *
     * @return - the flag coding
     */
    static FlagCoding createOlciFlagCoding() {
        return IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
    }

    /**
     * Provides OLCI pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    static void setupOlciClassifBitmask(Product classifProduct) {
        IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);
    }

    static void addOlciRadiance2ReflectanceBands(Product rad2reflProduct, Product targetProduct, String[] reflBandsToCopy) {
        for (int i = 1; i <= Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            for (String bandname : reflBandsToCopy) {
                // e.g. Oa01_reflectance
                if (!targetProduct.containsBand(bandname) && bandname.equals("Oa" + String.format("%02d", i) + "_reflectance")) {
                    ProductUtils.copyBand(bandname, rad2reflProduct, targetProduct, true);
                    targetProduct.getBand(bandname).setUnit("dl");
                }
            }
        }
    }

    static void addSlstrRadiance2ReflectanceBands(Product rad2reflProduct, Product targetProduct, String[] reflBandsToCopy) {
        for (int i = 1; i <= Rad2ReflConstants.SLSTR_REFL_BAND_NAMES.length; i++) {
            for (String bandname : reflBandsToCopy) {
                // e.g. S1_reflectance_an
                if (!targetProduct.containsBand(bandname) && bandname.startsWith("S" + String.format("%01d", i) + "_reflectance")) {
                    ProductUtils.copyBand(bandname, rad2reflProduct, targetProduct, true);
                    targetProduct.getBand(bandname).setUnit("dl");
                }
            }
        }
    }

    static Product computeRadiance2ReflectanceProduct(Product sourceProduct, Sensor sensor) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("sensor", sensor);
        params.put("copyNonSpectralBands", false);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), params, sourceProduct);
    }

    /**
     * Copies SLSTR cloud flag bands from source to target product.
     *
     * @param sourceProduct - the SYN source product
     * @param targetProduct - the target product
     */
    public static void copySlstrCloudFlagBands(Product sourceProduct, Product targetProduct) {
        Guardian.assertNotNull("source", sourceProduct);
        Guardian.assertNotNull("target", targetProduct);
        if (sourceProduct.getFlagCodingGroup().getNodeCount() > 0) {

            // loop over bands and check if they have a flags coding attached
            for (int i = 0; i < sourceProduct.getNumBands(); i++) {
                Band sourceBand = sourceProduct.getBandAt(i);
                String bandName = sourceBand.getName();
                // Use prefix 'cloud_' as identifier for SLSTR cloud flag band and copy only those.
                if (bandName.startsWith("cloud_") && sourceBand.isFlagBand() && targetProduct.getBand(bandName) == null) {
                    ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
                }
            }

            // first the bands have to be copied and then the masks
            // other wise the referenced bands, e.g. flag band, is not contained in the target product
            // and the mask is not copied
            copySlstrCloudMasks(sourceProduct, targetProduct);
//            ProductUtils.copyOverlayMasks(sourceProduct, targetProduct);
        }
    }

    private static void copySlstrCloudMasks(Product sourceProduct, Product targetProduct) {
        ProductNodeGroup<Mask> sourceMaskGroup = sourceProduct.getMaskGroup();

        for(int i = 0; i < sourceMaskGroup.getNodeCount(); ++i) {
            Mask mask = sourceMaskGroup.get(i);
            System.out.println("mask: " + mask.getName());
            final boolean isSlstrCloudMask = mask.getName().startsWith("cloud_");
            if (isSlstrCloudMask) {
                System.out.println("true");
            }
            if (isSlstrCloudMask && !targetProduct.getMaskGroup().contains(mask.getName()) && mask.getImageType().canTransferMask(mask, targetProduct)) {
                mask.getImageType().transferMask(mask, targetProduct);
            }
        }

    }
}
