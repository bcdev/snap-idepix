package org.esa.snap.idepix.slstr;

/**
 * Constants for IdePix SLSTR algorithm
 *
 * @author olafd
 */
class IdepixSlstrConstants {

    // todo: adapt

    /* Level 1 Quality Flags Positions */
    static final int L1_F_LAND = 31;
    static final int L1_F_INVALID = 25;
//    static final int L1_F_GLINT = 22;

    /* SLSTR Cloud AN Flags Positions */
    static final int CLOUD_AN_F_137_THRESH = 1;
    static final int CLOUD_AN_F_GROSS_CLOUD = 7;

    static final String SLSTR_CLOUD_AN_FLAG_BAND_NAME = "cloud_an";

}
