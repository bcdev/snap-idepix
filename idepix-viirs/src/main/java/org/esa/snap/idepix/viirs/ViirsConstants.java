package org.esa.snap.idepix.viirs;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * Constants for IdePix VIIRS algorithm
 *
 * @author olafd
 */
class ViirsConstants {

    static final int IDEPIX_MIXED_PIXEL = IdepixConstants.NUM_DEFAULT_FLAGS + 1;

    static final String IDEPIX_MIXED_PIXEL_DESCR_TEXT = "Mixed pixel";

    // debug bands:
    static final String BRIGHTNESS_BAND_NAME = "brightness_value";
    static final String NDSI_BAND_NAME = "ndsi_value";
}
