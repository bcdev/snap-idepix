package org.esa.snap.idepix.olcislstr;

import com.bc.ceres.core.ProgressMonitor;
import eu.esa.opt.processor.rad2refl.Rad2ReflOp;
import eu.esa.opt.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.Guardian;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.ResourceInstaller;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for IdePix OLCI
 *
 * @author olafd
 */

class OlciSlstrUtils {

    /**
     * Installs auxiliary data, here lookup tables.
     * todo: RENOVATION: same functionality as in IdepixOlciUtils in OLCI module, move to core or merge olci and olcislstr modules
     *
     * @return - the auxdata path for lookup tables.
     * @throws IOException -
     */
    static String installAuxdataNNCtp() throws IOException {
        Path auxdataDirectory = SystemUtils.getAuxDataPath().resolve("idepix_olcislstr/nn_ctp");
        final Path sourceDirPath = ResourceInstaller.findModuleCodeBasePath(OlciSlstrCtpOp.class).resolve("auxdata/nn_ctp");
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
        return IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
    }

    /**
     * Provides OLCI pixel classification flag bitmask
     *
     * @param classifProduct - the pixel classification product
     */
    static void setupOlciClassifBitmask(Product classifProduct) {
        IdepixFlagCoding.setupDefaultClassifBitmask(classifProduct);
    }

    static Product computeRadiance2ReflectanceProduct(Product sourceProduct, Sensor sensor) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("sensor", sensor);
        params.put("copyNonSpectralBands", false);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), params, sourceProduct);
    }

    /**
     * Copies SLSTR cloud flag bands from source to target product.
     *
     * @param sourceProduct - the SYN source product
     * @param targetProduct - the target product
     */
    public static void copySlstrCloudFlagBands(Product sourceProduct, Product targetProduct) {
        Guardian.assertNotNull("source", sourceProduct);
        Guardian.assertNotNull("target", targetProduct);
        if (sourceProduct.getFlagCodingGroup().getNodeCount() > 0) {

            // loop over bands and check if they have a flags coding attached
            for (int i = 0; i < sourceProduct.getNumBands(); i++) {
                Band sourceBand = sourceProduct.getBandAt(i);
                String bandName = sourceBand.getName();
                // copy SLSTR 'cloud_an' flag band only, 20220113
                if (bandName.startsWith("cloud_an") && sourceBand.isFlagBand() && targetProduct.getBand(bandName) == null) {
                    ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
                }
            }

            // first the bands have to be copied and then the masks
            // other wise the referenced bands, e.g. flag band, is not contained in the target product
            // and the mask is not copied
            copySlstrCloudMasks(sourceProduct, targetProduct);
        }
    }

    private static void copySlstrCloudMasks(Product sourceProduct, Product targetProduct) {
        ProductNodeGroup<Mask> sourceMaskGroup = sourceProduct.getMaskGroup();

        for(int i = 0; i < sourceMaskGroup.getNodeCount(); ++i) {
            Mask mask = sourceMaskGroup.get(i);
            System.out.println("mask: " + mask.getName());
            // SLSTR 'cloud_an' flag band only, 20220113
            final boolean isSlstrCloudMask = mask.getName().startsWith("cloud_an");
            if (isSlstrCloudMask) {
                System.out.println("true");
            }
            if (isSlstrCloudMask && !targetProduct.getMaskGroup().contains(mask.getName()) && mask.getImageType().canTransferMask(mask, targetProduct)) {
                mask.getImageType().transferMask(mask, targetProduct);
            }
        }

    }

    /**
     * Computes apparent sun azimuth angle to be used e.g. for cloud shadow computation.
     * Algorithm proposed by DM, 20190226.
     * TODO: move to core module, it's not sensor dependent. (Also applies for the same method in OLCI module.)
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
        if (saa > 270. || saa < 90){
            delta = -1.0 * delta;
        }
        if (oaa < 0.0) {
            return saa - delta * MathUtils.RTOD;
        } else {
            return saa + delta * MathUtils.RTOD;
        }
    }

    // todo: RENOVATION: code duplication from OLCI module, move to core
    static double getRefinedHeightFromCtp(double ctp, double slp, double[] temperatures) {
        double height = 0.0;
        final double[] prsLevels = OlciSlstrConstants.referencePressureLevels;

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

    // todo: RENOVATION: code duplication from OLCI module, move to core
    private static double getHeightFromCtp(double ctp, double p0, double ts) {
        return -ts * (Math.pow(ctp / p0, 1. / 5.255) - 1) / 0.0065;
    }

    // todo: RENOVATION: code duplication from OLCI module, move to core
    static Product computeCloudTopPressureProduct(Product sourceProduct, Product o2CorrProduct) {
        Map<String, Product> ctpSourceProducts = new HashMap<>();
        ctpSourceProducts.put("sourceProduct", sourceProduct);
        ctpSourceProducts.put("o2CorrProduct", o2CorrProduct);
        Map<String, Object> params = new HashMap<>(2);
        params.put("outputCtp", false);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(OlciSlstrCtpOp.class), params, ctpSourceProducts);
    }


}
