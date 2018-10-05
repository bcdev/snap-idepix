package org.esa.snap.idepix.meris.nc4;

import com.bc.ceres.binding.PropertyContainer;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductNodeGroup;
import org.esa.snap.dataio.netcdf.ProfileWriteContext;
import org.esa.snap.dataio.netcdf.metadata.profiles.beam.BeamMaskPart;
import org.esa.snap.dataio.netcdf.nc.NFileWriteable;
import org.esa.snap.dataio.netcdf.nc.NVariable;
import org.esa.snap.dataio.netcdf.util.Constants;
import org.esa.snap.dataio.netcdf.util.ReaderUtils;
import ucar.ma2.Array;
import ucar.ma2.DataType;

import java.awt.*;
import java.io.IOException;

/**
 * Modification of BeamMaskPart for IdePix MERIS purposes
 *
 * @author olafd
 */
public class IdepixMerisMaskPart extends BeamMaskPart {

    private static final String IDEPIX_MERIS_MASK_PREFIX = "idepix_meris";

    @Override
    public void preEncode(ProfileWriteContext ctx, Product p) throws IOException {
        NFileWriteable ncFile = ctx.getNetcdfFileWriteable();
        writeMasks(p, ncFile);
    }

    private static void writeMasks(Product p, NFileWriteable ncFile) throws IOException {
        final ProductNodeGroup<Mask> maskGroup = p.getMaskGroup();
        final String[] maskNames = maskGroup.getNodeNames();
        for (String maskName : maskNames) {
            if (maskName.startsWith(IDEPIX_MERIS_MASK_PREFIX)) {
                final Mask mask = maskGroup.get(maskName);
                if (Mask.BandMathsType.INSTANCE == mask.getImageType()) {
                    String variableName = ReaderUtils.getVariableName(mask);
                    final NVariable variable = ncFile.addScalarVariable(variableName + SUFFIX_MASK, DataType.BYTE);
                    if (!variableName.equals(maskName)) {
                        variable.addAttribute(Constants.ORIG_NAME_ATT_NAME, maskName);
                    }

                    variable.addAttribute("long_name", maskName);
                    final String description = mask.getDescription();
                    if (description != null && description.trim().length() > 0) {
                        variable.addAttribute("description", description);
                    }

                    final PropertyContainer imageConfig = mask.getImageConfig();
                    final String expression = imageConfig.getValue(Mask.BandMathsType.PROPERTY_NAME_EXPRESSION);
                    variable.addAttribute(EXPRESSION, expression);

                    final Color color = mask.getImageColor();
                    final int[] colorValues = new int[4];
                    colorValues[INDEX_RED] = color.getRed();
                    colorValues[INDEX_GREEN] = color.getGreen();
                    colorValues[INDEX_BLUE] = color.getBlue();
                    colorValues[INDEX_ALPHA] = color.getAlpha();
                    variable.addAttribute(COLOR, Array.factory(colorValues));

                    final double transparency = mask.getImageTransparency();
                    variable.addAttribute(TRANSPARENCY, transparency);
                }
            }
        }
    }
}
