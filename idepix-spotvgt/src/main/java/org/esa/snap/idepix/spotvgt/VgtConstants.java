package org.esa.snap.idepix.spotvgt;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * Constants for IdePix VGT algorithm
 *
 * @author olafd
 */
class VgtConstants {
    static final int IDEPIX_WATER = IdepixConstants.NUM_DEFAULT_FLAGS + 1;
    static final int IDEPIX_CLEAR_LAND = IdepixConstants.NUM_DEFAULT_FLAGS + 2;
    static final int IDEPIX_CLEAR_WATER = IdepixConstants.NUM_DEFAULT_FLAGS + 3;

    static final String IDEPIX_CLEAR_LAND_DESCR_TEXT = "Clear land pixels";
    static final String IDEPIX_CLEAR_WATER_DESCR_TEXT = "Clear water pixels";
    static final String IDEPIX_WATER_DESCR_TEXT = "Water pixels";
}
