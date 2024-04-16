package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.math3.fitting.PolynomialFitter;
import eu.esa.opt.processor.rad2refl.Rad2ReflConstants;
import eu.esa.opt.processor.rad2refl.Rad2ReflOp;
import eu.esa.opt.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.*;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

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
        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
        flagCoding.addFlag("IDEPIX_MOUNTAIN_SHADOW", BitSetter.setFlag(0,
                        IdepixOlciConstants.IDEPIX_MOUNTAIN_SHADOW),
                IdepixOlciConstants.IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT);
        return flagCoding;
    }

    /**
     * Provides OLCI pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    static void setupOlciClassifBitmask(Product classifProduct) {
        int index = IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);

        int w = classifProduct.getSceneRasterWidth();
        int h = classifProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random(1234567);

        mask = Mask.BandMathsType.create("IDEPIX_MOUNTAIN_SHADOW", IdepixOlciConstants.IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT, w, h,
                "pixel_classif_flags.IDEPIX_MOUNTAIN_SHADOW",
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

    static Product computeRadiance2ReflectanceProduct(Product sourceProduct) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("sensor", Sensor.OLCI);
        params.put("copyNonSpectralBands", false);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), params, sourceProduct);
    }

    static Product computeCloudTopPressureProduct(Product sourceProduct, Product o2CorrProduct, String alternativeCtpNNDir, boolean outputCtp) {
        Map<String, Product> ctpSourceProducts = new HashMap<>();
        ctpSourceProducts.put("sourceProduct", sourceProduct);
        ctpSourceProducts.put("o2CorrProduct", o2CorrProduct);
        Map<String, Object> params = new HashMap<>(2);
        params.put("alternativeCtpNNDir", alternativeCtpNNDir);
        params.put("outputCtp", outputCtp);
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
            final GeneralPath[] paths = GeoUtils.createGeoBoundaryPaths(product);
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
     * @return the apparent saa (deg)
     */
    static double computeApparentSaa(double sza, double saa, double oza, double oaa) {
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
// Sun in the North (Southern hemisphere), change sign!
// todo: conflict between master and cglops
//        if (saa < 270 && saa > 90){
        if (saa > 270. || saa < 90) {
            delta = -delta;
        }
        if (oaa < 0.0) {
            return saa - delta * MathUtils.RTOD;
        } else {
            return saa + delta * MathUtils.RTOD;
        }
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

    static boolean isReducedResolution(Product sourceProduct) {
//        return sourceProduct.getName().matches("S3.?_OL_1_.*RR_.*.SEN3");
        // less strict to allow subsets:
        return sourceProduct.getProductType().endsWith("RR");
    }

    static boolean isFullResolution(Product sourceProduct) {
//        return sourceProduct.getName().matches("S3.?_OL_1_.*FR_.*.SEN3");
        // less strict to allow subsets:
        return sourceProduct.getProductType().endsWith("FR");
    }

    static float[] interpolateViewAngles(PolynomialFitter curveFitter1, PolynomialFitter curveFitter2,
                                         float[] viewAngleOrig, int[] nx, int nxChange) {
        float[] viewAngleInterpol = viewAngleOrig.clone();

        curveFitter1.clearObservations();
        for (int x = nx[0]; x < nx[1]; x++) {
            curveFitter1.addObservedPoint(x * 1.0f, viewAngleOrig[x]);
        }
        final double[] fit1 = curveFitter1.fit(IdepixOlciConstants.POLYNOM_FIT_INITIAL);

        curveFitter2.clearObservations();
        for (int x = nx[2]; x < nx[3]; x++) {
            curveFitter2.addObservedPoint(x * 1.0f, viewAngleOrig[x]);
        }
        final double[] fit2 = curveFitter2.fit(IdepixOlciConstants.POLYNOM_FIT_INITIAL);

        for (int x = nx[1]; x < nxChange; x++) {
            viewAngleInterpol[x] = (float) (fit1[0] + fit1[1] * x + fit1[2] * x * x);
        }
        for (int x = nxChange; x < nx[2]; x++) {
            viewAngleInterpol[x] = (float) (fit2[0] + fit2[1] * x + fit2[2] * x * x);
        }

        return viewAngleInterpol;
    }

}
