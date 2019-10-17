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

    public static FlagCoding createAvhrrAcFlagCoding(String flagId) {

        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(flagId);

        // additional flags for AVHRR-AC (tests):
        flagCoding.addFlag("F_REFL1_ABOVE_THRESH", BitSetter.setFlag(0, IdepixConstants.NUM_DEFAULT_FLAGS + 1), null);
        flagCoding.addFlag("F_REFL2_ABOVE_THRESH", BitSetter.setFlag(0, IdepixConstants.NUM_DEFAULT_FLAGS + 2), null);
        flagCoding.addFlag("F_RATIO_REFL21_ABOVE_THRESH", BitSetter.setFlag(0, IdepixConstants.NUM_DEFAULT_FLAGS + 3), null);
        flagCoding.addFlag("F_RATIO_REFL31_ABOVE_THRESH", BitSetter.setFlag(0, IdepixConstants.NUM_DEFAULT_FLAGS + 4), null);
        flagCoding.addFlag("F_BT4_ABOVE_THRESH", BitSetter.setFlag(0, IdepixConstants.NUM_DEFAULT_FLAGS + 5), null);
        flagCoding.addFlag("F_BT5_ABOVE_THRESH", BitSetter.setFlag(0, IdepixConstants.NUM_DEFAULT_FLAGS + 6), null);

        return flagCoding;
    }


    public static int setupAvhrrAcClassifBitmask(Product classifProduct) {

        int index = IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);

        int w = classifProduct.getSceneRasterWidth();
        int h = classifProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random(124567);

        // tests:
        mask = Mask.BandMathsType.create("F_REFL1_ABOVE_THRESH", "TOA reflectance Channel 1 above threshold", w, h,
                                         "pixel_classif_flags.F_REFL1_ABOVE_THRESH",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("F_REFL2_ABOVE_THRESH", "TOA reflectance Channel 2 above threshold", w, h,
                                         "pixel_classif_flags.F_REFL2_ABOVE_THRESH",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("F_RATIO_REFL21_ABOVE_THRESH", "Ratio of TOA reflectance Channel 2/1 above threshold", w, h,
                                         "pixel_classif_flags.F_RATIO_REFL21_ABOVE_THRESH",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("F_RATIO_REFL31_ABOVE_THRESH", "Ratio of TOA reflectance Channel 3/1 above threshold", w, h,
                                         "pixel_classif_flags.F_RATIO_REFL31_ABOVE_THRESH",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("F_BT4_ABOVE_THRESH", "Brightness temperature Channel 4 above threshold", w, h,
                                         "pixel_classif_flags.F_BT4_ABOVE_THRESH",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create("F_BT5_ABOVE_THRESH", "Brightness temperature Channel 5 above threshold", w, h,
                                         "pixel_classif_flags.F_BT5_ABOVE_THRESH",
                                         IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index++, mask);

        return index;
    }

    public static Calendar getProductDateAsCalendar(String ddmmyy) {
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

    public static boolean anglesInvalid(double sza, double vza, double saa, double vaa) {
        // todo: we have a discontinuity in angle retrieval at sza=90deg. Check!
//        final double eps = 1.E-6;
//        final boolean szaInvalid = sza < 90.0 + eps && sza > 90.0 - eps;
//        final boolean szaInvalid = sza  > 85.0; // GK, 20150326
//        final boolean szaInvalid = sza > 70.0; // GK, 20150922
        final boolean szaInvalid = sza > 80.0; // GK, 20191017

        final boolean vzaInvalid = Double.isNaN(vza);
        final boolean saaInvalid = Double.isNaN(saa);
        final boolean vaaInvalid = Double.isNaN(vaa);

        return szaInvalid || saaInvalid || vzaInvalid || vaaInvalid;
    }

    public static double convertRadianceToBt(String noaaId, AvhrrAuxdata.Rad2BTTable rad2BTTable, double radianceOrig, int ch, float waterFraction) {
        final double c1 = 1.1910659E-5;
        final double c2 = 1.438833;

        double rad = rad2BTTable.getA(ch) * radianceOrig +
                rad2BTTable.getB(ch) * radianceOrig * radianceOrig + rad2BTTable.getD(ch);
        double nuStart = rad2BTTable.getNuMid(ch);
        double tRef = c2 * nuStart / (Math.log(1.0 + c1 * nuStart * nuStart * nuStart / rad));

        double nuFinal = nuStart;
        switch (noaaId) {
            case "7":
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
            default:
                throw new OperatorException("AVHRR version " + noaaId + " not supported.");
        }

        return c2 * nuFinal / (Math.log(1.0 + c1 * nuFinal * nuFinal * nuFinal / rad));
    }


    public static double convertBtToRadiance(String noaaId, AvhrrAuxdata.Rad2BTTable rad2BTTable, double bt, int ch, float waterFraction) {
        final double c1 = 1.1910659E-5;
        final double c2 = 1.438833;

        double tRef = bt;
        double nuStart = rad2BTTable.getNuMid(ch);
        double nuFinal = nuStart;

        switch (noaaId) {
            case "7":
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
            default:
                throw new OperatorException("AVHRR version " + noaaId + " not supported.");
        }

        double rad = (c1 * nuFinal * nuFinal * nuFinal) / (Math.exp(c2 * nuFinal / bt) - 1.0);

        // todo: test the part which should work as in other direction

        //                  A	    B	        D	    rad 	rad_ori
        //        7	    3	1	    0	        0	    121 	121
        //              4	0.8978	0.0004819	5.25	121 	121.0598375
        //              5	0.9368	0.0002425	3.93	121 	121.167507
        //        11	3	1	    0	        0	    121 	121
        //              4	0.8412	0.0008739	7.21	121	    120.2490977
        //              5	0.946	0.0002504	2.92	121 	120.9482344
        //        14	3	1.00359	0	        -0.0031	121 	120.5702528
        //              4	0.9237	0.0003833	3.72	121	    120.9020137
        //              5	0.9619	0.0001742	2	    121	    121.0593963


        double radOri1 = Double.NaN;
        double radOri2 = Double.NaN;

        switch (noaaId) {
            case "11":
            case "7":
                radOri1 = rad;
                radOri2 = radOri1;
                break;
            case "14":
                radOri1 = (rad - rad2BTTable.getD(ch)) / rad2BTTable.getA(ch);
                radOri2 = radOri1;
                break;
            default:
                throw new OperatorException("AVHRR version " + noaaId + " not supported.");
        }

        if (ch > 3) {
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

}
