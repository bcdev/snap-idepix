package org.esa.snap.idepix.slstr;

import eu.esa.opt.processor.rad2refl.Rad2ReflConstants;
import eu.esa.opt.processor.rad2refl.Rad2ReflOp;
import eu.esa.opt.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for IdePix OLCI
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
                if (!targetProduct.containsBand(bandname) && bandname.startsWith("S" + String.format("%01d", i) + "_reflectance")) {
                    ProductUtils.copyBand(bandname, rad2reflProduct, targetProduct, true);
                    targetProduct.getBand(bandname).setUnit("dl");
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

}
