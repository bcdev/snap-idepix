package org.esa.snap.idepix.meris;

import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflOp;
import org.esa.s3tbx.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.ProductUtils;

import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Utility class for IdePix MERIS
 *
 * @author olafd
 */
class IdepixMerisUtils {

    /**
     * Provides MERIS pixel classification flag coding
     *
     * @return - the flag coding
     */
    static FlagCoding createMerisFlagCoding() {
        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);

        flagCoding.addFlag("IDEPIX_GLINT_RISK", BitSetter.setFlag(0, IdepixMerisConstants.IDEPIX_GLINT_RISK),
                           IdepixMerisConstants.IDEPIX_GLINT_RISK_DESCR_TEXT);

        return flagCoding;
    }

    /**
     * Provides MERIS pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    static void setupMerisClassifBitmask(Product classifProduct) {
        int index = IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);

        int w = classifProduct.getSceneRasterWidth();
        int h = classifProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random(1234567);

        mask = Mask.BandMathsType.create("IDEPIX_GLINT_RISK", IdepixMerisConstants.IDEPIX_GLINT_RISK_DESCR_TEXT, w, h,
                                         "pixel_classif_flags.IDEPIX_GLINT_RISK",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index, mask);

    }

    static void addMerisRadiance2ReflectanceBands(Product rad2reflProduct, Product targetProduct, String[] reflBandsToCopy) {
        for (int i = 1; i <= Rad2ReflConstants.MERIS_REFL_BAND_NAMES.length; i++) {
            for (String bandname : reflBandsToCopy) {
                // e.g. Oa01_reflectance
                if (!targetProduct.containsBand(bandname) &&
                        bandname.startsWith(Rad2ReflConstants.MERIS_AUTOGROUPING_REFL_STRING) &&
                        bandname.endsWith("_" + i)) {
                    ProductUtils.copyBand(bandname, rad2reflProduct, targetProduct, true);
                    targetProduct.getBand(bandname).setUnit("dl");
                }
            }
        }
    }

    static Product computeRadiance2ReflectanceProduct(Product sourceProduct) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("sensor", Sensor.MERIS);
        params.put("copyNonSpectralBands", false);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), params, sourceProduct);
    }

    static Product computeCloudTopPressureProduct(Product sourceProduct) {
        return GPF.createProduct("Meris.CloudTopPressureOp", GPF.NO_PARAMS, sourceProduct);
    }

}
