package org.esa.snap.idepix.olci;

import org.esa.s3tbx.idepix.core.IdepixFlagCoding;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflOp;
import org.esa.s3tbx.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for Idepix OLCI
 *
 * @author olafd
 */

public class IdepixOlciUtils {

    /**
     * Provides OLCI pixel classification flag coding
     *
     * @param flagId - the flag ID
     * @return - the flag coding
     */
    public static FlagCoding createOlciFlagCoding(String flagId) {
        return IdepixFlagCoding.createDefaultFlagCoding(flagId);
    }

    /**
     * Provides OLCI pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    public static void setupOlciClassifBitmask(Product classifProduct) {
        IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);
    }

    public static void addOlciRadiance2ReflectanceBands(Product rad2reflProduct, Product targetProduct, String[] reflBandsToCopy) {
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

    public static Product computeRadiance2ReflectanceProduct(Product sourceProduct) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("sensor", Sensor.OLCI);
        params.put("copyNonSpectralBands", false);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), params, sourceProduct);
    }

    public static Product computeCloudTopPressureProduct(Product sourceProduct) {
        return GPF.createProduct("Snap.Idepix.Olci.Ctp", GPF.NO_PARAMS, sourceProduct);
    }

    public static double getRefinedHeightFromCtp(double ctp, double slp, double[] temperatures) {
        double height = 0.0;
        final double[] prsLevels = IdepixOlciConstants.referencePressureLevels;

        double t1;
        double t2;
        if (ctp >= prsLevels[prsLevels.length - 1]) {
            for (int i = 0; i < prsLevels.length - 1; i++) {
                if (ctp > prsLevels[0] || (ctp < prsLevels[i] && ctp > prsLevels[i + 1])) {
                    t1 = temperatures[i];
                    t2 = temperatures[i + 1];
                    final double ts = (t2 - t1) / (prsLevels[i + 1] - prsLevels[i]) * (ctp - prsLevels[i]) + t1;
                    height = getHeightFromCtp(ctp, slp, ts);
                    break;
                }
            }
        } else {
            // CTP < 1 hPa? This should never happen...
            t1 = temperatures[prsLevels.length - 2];
            t2 = temperatures[prsLevels.length - 1];
            final double ts = (t2 - t1) / (prsLevels[prsLevels.length - 2] - prsLevels[prsLevels.length - 1]) *
                    (ctp - prsLevels[prsLevels.length - 1]) + t1;
            height = getHeightFromCtp(ctp, slp, ts);
        }
        return height;
    }

    private static double getHeightFromCtp(double ctp, double p0, double ts) {
        return -ts * (Math.pow(ctp / p0, 1. / 5.255) - 1) / 0.0065;
    }
}
