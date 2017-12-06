package org.esa.s3tbx.idepix.algorithms.olci;

/**
 * Constants for Idepix OLCI algorithm
 *
 * @author olafd
 */
public class OlciConstants {

    /* Level 1 Quality Flags Positions */
    public static final int L1_F_LAND = 31;
    public static final int L1_F_BRIGHT = 27;
    public static final int L1_F_INVALID = 25;
    public static final int L1_F_GLINT = 22;

    public static final String OLCI_QUALITY_FLAGS_BAND_NAME = "quality_flags";

    public static final int[] CAMERA_BOUNDS_FR = {1586, 2532, 3297, 4019};
    public static final int[] CAMERA_BOUNDS_RR = {617, 808, 988, 1191};
}
