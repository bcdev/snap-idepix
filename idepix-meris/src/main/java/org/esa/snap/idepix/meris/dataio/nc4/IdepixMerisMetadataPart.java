package org.esa.snap.idepix.meris.dataio.nc4;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.dataio.netcdf.ProfileWriteContext;
import org.esa.snap.dataio.netcdf.metadata.profiles.beam.BeamMetadataPart;

/**
 * Modification of BeamMetadataPart for IdePix MERIS purposes
 *
 * @author olafd
 */
public class IdepixMerisMetadataPart extends BeamMetadataPart {
    @Override
    public void preEncode(ProfileWriteContext ctx, Product p) {
        // nothing to do here for IdePix MERIS
    }
}
