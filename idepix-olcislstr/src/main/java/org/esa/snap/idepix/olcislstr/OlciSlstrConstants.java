package org.esa.snap.idepix.olcislstr;

/**
 * Constants for IdePix OLCI algorithm
 *
 * @author olafd
 */
class OlciSlstrConstants {

    /* Level 1 Quality Flags Positions */
    static final int L1_F_LAND = 31;
    static final int L1_F_INVALID = 25;
//    static final int L1_F_GLINT = 22;

    /* SLSTR Cloud AN Flags Positions */
    static final int CLOUD_AN_F_137_THRESH = 1;
    static final int CLOUD_AN_F_GROSS_CLOUD = 7;

    static final String OLCI_QUALITY_FLAGS_BAND_NAME = "quality_flags";
    static final String SLSTR_CLOUD_AN_FLAG_BAND_NAME = "cloud_an";

    // todo: RENOVATION: code duplication, move to core
    static final double[] referencePressureLevels = {
            1000., 950., 925., 900., 850., 800., 700.,
            600., 500., 400., 300., 250., 200., 150.,
            100., 70., 50., 30., 20., 10., 7., 5., 3., 2., 1.};

    public static final String OLCI_ALTITUDE_BAND_NAME = "altitude";
}
