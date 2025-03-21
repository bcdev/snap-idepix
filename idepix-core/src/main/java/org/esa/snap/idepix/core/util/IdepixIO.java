package org.esa.snap.idepix.core.util;

import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.dataio.envisat.EnvisatConstants;
import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;

import java.util.regex.Pattern;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class IdepixIO {

    private IdepixIO() {
    }

    /**
     * creates a new product with the same size
     **/
    public static Product createCompatibleTargetProduct(Product sourceProduct, String name, String type,
                                                        boolean copyAllTiePoints) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyTiePoints(sourceProduct, targetProduct, copyAllTiePoints);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        return targetProduct;
    }

    /**
     * copies a geocoding from a given reference band as scene geocoding to a given product
     * todo: move to a more general place?!
     *
     * @param referenceBand - the reference band
     * @param product       - the product where the geocoding shall be copied
     */
    public static void copyGeocodingFromBandToProduct(Band referenceBand, Product product) {
        final Scene srcScene = SceneFactory.createScene(referenceBand);
        final Scene destScene = SceneFactory.createScene(product);
        if (srcScene != null && destScene != null) {
            srcScene.transferGeoCodingTo(destScene, null);
        }
    }

    public static Product cloneProduct(Product sourceProduct, boolean copySourceBands) {
        return cloneProduct(sourceProduct, sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight(), copySourceBands);
    }

    public static Product cloneProduct(Product sourceProduct, int width, int height, boolean copySourceBands) {
        Product clonedProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                width,
                height);

        ProductUtils.copyMetadata(sourceProduct, clonedProduct);
        ProductUtils.copyGeoCoding(sourceProduct, clonedProduct);
        ProductUtils.copyFlagCodings(sourceProduct, clonedProduct);
        ProductUtils.copyFlagBands(sourceProduct, clonedProduct, true);
        ProductUtils.copyMasks(sourceProduct, clonedProduct);
        clonedProduct.setStartTime(sourceProduct.getStartTime());
        clonedProduct.setEndTime(sourceProduct.getEndTime());

        if (copySourceBands) {
            // copy all bands from source product
            for (Band b : sourceProduct.getBands()) {
                if (!clonedProduct.containsBand(b.getName())) {
                    ProductUtils.copyBand(b.getName(), sourceProduct, clonedProduct, true);
                    if (isIdepixSpectralBand(b)) {
                        ProductUtils.copyRasterDataNodeProperties(b, clonedProduct.getBand(b.getName()));
                    }
                }
            }

            for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
                TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
                if (!clonedProduct.containsTiePointGrid(srcTPG.getName())) {
                    clonedProduct.addTiePointGrid(srcTPG.cloneTiePointGrid());
                }
            }
        }

        return clonedProduct;
    }

    public static boolean validateInputProduct(Product inputProduct, AlgorithmSelector algorithm) {
        return isInputValid(inputProduct) && isInputConsistentWithAlgorithm(inputProduct, algorithm);
    }

    public static boolean areAllReflectancesValid(float[] reflectance) {
        for (float aReflectance : reflectance) {
            if (Float.isNaN(aReflectance) || aReflectance <= 0.0f) {
                return false;
            }
        }
        return true;
    }

    public static void setNewBandProperties(Band band, String description, String unit, double noDataValue,
                                            boolean useNoDataValue) {
        band.setDescription(description);
        band.setUnit(unit);
        band.setNoDataValue(noDataValue);
        band.setNoDataValueUsed(useNoDataValue);
    }

    public static void copySourceBands(Product sourceProduct, Product targetProduct, String bandNameSubstring) {
        for (String bandname : sourceProduct.getBandNames()) {
            if (bandname.contains(bandNameSubstring) && !targetProduct.containsBand(bandname)) {
                ProductUtils.copyBand(bandname, sourceProduct, targetProduct, true);
            }
        }
    }

    public static void addRadianceBands(Product l1bProduct, Product targetProduct, String[] bandsToCopy) {
        for (String bandname : bandsToCopy) {
            if (!targetProduct.containsBand(bandname) && bandname.contains("radiance")) {
                ProductUtils.copyBand(bandname, l1bProduct, targetProduct, true);
            }
        }
    }

    public static boolean isMeris4thReprocessingL1bProduct(String productType) {
        return productType.startsWith("ME_1");  // todo: discuss this criterion
    }


    /// END of public ///

    static boolean isValidLandsat8Product(Product product) {
        return product.containsBand("coastal_aerosol") &&
                product.containsBand("blue") &&
                product.containsBand("green") &&
                product.containsBand("red") &&
                product.containsBand("near_infrared") &&
                product.containsBand("swir_1") &&
                product.containsBand("swir_2") &&
                product.containsBand("panchromatic") &&
                product.containsBand("cirrus") &&
                product.containsBand("thermal_infrared_(tirs)_1") &&
                product.containsBand("thermal_infrared_(tirs)_2");

    }

    private static void copyTiePoints(Product sourceProduct,
                                      Product targetProduct, boolean copyAllTiePoints) {
        if (copyAllTiePoints) {
            // copy all tie point grids to output product
            ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        } else {
            for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
                TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
                if (srcTPG.getName().equals("latitude") || srcTPG.getName().equals("longitude")) {
                    targetProduct.addTiePointGrid(srcTPG.cloneTiePointGrid());
                }
            }
        }
    }

    private static boolean isIdepixSpectralBand(Band b) {
        return b.getName().startsWith("radiance") || b.getName().startsWith("refl") ||
                b.getName().startsWith("brr") || b.getName().startsWith("rho_toa");
    }

    // TODO (mp:2022-01-13) - This should be specifcally implemented in the modules.
    //  For example AVHRR and AATSR are not valid inputs. Because we have disabled it.
    //  Also in addition it is strange if the OLCI IdePix states that L8 and Proba-V are valid.
    private static boolean isInputValid(Product inputProduct) {
        if (!isValidAvhrrProduct(inputProduct) &&
                !isValidLandsat8Product(inputProduct) &&
                !isValidProbavProduct(inputProduct) &&
                !isValidModisProduct(inputProduct) &&
                !isValidSeawifsProduct(inputProduct) &&
                !isValidViirsProduct(inputProduct) &&
                !isValidMerisProduct(inputProduct) &&
                !isValidOlciProduct(inputProduct) &&
                !isValidOlciSlstrSynergyProduct(inputProduct) &&
                !isValidVgtProduct(inputProduct)) {
            IdepixUtils.logErrorMessage("Input sensor must be either Landsat-8, MERIS, AATSR, AVHRR, " +
                    "OLCI, colocated OLCI/SLSTR, " +
                    "MODIS/SeaWiFS, PROBA-V or VGT!");
        }
        return true;
    }

    private static boolean isValidMerisProduct(Product product) {
        final boolean merisL1TypePatternMatches = EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(product.getProductType()).matches();
        // accept also ICOL L1N products...
        final boolean merisIcolTypePatternMatches = isValidMerisIcolL1NProduct(product);
        final boolean merisCCL1PTypePatternMatches = isValidMerisCCL1PProduct(product);
        // now accept also 4th reprocessing products (20210126):
        final boolean meris4thReproTypePatternMatches = isValidMeris4thReprocessingL1bProduct(product);

        return merisL1TypePatternMatches || merisIcolTypePatternMatches ||
                merisCCL1PTypePatternMatches || meris4thReproTypePatternMatches;
    }

    private static boolean isValidOlciProduct(Product product) {
//        return product.getProductType().startsWith("S3A_OL_");  // todo: clarify
        return product.getProductType().contains("OL_1");  // new products have product type 'OL_1_ERR'
    }

    private static boolean isValidOlciSlstrSynergyProduct(Product product) {
        return (product.getName().contains("S3A_SY_1")||
                product.getName().contains("S3B_SY_1"));  // todo: clarify
    }

    private static boolean isValidMerisIcolL1NProduct(Product product) {
        final String icolProductType = product.getProductType();
        if (icolProductType.endsWith("_1N")) {
            int index = icolProductType.indexOf("_1");
            final String merisProductType = icolProductType.substring(0, index) + "_1P";
            return (Pattern.compile("MER_..._1P").matcher(merisProductType).matches());
        } else {
            return false;
        }
    }

    private static boolean isValidMeris4thReprocessingL1bProduct(Product product) {
        return isMeris4thReprocessingL1bProduct(product.getProductType()); // todo: discuss this criterion
    }

    private static boolean isValidMerisCCL1PProduct(Product product) {
        return IdepixConstants.MERIS_CCL1P_TYPE_PATTERN.matcher(product.getProductType()).matches();
    }

    private static boolean isValidAvhrrProduct(Product product) {
        return product.getProductType().equalsIgnoreCase(IdepixConstants.AVHRR_L1b_PRODUCT_TYPE) ||
                product.getProductType().equalsIgnoreCase(IdepixConstants.AVHRR_L1b_USGS_PRODUCT_TYPE);
    }

    private static boolean isValidModisProduct(Product product) {
        //        return (product.getName().matches("MOD021KM.A[0-9]{7}.[0-9]{4}.[0-9]{3}.[0-9]{13}.(?i)(hdf)") ||
        //                product.getName().matches("MOD021KM.A[0-9]{7}.[0-9]{4}.[0-9]{3}.[0-9]{13}") ||
        //                product.getName().matches("A[0-9]{13}.(?i)(L1B_LAC)"));
        return (product.getName().contains("MOD021KM") || product.getName().contains("MYD021KM") ||
                //                product.getName().contains("L1B_LAC"));
                product.getName().contains("L1B_"));  // seems that we have various extensions :-(
    }

    private static boolean isValidSeawifsProduct(Product product) {
        return (product.getName().matches("S[0-9]{13}.(?i)(L1B_HRPT)") ||
                product.getName().matches("S[0-9]{13}.(?i)(L1B_GAC)") ||
                product.getName().matches("S[0-9]{13}.(?i)(L1C)"));
    }

    private static boolean isValidViirsProduct(Product product) {
        return isValidViirsSNPPProduct(product) ||
                isValidViirsNOAA20Product(product) ||
                isValidViirsNOAA21Product(product);
    }

    private static boolean isValidViirsNOAA21Product(Product product) {
        for (String expectedBandName : IdepixConstants.VIRRS_NOAA21_BAND_NAMES) {
            if (!product.containsBand(expectedBandName)) {
                return false;
            }
        }
        // Update 20250108: PML requests support for products like JPSS2_VIIRS.20190613T070000.L1C
        return product.getName().matches("JPSS2_VIIRS.[0-9]{8}T[0-9]{6}.(?i)(L1C)");
    }

    private static boolean isValidViirsNOAA20Product(Product product) {
        for (String expectedBandName : IdepixConstants.VIRRS_NOAA20_BAND_NAMES) {
            if (!product.containsBand(expectedBandName)) {
                return false;
            }
        }
        // Update 20250108: PML requests support for products like JPSS2_VIIRS.20190613T070000.L1C
        return product.getName().matches("JPSS1_VIIRS.[0-9]{8}T[0-9]{6}.(?i)(L1C)");
    }

    private static boolean isValidViirsSNPPProduct(Product product) {
        for (String expectedBandName : IdepixConstants.VIRRS_SNPP_BAND_NAMES) {
            if (!product.containsBand(expectedBandName)) {
                return false;
            }
        }
        // Update 20250108: PML requests support for products like SNPP_VIIRS.20190613T070000.L1C
        return product.getName().matches("SNPP_VIIRS.[0-9]{8}T[0-9]{6}.(?i)(L1C)");
    }

    /**
     * Provides VIIRS spectral band names, depending on product type (SNPP, NOAA20 or NOAA21)
     *
     * @param productName -
     * @return String[]
     */
    public static String[] getViirsSpectralBandNames(String productName) {
        if (productName.startsWith("SNPP_VIIRS")) {
            return IdepixConstants.VIRRS_SNPP_BAND_NAMES;
        } else if (productName.startsWith("JPSS1_VIIRS")) {
            return IdepixConstants.VIRRS_NOAA20_BAND_NAMES;
        } else if (productName.startsWith("JPSS2_VIIRS")) {
            return IdepixConstants.VIRRS_NOAA21_BAND_NAMES;
        } else {
            return null;
        }
    }


    private static boolean isValidProbavProduct(Product product) {
        return product.getProductType().startsWith(IdepixConstants.PROBAV_PRODUCT_TYPE_PREFIX);
    }

    private static boolean isValidVgtProduct(Product product) {
        return product.getProductType().startsWith(IdepixConstants.SPOT_VGT_PRODUCT_TYPE_PREFIX);
    }

    private static boolean isInputConsistentWithAlgorithm(Product sourceProduct, AlgorithmSelector algorithm) {
        switch (algorithm) {
            case AVHRR:
                return (isValidAvhrrProduct(sourceProduct));
            case LANDSAT8:
                return (isValidLandsat8Product(sourceProduct));
            case MODIS:
                return (isValidModisProduct(sourceProduct));
            case PROBAV:
                return (isValidProbavProduct(sourceProduct));
            case SEAWIFS:
                return (isValidSeawifsProduct(sourceProduct));
            case VIIRS:
                return (isValidViirsProduct(sourceProduct));
            case MERIS:
                return (isValidMerisProduct(sourceProduct));
            case OLCI:
                return (isValidOlciProduct(sourceProduct));
            case OLCISLSTR:
                return (isValidOlciSlstrSynergyProduct(sourceProduct));
            case VGT:
                return (isValidVgtProduct(sourceProduct));
            default:
                throw new OperatorException(String.format("Algorithm %s not supported.", algorithm));
        }
    }

}
