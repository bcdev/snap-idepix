package org.esa.snap.idepix.modis.dataio.nc4;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.dataio.netcdf.ProfileWriteContext;
import org.esa.snap.dataio.netcdf.metadata.profiles.beam.BeamMetadataPart;

/**
 * Modification of BeamMetadataPart for IdePix MODIS purposes
 *
 * @author olafd
 */
public class IdepixModisMetadataPart extends BeamMetadataPart {
    @Override
    public void preEncode(ProfileWriteContext ctx, Product p) {
        // nothing to do here for IdePix MODIS
    }
}
