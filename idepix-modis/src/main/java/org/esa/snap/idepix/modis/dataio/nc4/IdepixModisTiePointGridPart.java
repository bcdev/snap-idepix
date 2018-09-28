package org.esa.snap.idepix.modis.dataio.nc4;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.dataio.netcdf.ProfileWriteContext;
import org.esa.snap.dataio.netcdf.metadata.profiles.beam.BeamTiePointGridPart;
import org.esa.snap.dataio.netcdf.nc.NFileWriteable;
import org.esa.snap.dataio.netcdf.nc.NVariable;
import org.esa.snap.dataio.netcdf.util.ReaderUtils;

import java.io.IOException;

/**
 * Modification of BeamTiePointGridPart for IdePix MODIS purposes
 *
 * @author olafd
 */
public class IdepixModisTiePointGridPart extends BeamTiePointGridPart {
    @Override
    public void preEncode(ProfileWriteContext ctx, Product p) throws IOException {
        super.preEncode(ctx, p);
        final NFileWriteable ncFile = ctx.getNetcdfFileWriteable();
        for (TiePointGrid tiePointGrid : p.getTiePointGrids()) {
            String variableName = ReaderUtils.getVariableName(tiePointGrid);
            final NVariable variable = ncFile.findVariable(variableName);
            variable.addAttribute("units", "degree");
            variable.addAttribute("standard_name", variableName);
        }
    }
}
