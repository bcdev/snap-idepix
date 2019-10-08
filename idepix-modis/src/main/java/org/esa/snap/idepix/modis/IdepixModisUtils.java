package org.esa.snap.idepix.modis;

import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import java.util.Random;

/**
 * Utility class for IdePix MODIS
 *
 * @author olafd
 */
class IdepixModisUtils {

    /**
     * Provides MODIS pixel classification flag coding
     *
     * @return - the flag coding
     */
    static FlagCoding createModisFlagCoding() {
        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);

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
        Random r = new Random(1234567);

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
        final MetadataAttribute dayNightAttr = product.getMetadataRoot().getElement("Global_Attributes").
                getAttribute("DayNightFlag");

        if (dayNightAttr != null && !dayNightAttr.getData().getElemString().equals("Day")) {
            throw new OperatorException("Product '" + product.getName() +
                                                "' does not seem to be a MODIS L1b Day product - will exit IdePix.");
        }
    }

//    static boolean checkIfDayProduct(Product product, Logger logger) {
//        final MetadataAttribute dayNightAttr = product.getMetadataRoot().getElement("Global_Attributes").
//                getAttribute("DayNightFlag");
//        logger.info("dayNightAttr: " + dayNightAttr);
//        if (dayNightAttr != null) {
//            logger.info("dayNightAttr.getData().getElemString(): " + dayNightAttr.getData().getElemString());
//        }
//
//        final MetadataAttribute daymodeScansAttr = product.getMetadataRoot().getElement("Global_Attributes").
//                getAttribute("Number_of_Day_mode_scans");
//        logger.info("dayNightAttr: " + dayNightAttr);
//        if (daymodeScansAttr != null) {
//            logger.info("daymodeScansAttr.getData().getElemString(): " + daymodeScansAttr.getData().getElemString());
//        }
//
//        return dayNightAttr == null || dayNightAttr.getData().getElemString().equals("Day");
//    }


    static void validateModisWaterMaskProduct(Product sourceProduct, Product waterMaskProduct) {
        Band waterMaskBand = waterMaskProduct.getBand(IdepixModisConstants.MODIS_WATER_MASK_BAND_NAME);
        if (waterMaskBand == null) {
            throw new OperatorException("Specified water mask product does not contain a band named '" +
                    IdepixModisConstants.MODIS_WATER_MASK_BAND_NAME + "'. Please check.");
        }
        final int cw = waterMaskProduct.getSceneRasterWidth();
        final int ch = waterMaskProduct.getSceneRasterHeight();
        final int sw = sourceProduct.getSceneRasterWidth();
        final int sh = sourceProduct.getSceneRasterHeight();
        if (cw != sw || ch != sh) {  // todo: this is not sufficient! Compare date/time from filenames!
            throw new OperatorException("Dimensions of water mask product differ from source product. Please check.");
        }
    }

}
