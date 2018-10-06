package org.esa.snap.idepix.modis;

import org.esa.s3tbx.idepix.core.IdepixFlagCoding;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.util.BitSetter;

import java.util.Random;

/**
 * Utility class for Idepix MODIS
 *
 * @author olafd
 */
public class IdepixModisUtils {

    /**
     * Provides MODIS pixel classification flag coding
     *
     * @param flagId - the flag ID
     * @return - the flag coding
     */
    static FlagCoding createModisFlagCoding(String flagId) {
        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(flagId);

        flagCoding.addFlag("IDEPIX_MIXED_PIXEL", BitSetter.setFlag(0, IdepixModisConstants.IDEPIX_MIXED_PIXEL),
                IdepixModisConstants.IDEPIX_MIXED_PIXEL_DESCR_TEXT);

        flagCoding.addFlag("IDEPIX_CLOUD_B_NIR", BitSetter.setFlag(0, IdepixModisConstants.IDEPIX_CLOUD_B_NIR),
                IdepixModisConstants.IDEPIX_CLOUD_B_NIR_DESCR_TEXT);

        return flagCoding;
    }

    /**
     * Provides MODIS pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    static void setupModisClassifBitmask(Product classifProduct) {
        int index = IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);

        int w = classifProduct.getSceneRasterWidth();
        int h = classifProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random();

        mask = Mask.BandMathsType.create("IDEPIX_MIXED_PIXEL", IdepixModisConstants.IDEPIX_MIXED_PIXEL_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_MIXED_PIXEL",
                IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("IDEPIX_CLOUD_B_NIR", IdepixModisConstants.IDEPIX_CLOUD_B_NIR_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_CLOUD_B_NIR",
                IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index, mask);
    }

    static void checkIfDayProduct(Product product) {
        final String dayNightString =
                product.getMetadataRoot().getElements()[0].getAttributes()[20].getData().getElemString();

        if (!dayNightString.equals("Day")) {
            throw new OperatorException("Product '" + product.getName() +
                    "' does not seem to be a MODIS L1b Day product - will exit IdePix.");
        }
    }
}
