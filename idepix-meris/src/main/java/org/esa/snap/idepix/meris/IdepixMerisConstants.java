package org.esa.snap.idepix.meris;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * Constants for IdePix MERIS algorithm
 *
 * @author olafd
 */
public class IdepixMerisConstants {

    public static final int IDEPIX_GLINT_RISK = IdepixConstants.NUM_DEFAULT_FLAGS + 1;

    public static final int MERIS_NUM_TEMPERATURE_PROFILES = 20;

    static final String IDEPIX_GLINT_RISK_DESCR_TEXT = "Glint risk pixel";

    /* Level 1 Flags Positions */
    static final int L1_F_LAND = 4;
    static final int L1_F_INVALID = 7;
}
