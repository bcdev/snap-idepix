package org.esa.snap.idepix.probav;

import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.BitSetter;

import java.util.Random;

/**
 * Utility class for IdePix MERIS
 *
 * @author olafd
 */
class ProbaVUtils {

    /**
     * Provides MERIS pixel classification flag coding
     *
     * @return - the flag coding
     */
    static FlagCoding createProbavFlagCoding() {
        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);

        flagCoding.addFlag("IDEPIX_WATER", BitSetter.setFlag(0, ProbaVConstants.IDEPIX_WATER),
                           ProbaVConstants.IDEPIX_WATER_DESCR_TEXT);
        flagCoding.addFlag("IDEPIX_CLEAR_LAND", BitSetter.setFlag(0, ProbaVConstants.IDEPIX_CLEAR_LAND),
                           ProbaVConstants.IDEPIX_CLEAR_LAND_DESCR_TEXT);
        flagCoding.addFlag("IDEPIX_CLEAR_WATER", BitSetter.setFlag(0, ProbaVConstants.IDEPIX_CLEAR_WATER),
                           ProbaVConstants.IDEPIX_CLEAR_WATER_DESCR_TEXT);

        return flagCoding;
    }

    /**
     * Provides MERIS pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    static void setupProbavClassifBitmask(Product classifProduct) {
        int index = IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);

        int w = classifProduct.getSceneRasterWidth();
        int h = classifProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random(124567);

        mask = Mask.BandMathsType.create("IDEPIX_WATER", ProbaVConstants.IDEPIX_WATER_DESCR_TEXT, w, h,
                                         "pixel_classif_flags.IDEPIX_WATER",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("IDEPIX_CLEAR_LAND", ProbaVConstants.IDEPIX_CLEAR_LAND_DESCR_TEXT, w, h,
                                         "pixel_classif_flags.IDEPIX_CLEAR_LAND",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("IDEPIX_CLEAR_WATER", ProbaVConstants.IDEPIX_CLEAR_WATER_DESCR_TEXT, w, h,
                                         "pixel_classif_flags.IDEPIX_CLEAR_WATER",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index, mask);
    }
}
