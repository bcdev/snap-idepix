package org.esa.snap.idepix.c3solcislstr;

/**
 * Constants for IdePix OLCI algorithm
 *
 * @author olafd
 */
class C3SOlciSlstrConstants {

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

    public static final int NO_DATA_VALUE = -1;

    public static final double[] s2_WAVELENGTHS = {
            443.0f,     // B1   0
            490.0f,     // B2   1
            560.0f,     // B3   2
            665.0f,     // B4   3
            705.0f,     // B5   4
            740.0f,     // B6   5
            783.0f,     // B7   6
            842.0f,     // B8
            865.0f,     // B8A
            945.0f,     // B9
            1375.0f,    // B10
            1610.0f,    // B11
            2190.0f     // B12
    };
    public static final double[] OLCI_WAVELENGTHS = {
            400.,       // Oa01 	0
            412.5,      // Oa02 	1
            442.5,      // Oa03 	2-
            490.,       // Oa04 	3-
            510.,       // Oa05 	4
            560.,       // Oa06 	5-
            620.,       // 0a07 	6
            665.,       // Oa08 	7-
            673.75,     // Oa09     8
            681.25,     // Oa10     9
            708.75,     // Oa11     10-
            753.75,     // Oa12     11
            761.25,     // Oa13     12
            764.375,    // Oa14     13
            767.5,      // Oa15     14
            778.75,     // Oa16     15-
            865.,       // Oa17
            885.,       // Oa18
            900.,       // Oa19
            940.,       // Oa20
            1020.         // Oa21
    };



    public final static String[] OLCI_REFL_BAND_NAMES = new String[]{
            "Oa01_reflectance", "Oa02_reflectance", "Oa03_reflectance", "Oa04_reflectance", "Oa05_reflectance",
            "Oa06_reflectance", "Oa07_reflectance", "Oa08_reflectance", "Oa09_reflectance", "Oa10_reflectance",
            "Oa11_reflectance", "Oa12_reflectance", "Oa13_reflectance", "Oa14_reflectance", "Oa15_reflectance",
            "Oa16_reflectance", "Oa17_reflectance", "Oa18_reflectance", "Oa19_reflectance", "Oa20_reflectance", "Oa21_reflectance"
    };

}
