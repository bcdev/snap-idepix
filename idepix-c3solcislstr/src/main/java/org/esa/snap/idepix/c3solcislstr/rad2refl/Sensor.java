package org.esa.snap.idepix.c3solcislstr.rad2refl;

import static org.esa.snap.idepix.c3solcislstr.rad2refl.Rad2ReflConstants.*;

/**
 * Enumeration for sensors supported in radiance/reflectance conversion.
 *
 * @author olafd
 */
public enum Sensor {

    MERIS("MERIS", MERIS_RAD_BAND_NAMES, MERIS_REFL_BAND_NAMES,
          null, MERIS_SZA_BAND_NAMES, MERIS_AUTOGROUPING_RAD_STRING, MERIS_AUTOGROUPING_REFL_STRING,
          MERIS_SOLAR_FLUXES_DEFAULT, MERIS_INVALID_PIXEL_EXPR),
    OLCI("OLCI", OLCI_RAD_BAND_NAMES, OLCI_REFL_BAND_NAMES,
         OLCI_SOLAR_FLUX_BAND_NAMES, OLCI_SZA_BAND_NAMES, OLCI_AUTOGROUPING_RAD_STRING, OLCI_AUTOGROUPING_REFL_STRING,
         OLCI_SOLAR_FLUXES_DEFAULT, OLCI_INVALID_PIXEL_EXPR),
    SLSTR_500m("SLSTR 500m", SLSTR_RAD_BAND_NAMES, SLSTR_REFL_BAND_NAMES,
          null, SLSTR_SZA_BAND_NAMES, SLSTR_AUTOGROUPING_RAD_STRING, SLSTR_AUTOGROUPING_REFL_STRING,
          SLSTR_SOLAR_FLUXES_DEFAULT, null),
    C3S_SYN_SLSTR("C3S SYN SLSTR", C3S_SYN_SLSTR_RAD_BAND_NAMES, C3S_SYN_SLSTR_REFL_BAND_NAMES,
          null, C3S_SYN_SLSTR_SZA_BAND_NAMES, C3S_SYN_SLSTR_AUTOGROUPING_RAD_STRING, C3S_SYN_SLSTR_AUTOGROUPING_REFL_STRING,
                  C3S_SYN_SLSTR_SOLAR_FLUXES_DEFAULT, null);

    private final String name;
    private final String[] radBandNames;
    private final String[] reflBandNames;
    private final String[] solarFluxBandNames;
    private final String[] szaBandNames;
    private final String radAutogroupingString;
    private final String reflAutogroupingString;
    private final float[] solarFluxesDefault;
    private final String invalidPixelExpression;

    Sensor(String name, String[] radBandNames, String[] reflBandNames,
           String[] solarFluxBandNames, String[] szaBandNames, String radAutogroupingString,
           String reflAutogroupingString, float[] solarFluxesDefault, String invalidPixelExpression) {
        this.name = name;
        this.radBandNames = radBandNames;
        this.reflBandNames = reflBandNames;
        this.solarFluxBandNames = solarFluxBandNames;
        this.szaBandNames = szaBandNames;
        this.radAutogroupingString = radAutogroupingString;
        this.reflAutogroupingString = reflAutogroupingString;
        this.solarFluxesDefault = solarFluxesDefault;
        this.invalidPixelExpression = invalidPixelExpression;
    }

    public String getName() {
        return name;
    }

    public String[] getRadBandNames() {
        return radBandNames;
    }

    public String[] getReflBandNames() {
        return reflBandNames;
    }

    public String[] getSolarFluxBandNames() {
        return solarFluxBandNames;
    }

    public String[] getSzaBandNames() {
        return szaBandNames;
    }

    public String getRadAutogroupingString() {
        return radAutogroupingString;
    }

    public String getReflAutogroupingString() {
        return reflAutogroupingString;
    }

    public float[] getSolarFluxesDefault() {
        return solarFluxesDefault;
    }

    public String getInvalidPixelExpression() {
        return invalidPixelExpression;
    }
}
