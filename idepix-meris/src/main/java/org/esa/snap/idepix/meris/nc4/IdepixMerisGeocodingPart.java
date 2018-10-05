package org.esa.snap.idepix.meris.nc4;

import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.TiePointGeoCoding;
import org.esa.snap.core.image.ImageManager;
import org.esa.snap.core.util.StringUtils;
import org.esa.snap.dataio.netcdf.ProfileWriteContext;
import org.esa.snap.dataio.netcdf.metadata.profiles.cf.CfGeocodingPart;
import org.esa.snap.dataio.netcdf.nc.NFileWriteable;
import org.esa.snap.dataio.netcdf.nc.NVariable;
import org.esa.snap.dataio.netcdf.util.Constants;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import ucar.ma2.DataType;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;

/**
 * Modification of BeamGeocodingPart for IdePix MERIS purposes, i.e. do not write lat/lon bands
 * into target netcdf file, as we want to keep products small and we still have the TPGs
 * *** CURRENTLY NOT USED ***
 *
 * @author olafd
 */
public class IdepixMerisGeocodingPart extends CfGeocodingPart {
    private static final String TIEPOINT_COORDINATES = "tiepoint_coordinates";

    private static final int LON_INDEX = 0;
    private static final int LAT_INDEX = 1;

    @Override
    public void preEncode(ProfileWriteContext ctx, Product product) throws IOException {
        final GeoCoding geoCoding = product.getSceneGeoCoding();
        if (geoCoding == null) {
            return;
        }

        // difference to BeamGeocodingPart: do not add lat/lon bands here!
        // final NFileWriteable ncFile = ctx.getNetcdfFileWriteable();
        //addLatLonBands(ncFile, ImageManager.getPreferredTileSize(product));

        final NFileWriteable ncFile = ctx.getNetcdfFileWriteable();
        if (!isLatLonPresent(ncFile)) {
            addLatLonBands(ncFile, ImageManager.getPreferredTileSize(product));
        }

        ctx.setProperty(Constants.Y_FLIPPED_PROPERTY_NAME, false);

        if (geoCoding instanceof TiePointGeoCoding) {
            // this should be the normal case!
            final TiePointGeoCoding tpGC = (TiePointGeoCoding) geoCoding;
            final String[] names = new String[2];
            names[LON_INDEX] = tpGC.getLonGrid().getName();
            names[LAT_INDEX] = tpGC.getLatGrid().getName();
            final String value = StringUtils.arrayToString(names, " ");
            ctx.getNetcdfFileWriteable().addGlobalAttribute(TIEPOINT_COORDINATES, value);
        } else {
            if (geoCoding instanceof CrsGeoCoding) {
                addWktAsVariable(ctx.getNetcdfFileWriteable(), geoCoding);
            }
        }
    }

    @Override
    public void encode(ProfileWriteContext ctx, Product product) {
//         we do not add lat/lon bands, so nothing to do here
    }

    private void addWktAsVariable(NFileWriteable ncFile, GeoCoding geoCoding) throws IOException {
        final CoordinateReferenceSystem crs = geoCoding.getMapCRS();
        if (crs != null && crs.toWKT() != null) {
            final double[] matrix = new double[6];
            final MathTransform transform = geoCoding.getImageToMapTransform();
            if (transform instanceof AffineTransform) {
                ((AffineTransform) transform).getMatrix(matrix);
            }

            final NVariable crsVariable = ncFile.addScalarVariable("crs", DataType.INT);
            crsVariable.addAttribute("wkt", crs.toWKT());
            crsVariable.addAttribute("i2m", StringUtils.arrayToCsv(matrix));
            crsVariable.addAttribute("long_name", "Coordinate Reference System");
            final String crsCommentString = "A coordinate reference system (CRS) defines defines how the georeferenced " +
                    "spatial data relates to real locations on the Earth\'s surface";
            crsVariable.addAttribute("comment", crsCommentString);
        }
    }

    private boolean isLatLonPresent(NFileWriteable ncFile) {
        return ncFile.findVariable("lat") != null && ncFile.findVariable("lon") != null;
    }

    private void addLatLonBands(final NFileWriteable ncFile, Dimension tileSize) throws IOException {
        final NVariable lat = ncFile.addVariable("lat", DataType.DOUBLE, tileSize, "y x");
        lat.addAttribute("units", "degrees_north");
        lat.addAttribute("long_name", "latitude coordinate");
        lat.addAttribute("standard_name", "latitude");

        final NVariable lon = ncFile.addVariable("lon", DataType.DOUBLE, tileSize, "y x");
        lon.addAttribute("units", "degrees_east");
        lon.addAttribute("long_name", "longitude coordinate");
        lon.addAttribute("standard_name", "longitude");
    }

}
