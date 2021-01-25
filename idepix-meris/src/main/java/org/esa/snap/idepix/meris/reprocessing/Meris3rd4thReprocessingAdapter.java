package org.esa.snap.idepix.meris.reprocessing;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.dataio.envisat.EnvisatConstants;

public class Meris3rd4thReprocessingAdapter implements ReprocessingAdapter {

    @Override
    public Product convertToLowerVersion(Product inputProduct) {
        Product thirdReproProduct = new Product(inputProduct.getName(),
                inputProduct.getProductType(),
                inputProduct.getSceneRasterWidth(),
                inputProduct.getSceneRasterHeight());

        // adapt band names:
        // ## M%02d_radiance --> radiance_%d
        // ## detector_index --> detector_index
        adaptBandNamesToThirdRepro(inputProduct, thirdReproProduct);

        // adapt tie point grids:
        // TP_latitude --> latitude
        // TP_longitude --> longitude
        // TP_altitude --> dem_alt  // todo: clarify this!
        // n.a. --> dem_rough  // todo: clarify this!
        // n.a. --> lat_corr  // todo: clarify this!
        // n.a. --> lon_corr  // todo: clarify this!
        // SZA --> sun_zenith
        // OZA --> view_zenith
        // SAA --> sun_azimuth
        // OAA --> view_azimuth
        // horiz_wind_vector_1 --> zonal_wind
        // horiz_wind_vector_2 --> merid_wind
        // sea_level_pressure --> atm_press
        // total_ozone --> ozone  // todo: check units!
        // n.a. --> rel_hum  // todo: clarify this!
        adaptTiePointGridsToThirdRepro(inputProduct, thirdReproProduct);

        // adapt flag band and coding:
        // quality_flags --> l1_flags
        // ##  cosmetic (2^24) --> COSMETIC (1)
        // ##  duplicated (2^23) --> DUPLICATED (2)
        // ##  sun_glint_risk (2^22) --> GLINT_RISK (4)
        // ##  dubious (2^21) --> SUSPECT (8)
        // ##  land (2^31) --> LAND_OCEAN (16)
        // ##  bright (2^27) --> BRIGHT (32)
        // ##  coastline (2^30) --> COASTLINE (64)
        // ##  invalid (2^2^25) --> INVALID (128)
        adaptFlagBandToThirdRepro(inputProduct, thirdReproProduct);

        // adapt metadata:
        // Idepix MERIS does not use any product metadata
        // --> for the moment, copy just a few elements from element metadataSection:
        // ## acquisition period
        // ## platform
        // ## generalProductInformation
        // ## orbitReference
        // ## qualityInformation
        // ## frameSet
        // ## merisProductInformation
        // todo: discuss what else might be useful
        fillMetadataInThirdRepro(inputProduct, thirdReproProduct);


        return null;
    }

    private void fillMetadataInThirdRepro(Product inputProduct, Product thirdReproProduct) {

    }

    private void adaptFlagBandToThirdRepro(Product inputProduct, Product thirdReproProduct) {

    }

    private void adaptTiePointGridsToThirdRepro(Product inputProduct, Product thirdReproProduct) {

    }

    private void adaptBandNamesToThirdRepro(Product inputProduct, Product thirdReproProduct) {
        // adapt band names:
        // ## M%02d_radiance --> radiance_%d
        // ## detector_index --> detector_index
        Band detectorIndexTargetBand =
                ProductUtils.copyBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME,
                        inputProduct, thirdReproProduct, true);
        final Band detectorIndexSourceBand = inputProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME);
        detectorIndexTargetBand.setDescription(detectorIndexSourceBand.getDescription());
        detectorIndexTargetBand.setNoDataValueUsed(false);

        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final String inputBandName = "M" + String.format("%02d", i + 1) + "_radiance";
            final String thirdReproBandName = EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i];
            final Band radianceBand = ProductUtils.copyBand(inputBandName, inputProduct, thirdReproProduct, true);
        }
    }

    @Override
    public Product convertToHigherVersion(Product inputProduct) {
        // todo: implement
        return null;
    }
}
