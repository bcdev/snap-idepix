package org.esa.snap.idepix.meris;

import org.apache.commons.math3.fitting.PolynomialFitter;
import eu.esa.opt.processor.rad2refl.Rad2ReflConstants;
import eu.esa.opt.processor.rad2refl.Rad2ReflOp;
import eu.esa.opt.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.math.MathUtils;
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

        flagCoding.addFlag("IDEPIX_MOUNTAIN_SHADOW", BitSetter.setFlag(0,
                        IdepixMerisConstants.IDEPIX_MOUNTAIN_SHADOW),
                IdepixMerisConstants.IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT);

        flagCoding.addFlag("IDEPIX_GLINT_RISK", BitSetter.setFlag(0,
                        IdepixMerisConstants.IDEPIX_GLINT_RISK),
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

        mask = Mask.BandMathsType.create("IDEPIX_MOUNTAIN_SHADOW", IdepixMerisConstants.IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_MOUNTAIN_SHADOW",
                IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

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

    static double computeApparentSaa(double sza, double saa, double oza, double oaa) {
        final double tanSza = Math.tan(sza * MathUtils.DTOR);
        final double tanOza = Math.tan(oza * MathUtils.DTOR);
        final double cosDeltaPhi = Math.cos((saa - oaa) * MathUtils.DTOR);
        final double numerator = tanSza - tanOza * cosDeltaPhi;
        final double denominator = Math.sqrt(tanOza * tanOza + tanSza * tanSza -
                2.0 * tanSza * tanOza * cosDeltaPhi);
        double delta = Math.acos(numerator / denominator);
        // Sun in the North (Southern hemisphere), change sign!
        if (saa > 270. || saa < 90){
            delta = -delta;
        }
        if (oaa < 0.0) {
            return saa - delta * MathUtils.RTOD;
        } else {
            return saa + delta * MathUtils.RTOD;
        }
    }

    static boolean isFullResolution(Product sourceProduct) {
        // less strict to allow subsets:
        return sourceProduct.getProductType().contains("_FR");
    }

    static boolean isReducedResolution(Product sourceProduct) {
        // less strict to allow subsets:
        return sourceProduct.getProductType().contains("_RR");
    }
    static float[] interpolateViewAngles(PolynomialFitter curveFitter1, PolynomialFitter curveFitter2,
                                         float[] viewAngleOrig, int[] nx, int nxChange) {
        float[] viewAngleInterpol = viewAngleOrig.clone();

        curveFitter1.clearObservations();
        for (int x = nx[0]; x < nx[1]; x++) {
            curveFitter1.addObservedPoint(x * 1.0f, viewAngleOrig[x]);
        }
        final double[] fit1 = curveFitter1.fit(IdepixMerisConstants.POLYNOM_FIT_INITIAL);

        curveFitter2.clearObservations();
        for (int x = nx[2]; x < nx[3]; x++) {
            curveFitter2.addObservedPoint(x * 1.0f, viewAngleOrig[x]);
        }
        final double[] fit2 = curveFitter2.fit(IdepixMerisConstants.POLYNOM_FIT_INITIAL);

        for (int x = nx[1]; x < nxChange; x++) {
            viewAngleInterpol[x] = (float) (fit1[0] + fit1[1] * x + fit1[2] * x * x);
        }
        for (int x = nxChange; x < nx[2]; x++) {
            viewAngleInterpol[x] = (float) (fit2[0] + fit2[1] * x + fit2[2] * x * x);
        }

        return viewAngleInterpol;
    }

    static boolean isLandPixel(int x, int y, GeoPos geoPos, Tile l1FlagsTile, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (geoPos.lat > -58f) {
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                return waterFraction == 0;
            } else {
                return l1FlagsTile.getSampleBit(x, y, IdepixMerisConstants.L1_F_LAND);
            }
        } else {
            return l1FlagsTile.getSampleBit(x, y, IdepixMerisConstants.L1_F_LAND);
        }
    }

    static boolean isCoastlinePixel(GeoPos geoPos, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        // values bigger than 100 indicate no data
        // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502)
        return geoPos.lat > -58f && waterFraction < 100 && waterFraction > 0;
    }
}
