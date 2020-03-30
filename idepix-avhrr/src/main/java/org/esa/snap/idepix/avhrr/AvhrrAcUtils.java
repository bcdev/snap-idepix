package org.esa.snap.idepix.avhrr;

import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.TimeZone;

/**
 * Utility class for IdePix AVHRR-AC
 *
 * @author olafd
 */
public class AvhrrAcUtils {

    static FlagCoding createAvhrrAcFlagCoding() {

        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);

        flagCoding.addFlag("IDEPIX_INLAND_WATER", BitSetter.setFlag(0, AvhrrConstants.IDEPIX_INLAND_WATER),
                AvhrrConstants.IDEPIX_INLAND_WATER_DESCR_TEXT);
        flagCoding.addFlag("IDEPIX_WATER", BitSetter.setFlag(0, AvhrrConstants.IDEPIX_WATER),
                AvhrrConstants.IDEPIX_WATER_DESCR_TEXT);
        flagCoding.addFlag("IDEPIX_CLEAR_LAND", BitSetter.setFlag(0, AvhrrConstants.IDEPIX_CLEAR_LAND),
                AvhrrConstants.IDEPIX_CLEAR_LAND_DESCR_TEXT);
        flagCoding.addFlag("IDEPIX_CLEAR_WATER", BitSetter.setFlag(0, AvhrrConstants.IDEPIX_CLEAR_WATER),
                AvhrrConstants.IDEPIX_CLEAR_WATER_DESCR_TEXT);

        return flagCoding;
    }


    static void setupAvhrrAcClassifBitmask(Product classifProduct) {

        int index = IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);

        int w = classifProduct.getSceneRasterWidth();
        int h = classifProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random(124567);

        // tests:
        mask = Mask.BandMathsType.create("IDEPIX_INLAND_WATER", AvhrrConstants.IDEPIX_INLAND_WATER_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_INLAND_WATER",
                IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("IDEPIX_WATER", AvhrrConstants.IDEPIX_WATER_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_WATER",
                IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("IDEPIX_CLEAR_LAND", AvhrrConstants.IDEPIX_CLEAR_LAND_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_CLEAR_LAND",
                IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("IDEPIX_CLEAR_WATER", AvhrrConstants.IDEPIX_CLEAR_WATER_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_CLEAR_WATER",
                IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index, mask);


    }

    static Calendar getProductDateAsCalendar(String ddmmyy) {
        final Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        int year = Integer.parseInt(ddmmyy.substring(4, 6));
        if (year < 50) {
            year = 2000 + year;
        } else {
            year = 1900 + year;
        }
        final int month = Integer.parseInt(ddmmyy.substring(2, 4)) - 1;
        final int day = Integer.parseInt(ddmmyy.substring(0, 2));
        calendar.set(year, month, day, 12, 0, 0);
        return calendar;
    }

    static boolean anglesValid(double sza, double vza, double saa, double vaa) {
        // todo: we have a discontinuity in angle retrieval at sza=90deg. Check!
//        final double eps = 1.E-6;
//        final boolean szaInvalid = sza < 90.0 + eps && sza > 90.0 - eps;
//        final boolean szaInvalid = sza  > 85.0; // GK, 20150326
//        final boolean szaInvalid = sza > 70.0; // GK, 20150922
        final boolean szaInvalid = sza > 80.0; // GK, 20191017

        final boolean vzaInvalid = Double.isNaN(vza);
        final boolean saaInvalid = Double.isNaN(saa);
        final boolean vaaInvalid = Double.isNaN(vaa);

        return !szaInvalid && !saaInvalid && !vzaInvalid && !vaaInvalid;
    }

    static double convertRadianceToBt(String noaaId, AvhrrAuxdata.Rad2BTTable rad2BTTable, double radianceOrig, int ch, float waterFraction) {
        final double c1 = 1.1910659E-5;
        final double c2 = 1.438833;

        double rad = rad2BTTable.getA(ch) * radianceOrig +
                rad2BTTable.getB(ch) * radianceOrig * radianceOrig + rad2BTTable.getD(ch);
        double nuStart = rad2BTTable.getNuMid(ch);
        double tRef = c2 * nuStart / (Math.log(1.0 + c1 * nuStart * nuStart * nuStart / rad));

        double nuFinal = getNuFinalFromAuxdata(noaaId, rad2BTTable, ch, waterFraction, tRef);

        return c2 * nuFinal / (Math.log(1.0 + c1 * nuFinal * nuFinal * nuFinal / rad));
    }


    static double convertBtToRadiance(String noaaId, AvhrrAuxdata.Rad2BTTable rad2BTTable, double bt, int ch, float waterFraction) {
        final double c1 = 1.1910659E-5;
        final double c2 = 1.438833;

        if (ch < 3 || ch > 5) {
            throw new IllegalArgumentException(String.format("Channel '%d' not supported. Only channels 3,4 and 5 are supported, .", ch));
        }

        double nuFinal = getNuFinalFromAuxdata(noaaId, rad2BTTable, ch, waterFraction, bt);
        double rad = (c1 * nuFinal * nuFinal * nuFinal) / (Math.exp(c2 * nuFinal / bt) - 1.0);

        double radOri1;
        double radOri2;

        if (ch == 3) {
            switch (noaaId) {
                case "7":
                case "11":
                    radOri1 = rad;
                    radOri2 = radOri1;
                    break;
                case "14":
                    radOri1 = (rad - rad2BTTable.getD(ch)) / rad2BTTable.getA(ch);
                    radOri2 = radOri1;
                    break;
                case "15":
                case "16":
                case "17":
                case "18":
                case "METOP-A":
                case "19":
                case "METOP-B":
                    double pHalf = rad2BTTable.getA(ch) / rad2BTTable.getB(ch) / 2.0;
                    double q = (-rad) / rad2BTTable.getB(ch);
                    radOri1 = -pHalf - Math.sqrt((pHalf * pHalf) - q);
                    radOri2 = -pHalf + Math.sqrt((pHalf * pHalf) - q);
                    break;
                default:
                    throw new OperatorException("AVHRR version " + noaaId + " not supported.");
            }
        } else {
                    double pHalf = rad2BTTable.getA(ch) / rad2BTTable.getB(ch) / 2.0;
                    double q = (rad2BTTable.getD(ch) - rad) / rad2BTTable.getB(ch);
                    radOri1 = -pHalf - Math.sqrt((pHalf * pHalf) - q);
                    radOri2 = -pHalf + Math.sqrt((pHalf * pHalf) - q);
        }

        if (Math.max(radOri1, radOri2) >= 0) {
            return Math.max(radOri1, radOri2);
        } else {
            return rad;
        }
    }

    private static double getNuFinalFromAuxdata(String noaaId, AvhrrAuxdata.Rad2BTTable rad2BTTable,
                                                int ch, float waterFraction, double tRef) {
        double nuFinal = rad2BTTable.getNuMid(ch);
        switch (noaaId) {
            case "7":
            case "11":
                if (tRef < 225.0) {
                    nuFinal = rad2BTTable.getNuLow(ch);
                } else if (tRef >= 225.0 && tRef < 275.0) {
                    if (waterFraction == 100.0f && tRef > 270.0) {
                        // water
                        nuFinal = rad2BTTable.getNuHighWater(ch);
                    } else {
                        nuFinal = rad2BTTable.getNuMid(ch);
                    }
                } else if (tRef >= 275.0 && tRef < 320.0) {
                    if (waterFraction == 100.0f && tRef < 310.0) {
                        // water
                        nuFinal = rad2BTTable.getNuHighWater(ch);
                    } else {
                        nuFinal = rad2BTTable.getNuHighLand(ch);
                    }
                }
                break;
            case "14":
                if (tRef < 230.0) {
                    nuFinal = rad2BTTable.getNuLow(ch);
                } else if (tRef >= 230.0 && tRef < 270.0) {
                    nuFinal = rad2BTTable.getNuMid(ch);
                } else if (tRef >= 270.0 && tRef < 330.0) {
                    if (waterFraction == 100.0f && tRef < 310.0) {
                        // water
                        nuFinal = rad2BTTable.getNuHighWater(ch);
                    } else {
                        nuFinal = rad2BTTable.getNuHighLand(ch);
                    }
                }
                break;
            case "15":
            case "16":
            case "17":
            case "18":
            case "METOP-A":
            case "19":
            case "METOP-B":
                nuFinal = rad2BTTable.getNuMid(ch);
                break;
            default:
                throw new OperatorException("AVHRR version " + noaaId + " not supported.");
        }
        return nuFinal;
    }

}
