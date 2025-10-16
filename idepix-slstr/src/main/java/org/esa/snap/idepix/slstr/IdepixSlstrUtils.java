package org.esa.snap.idepix.slstr;

import eu.esa.opt.processor.rad2refl.Rad2ReflConstants;
import eu.esa.opt.processor.rad2refl.Rad2ReflOp;
import eu.esa.opt.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import javax.media.jai.Interpolation;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.ScaleDescriptor;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for IdePix SLSTR
 *
 * @author olafd
 */

class IdepixSlstrUtils {

    /**
     * Provides OLCI pixel classification flag coding
     *
     * @return - the flag coding
     */
    static FlagCoding createSlstrFlagCoding() {
        return IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
    }

    /**
     * Provides OLCI pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    static void setupSlstrClassifBitmask(Product classifProduct) {
        IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);
    }

    static void addSlstrRadiance2ReflectanceBands(Product rad2reflProduct, Product targetProduct, String[] reflBandsToCopy) {
        for (int i = 1; i <= Rad2ReflConstants.SLSTR_REFL_BAND_NAMES.length; i++) {
            for (String bandname : reflBandsToCopy) {
                // e.g. s1_reflectance_an
                if (!targetProduct.containsBand(bandname) && bandname.toLowerCase().contains("_reflectance")) {
                    ProductUtils.copyBand(bandname, rad2reflProduct, targetProduct, true);
                    targetProduct.getBand(bandname).setUnit("dl");
                }
            }
        }
    }

    static void addSlstrBTBands(Product l1bRadProduct, Product targetProduct, String[] namesOfBTBandsToCopy) {
        // S7_bt_in, S7_bt_io, ...
        for (int i = 1; i <= IdepixSlstrConstants.SLSTR_BT_BAND_NAMES.length; i++) {
            for (String nameOfBandToCopy : namesOfBTBandsToCopy) {
                // e.g. S7_bt_in
                if (!targetProduct.containsBand(nameOfBandToCopy) && nameOfBandToCopy.toLowerCase().contains("_bt")) {
//                    ProductUtils.copyBand(nameOfBandToCopy, l1bRadProduct, targetProduct, true);
                    final Band l1bBTBand = l1bRadProduct.getBand(nameOfBandToCopy);
                    Band rescaledBand = getRescaledBTBand(l1bBTBand);
                    if (!targetProduct.containsBand(rescaledBand.getName())) {
                        targetProduct.addBand(rescaledBand);
                    }
                }
            }
        }
    }

    static Product computeRadiance2ReflectanceProduct(Product sourceProduct) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("sensor", Sensor.SLSTR_500m);
        params.put("copyNonSpectralBands", false);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), params, sourceProduct);
    }

    static Band[] getL1bBandsForClassification(String[] slstrBandsForNN,
                                               Product l1bRadProduct,
                                               Product l1bReflProduct) {

        List<Band> l1bBandsForClassificationList = new ArrayList<>();
        for (String s : slstrBandsForNN) {
            final String bandNameForNN = s.toLowerCase();
            if (bandNameForNN.contains("_bt_")) {
                // get BT from radiance product
                for (Band l1bBand : l1bRadProduct.getBands()) {
                    if (bandNameForNN.equals(l1bBand.getName().toLowerCase())) {
                        // upscale BT l1bBand by factor 2 to get same size as refl images
                        Band rescaledBTBand = getRescaledBTBand(l1bBand);
                        l1bBandsForClassificationList.add(rescaledBTBand);
                    }
                }
            } else {
                // get reflectance from rad2refl product
                for (Band band : l1bReflProduct.getBands()) {
                    if (bandNameForNN.equals(band.getName().toLowerCase())) {
                        l1bBandsForClassificationList.add(band);
                    }
                }
            }
        }

        return l1bBandsForClassificationList.toArray(new Band[0]);
    }

    private static Band getRescaledBTBand(Band band1) {
        Band rescaledBTBand = new Band(band1.getName() + "_rescaled", band1.getDataType(),
                band1.getRasterWidth() * 2, band1.getRasterHeight() * 2);
        ProductUtils.copySpectralBandProperties(band1, rescaledBTBand);
        rescaledBTBand.setScalingFactor(band1.getScalingFactor());
        rescaledBTBand.setScalingOffset(band1.getScalingOffset());
        rescaledBTBand.setUnit("dl");
        RenderedImage rescaledBTImage = ScaleDescriptor.create(band1.getSourceImage(),
                2.0f, 2.0f, 0.0f, 0.0f, Interpolation.getInstance(Interpolation.INTERP_NEAREST), null);
        rescaledBTBand.setSourceImage(rescaledBTImage);
        return rescaledBTBand;
    }

}
