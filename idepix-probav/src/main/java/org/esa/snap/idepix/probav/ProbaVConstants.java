package org.esa.snap.idepix.probav;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * Constants for IdePix Proba-V algorithm
 *
 * @author olafd
 */
class ProbaVConstants {
    static final int IDEPIX_INLAND_WATER = IdepixConstants.NUM_DEFAULT_FLAGS + 0;
    static final int IDEPIX_WATER = IdepixConstants.NUM_DEFAULT_FLAGS + 1;
    static final int IDEPIX_CLEAR_LAND = IdepixConstants.NUM_DEFAULT_FLAGS + 2;
    static final int IDEPIX_CLEAR_WATER = IdepixConstants.NUM_DEFAULT_FLAGS + 3;

    static final String IDEPIX_CLEAR_LAND_DESCR_TEXT = "Clear land pixels";
    static final String IDEPIX_CLEAR_WATER_DESCR_TEXT = "Clear water pixels";
    static final String IDEPIX_WATER_DESCR_TEXT = "Water pixels";
    static final String IDEPIX_INLAND_WATER_DESCR_TEXT = "inland water pixels";
}
