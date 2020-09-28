package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
//import org.esa.s3tbx.idepix.core.IdepixFlagCoding;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflOp;
import org.esa.s3tbx.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.ResourceInstaller;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;
import org.esa.snap.core.datamodel.Mask;

import java.awt.Color;
import java.text.ParseException;
import java.util.Random;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for IdePix OLCI
 *
 * @author olafd
 */

class IdepixOlciUtils {

    /**
     * Installs auxiliary data, here lookup tables.
     *
     * @return - the auxdata path for lookup tables.
     * @throws IOException -
     */
    static String installAuxdataNNCtp() throws IOException {
        Path auxdataDirectory = SystemUtils.getAuxDataPath().resolve("idepix_olci/nn_ctp");
        final Path sourceDirPath = ResourceInstaller.findModuleCodeBasePath(CtpOp.class).resolve("auxdata/nn_ctp");
        final ResourceInstaller resourceInstaller = new ResourceInstaller(sourceDirPath, auxdataDirectory);
        resourceInstaller.install(".*", ProgressMonitor.NULL);
        return auxdataDirectory.toString();
    }


    /**
     * Provides OLCI pixel classification flag coding
     *
     * @return - the flag coding
     */
    static FlagCoding createOlciFlagCoding() {
        FlagCoding olciFlags = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
        olciFlags.addFlag("IDEPIX_WATER_PROCESSABLE", BitSetter.setFlag(0, IdepixOlciConstants.IDEPIX_WATER_PROCESSABLE),
                IdepixOlciConstants.IDEPIX_WATER_PROCESSABLE_DESCR_TEXT);
        return olciFlags;
    }

    /**
     * Provides OLCI pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    static void setupOlciClassifBitmask(Product classifProduct) {
//        IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);

        int index = IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);

        int w = classifProduct.getSceneRasterWidth();
        int h = classifProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random();

        mask = Mask.BandMathsType.create("IDEPIX_WATER_PROCESSABLE", IdepixOlciConstants.IDEPIX_WATER_PROCESSABLE_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_WATER_PROCESSABLE",
                IdepixFlagCoding.getRandomColour(r), 0.5f);
        classifProduct.getMaskGroup().add(index, mask);

    }

    static void addOlciRadiance2ReflectanceBands(Product rad2reflProduct, Product targetProduct, String[] reflBandsToCopy) {
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

    static void addOlcirBRRBands(Product rBRRProduct, Product targetProduct) {
        String [] rBRRBandname = new String[]{"01", "04", "06", "08", "17"};
        for (int i = 0; i < rBRRBandname.length; i++) {
            String bandname = "rBRR_" + rBRRBandname[i];
            if (rBRRProduct.containsBand(bandname) && !targetProduct.containsBand(bandname)) {
                ProductUtils.copyBand(bandname, rBRRProduct, targetProduct, true);
                targetProduct.getBand(bandname).setUnit("");
            }

        }
    }

    static Product computeRadiance2ReflectanceProduct(Product sourceProduct) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("sensor", Sensor.OLCI);
        params.put("copyNonSpectralBands", false);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), params, sourceProduct);
    }

    static Product computeRayleighCorrectedProduct(Product sourceProduct) {
        Map<String, Product> raylSourceProducts = new HashMap<>();
        raylSourceProducts.put("sourceProduct", sourceProduct);
        Map<String, Object> params = new HashMap<>();

        params.put("sourceBandNames", "Oa01_radiance,Oa04_radiance,Oa06_radiance,Oa08_radiance,Oa17_radiance");
        params.put("computeTaur", false);
        params.put("computeRBrr", true);
        params.put("computeRtoaNg", false);
        params.put("computeRtoa", false);
        params.put("addAirMass", false);

        return GPF.createProduct("RayleighCorrection", params, raylSourceProducts);
    }


    static Product computeCloudTopPressureProduct(Product sourceProduct,
                                                  Product o2CorrProduct,
                                                  String alternativeNNDirPath) {
        Map<String, Product> ctpSourceProducts = new HashMap<>();
        ctpSourceProducts.put("sourceProduct", sourceProduct);
        ctpSourceProducts.put("o2CorrProduct", o2CorrProduct);
        Map<String, Object> params = new HashMap<>(2);
        params.put("alternativeNNDirPath", alternativeNNDirPath);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CtpOp.class), params, ctpSourceProducts);
    }

    static Polygon createPolygonFromCoordinateArray(double[][] coordArray) {
        final Coordinate[] coordinates = new Coordinate[coordArray.length];
        for (int i = 0; i < coordinates.length; i++) {
            final double[] coord = coordArray[i];
            coordinates[i] = new Coordinate(coord[0], coord[1]);
        }
        final GeometryFactory factory = new GeometryFactory();
        return factory.createPolygon(factory.createLinearRing(coordinates), null);
    }

    static boolean isCoordinateInsideGeometry(Coordinate coord, Geometry g, GeometryFactory gf) {
        return gf.createPoint(coord).within(g);
    }

    static Geometry computeProductGeometry(Product product) {
        try {
            final GeneralPath[] paths = ProductUtils.createGeoBoundaryPaths(product);
            final Polygon[] polygons = new Polygon[paths.length];
            final GeometryFactory factory = new GeometryFactory();
            for (int i = 0; i < paths.length; i++) {
                polygons[i] = convertAwtPathToJtsPolygon(paths[i], factory);
            }
            final DouglasPeuckerSimplifier peuckerSimplifier = new DouglasPeuckerSimplifier(
                    polygons.length == 1 ? polygons[0] : factory.createMultiPolygon(polygons));
            return peuckerSimplifier.getResultGeometry();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Computes apparent sun azimuth angle to be used e.g. for cloud shadow computation.
     * Algorithm proposed by DM, 20190226.
     *
     * @param sza - sun zenith (deg)
     * @param saa - sun azimuth (deg)
     * @param oza - view zenith (deg)
     * @param oaa - view azimuth (deg)
     * @param lat - latitude (deg)
     * @return the apparent saa (deg)
     */
    static double computeApparentSaa(double sza, double saa, double oza, double oaa, double lat) {
        final double szaRad = sza * MathUtils.DTOR;
        final double ozaRad = oza * MathUtils.DTOR;

        double deltaPhi;
        if (oaa < 0.0) {
            deltaPhi = 360.0 - Math.abs(oaa) - saa;
        } else {
            deltaPhi = saa - oaa;
        }
        final double deltaPhiRad = deltaPhi * MathUtils.DTOR;
        final double numerator = Math.tan(szaRad) - Math.tan(ozaRad) * Math.cos(deltaPhiRad);
        final double denominator = Math.sqrt(Math.tan(ozaRad) * Math.tan(ozaRad) + Math.tan(szaRad) * Math.tan(szaRad) -
                2.0 * Math.tan(szaRad) * Math.tan(ozaRad) * Math.cos(deltaPhiRad));

        double delta = Math.acos(numerator / denominator);
        if (saa > 270. || saa < 90){
            //sun towards the South
            delta *= -1.0;
        }
        if (oaa < 0.0) {
            return saa - delta * MathUtils.RTOD;
        } else {
            return saa + delta * MathUtils.RTOD;
        }
    }

    static ProductData.UTC getStartTimeFromOlciFileName(String filename) {
        // filename e.g. S3A_OL_1_EFR____20170522T090453_20170522T090653_20171018T123841_0119_018_050______MR1_R_NT_002.SEN3.extract_5x5.nc
        final String startTimeString = filename.substring(16, 24) + " " + filename.substring(25, 31);
        try {
            return ProductData.UTC.parse(startTimeString, "yyyyMMdd HHmmss");
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    static ProductData.UTC getEndTimeFromOlciFileName(String filename) {
        // filename e.g. S3A_OL_1_EFR____20170522T090453_20170522T090653_20171018T123841_0119_018_050______MR1_R_NT_002.SEN3.extract_5x5.nc
        final String endTimeString = filename.substring(32, 40) + " " + filename.substring(41, 47);
        try {
            return ProductData.UTC.parse(endTimeString, "yyyyMMdd HHmmss");
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Polygon convertAwtPathToJtsPolygon(Path2D path, GeometryFactory factory) {
        final PathIterator pathIterator = path.getPathIterator(null);
        ArrayList<double[]> coordList = new ArrayList<>();
        int lastOpenIndex = 0;
        while (!pathIterator.isDone()) {
            final double[] coords = new double[6];
            final int segType = pathIterator.currentSegment(coords);
            if (segType == PathIterator.SEG_CLOSE) {
                // we should only detect a single SEG_CLOSE
                coordList.add(coordList.get(lastOpenIndex));
                lastOpenIndex = coordList.size();
            } else {
                coordList.add(coords);
            }
            pathIterator.next();
        }
        final Coordinate[] coordinates = new Coordinate[coordList.size()];
        for (int i1 = 0; i1 < coordinates.length; i1++) {
            final double[] coord = coordList.get(i1);
            coordinates[i1] = new Coordinate(coord[0], coord[1]);
        }

        return factory.createPolygon(factory.createLinearRing(coordinates), null);
    }


    static double getRefinedHeightFromCtp(double ctp, double slp, double[] temperatures) {
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

    /**
     * Returns month (1-12) from given start/stop time
     *
     * @param startStopTime - start/stop time given as {@link ProductData.UTC}
     * @return month
     */
    static int getMonthFromStartStopTime(ProductData.UTC startStopTime) {
        return startStopTime.getAsCalendar().get(Calendar.MONTH) + 1;
    }

    private static double getHeightFromCtp(double ctp, double p0, double ts) {
        return -ts * (Math.pow(ctp / p0, 1. / 5.255) - 1) / 0.0065;
    }
}
