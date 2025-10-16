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

    static final String[] SLSTR_BT_BAND_NAMES = new String[]{"S7_bt_in", "S8_bt_in", "S9_bt_in",
            "S7_bt_io", "S8_bt_io", "S9_bt_io"};

}
