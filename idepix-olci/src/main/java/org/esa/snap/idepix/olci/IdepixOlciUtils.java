package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflOp;
import org.esa.s3tbx.processor.rad2refl.Sensor;
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

    static Product computeCloudTopPressureProduct(Product sourceProduct, Product o2CorrProduct, String alternativeNNDirPath, boolean outputCtp) {
        Map<String, Product> ctpSourceProducts = new HashMap<>();
        ctpSourceProducts.put("sourceProduct", sourceProduct);
        ctpSourceProducts.put("o2CorrProduct", o2CorrProduct);
        Map<String, Object> params = new HashMap<>(2);
        params.put("alternativeNNDirPath", alternativeNNDirPath);
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
     * @param lat - latitude (deg)
     * @return the apparent saa (deg)
     */
    static double computeApparentSaa(double sza, double saa, double oza, double oaa, double lat) {
        final double tanSza = Math.tan(sza * MathUtils.DTOR);
        final double tanOza = Math.tan(oza * MathUtils.DTOR);
        final double deltaPhi = oaa < 0.0 ? 360.0 - Math.abs(oaa) - saa : saa - oaa;
        final double cosDeltaPhi = Math.cos(deltaPhi * MathUtils.DTOR);
        final double a = tanSza - tanOza * cosDeltaPhi;
        final double b = Math.sqrt(tanOza * tanOza + tanSza * tanSza - 2.0 * tanSza * tanOza * cosDeltaPhi);
        final double delta;
        if (lat < 0.0) {
            delta = -Math.acos(a / b);
        } else {
            delta = Math.acos(a / b);
        }
        if (oaa < 0.0) {
            return saa - delta * MathUtils.RTOD;
        } else {
            return saa + delta * MathUtils.RTOD;
        }
    }

    static double getRefinedHeightFromCtp(double ctp, double slp, double[] temperatures) {
        final double[] prsLevels = IdepixOlciConstants.referencePressureLevels;
        final int i = binarySearch(prsLevels, ctp);
        final double t1 = temperatures[i + 1];
        final double t2 = temperatures[i];
        final double ts = (t2 - t1) / (prsLevels[i] - prsLevels[i + 1]) * (ctp - prsLevels[i + 1]) + t1;
        return getHeightFromCtp(ctp, slp, ts);
    }

    private static int binarySearch(double[] descending, double value) {
        final int n = descending.length;
        if (value > descending[0]) {
            return 0;
        }
        if (value < descending[n - 1]) {
            return n - 2;
        }
        int up = 0;
        int lo = n - 1;
        while (lo > up + 1) {
            final int m = (up + lo) >> 1;
            if (value > descending[m]) {
                lo = m;
            } else {
                up = m;
            }
        }
        return up; // return the index of the smallest sequence element, which is larger than the given value
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
}
