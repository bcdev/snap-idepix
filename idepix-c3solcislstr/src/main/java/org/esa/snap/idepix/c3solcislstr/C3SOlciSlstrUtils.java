package org.esa.snap.idepix.c3solcislstr;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.Guardian;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.ResourceInstaller;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.c3solcislstr.rad2refl.Rad2ReflConstants;
import org.esa.snap.idepix.c3solcislstr.rad2refl.C3SOlciSlstrRad2ReflOp;
import org.esa.snap.idepix.c3solcislstr.rad2refl.Sensor;
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

class C3SOlciSlstrUtils {

    /**
     * Installs auxiliary data, here lookup tables.
     * todo: RENOVATION: same functionality as in IdepixOlciUtils in OLCI module, move to core or merge olci and olcislstr modules
     *
     * @return - the auxdata path for lookup tables.
     * @throws IOException -
     */
    static String installAuxdataNNCtp() throws IOException {
        Path auxdataDirectory = SystemUtils.getAuxDataPath().resolve("idepix_olcislstr/nn_ctp");
        final Path sourceDirPath = ResourceInstaller.findModuleCodeBasePath(C3SOlciSlstrCtpOp.class).resolve("auxdata/nn_ctp");
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
        return GPF.createProduct(OperatorSpi.getOperatorAlias(C3SOlciSlstrRad2ReflOp.class), params, sourceProduct);
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
                // copy SLSTR 'cloud_an' and 'cloud_ao' flag band only, 20220113
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
            // SLSTR 'cloud_an' and 'cloud_ao' flag band only, 20220113
            final boolean isSlstrCloudAnMask = mask.getName().startsWith("cloud_an");

            if (isSlstrCloudAnMask) {
                System.out.println("true");
            }
            if (isSlstrCloudAnMask && !targetProduct.getMaskGroup().contains(mask.getName()) && mask.getImageType().canTransferMask(mask, targetProduct)) {
                mask.getImageType().transferMask(mask, targetProduct);
            }
        }
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
    static void addSlstrRadiance2ReflectanceBands(Product rad2reflProduct, Product targetProduct, String[] reflBandsToCopy) {
        for (int i = 0; i < Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES.length; i++) {
            final int suffixStart = Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES[i].indexOf("_");
            final String reflBandname = Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES[i].substring(0, suffixStart);
            final int suffixEnd = Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES[i].lastIndexOf("_");
            final String reflBandnameSuffix = Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES[i].substring(suffixEnd);
            for (String bandname : reflBandsToCopy) {
                // e.g. S1_reflectance_an
                if (!targetProduct.containsBand(bandname) && bandname.equals(reflBandname + "_reflectance" + reflBandnameSuffix)) {
                    ProductUtils.copyBand(bandname, rad2reflProduct, targetProduct, true);
                    targetProduct.getBand(bandname).setUnit("dl");
                }
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
        final double[] prsLevels = C3SOlciSlstrConstants.referencePressureLevels;

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
        return GPF.createProduct(OperatorSpi.getOperatorAlias(C3SOlciSlstrCtpOp.class), params, ctpSourceProducts);
    }

    public static double spectralSlope(double ch1, double ch2, double wl1, double wl2) {
        return (ch2 - ch1) / (wl2 - wl1);
    }


    public static double calcScatteringCos(double sza, double vza, double saa, double vaa) {
        final double sins = Math.sin(sza * MathUtils.DTOR);
        final double sinv = Math.sin(vza * MathUtils.DTOR);
        final double coss = Math.cos(sza * MathUtils.DTOR);
        final double cosv = Math.cos(vza * MathUtils.DTOR);

        // Compute the geometric conditions
        final double cosphi = Math.cos((vaa - saa) * MathUtils.DTOR);

        // cos of scattering angle
        return -coss * cosv - sins * sinv * cosphi;
    }

}
