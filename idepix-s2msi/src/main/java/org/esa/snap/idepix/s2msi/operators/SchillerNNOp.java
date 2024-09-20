package org.esa.snap.idepix.s2msi.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.awt.Rectangle;
import java.util.Arrays;

/**
 * NN-based pixel classification operator.
 * @author Martin Böttcher
 */
@OperatorMetadata(alias = "SchillerNNOp",
        version = "1.0",
        internal = true,
        authors = "Martin Böttcher",
        copyright = "(c) 2024 by Brockmann Consult",
        description = "Operator for NN-based pixel classification. Expects an NN file and a list of bands, provides band 'nnOutput'.")
public class SchillerNNOp extends Operator {

    @Parameter(description = "Path to 'Schiller' neuronal net file for classification.",
               label = "NN path")
    private File nnFile;
    @Parameter(description = "Names of the source bands for the NN.",
               label = "Band names")
    private String[] sourceBandNames;  // e.g. S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES

    @Parameter(description = "Transormer to be applied to the input values, sqrt or log, optional.",
               label = "Transformer")
    private String inputTransformer;
    @Parameter(description = "Scale factor to be applied to the output values, optional.",
               label = "Scale factor",
               defaultValue = "1.0")
    private float outputScaleFactor;
    @Parameter(description = "Add offset to be applied to the output values after scaling, optional.",
               label = "Add offset",
               defaultValue = "0.0")
    private float outputAddOffset;

    @SourceProduct(alias = "source", description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    enum TRANSFORMER {
        NO_TRANSFORMER,
        SQRT_TRANSFORMER,
        LOG_TRANSFORMER
    }
    private TRANSFORMER transformer;

    protected Band[] sourceBands;
    private Band nnBand;
    private ThreadLocal<SchillerNeuralNetWrapper> nn;


    protected Band[] getSourceBands() {
        return sourceBands;
    }

    protected void setBands() {
        sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; ++i) {
            sourceBands[i] = getSourceProduct().getBand(sourceBandNames[i]);
        }
    }

    @Override
    public void initialize() throws OperatorException {
        SystemUtils.LOG.info("Executing NN classification operator");
        if ("sqrt".equals(inputTransformer)) {
            transformer = TRANSFORMER.SQRT_TRANSFORMER;
        } else if ("log".equals(inputTransformer)) {
            transformer = TRANSFORMER.LOG_TRANSFORMER;
        } else {
            transformer = TRANSFORMER.NO_TRANSFORMER;
        }
        setBands();
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();
        Product nnProduct = new Product(sourceProduct.getName(), "NNOutput", sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(sourceProduct, nnProduct);
        nnProduct.setStartTime(sourceProduct.getStartTime());
        nnProduct.setEndTime(sourceProduct.getEndTime());

        nnBand = nnProduct.addBand("nnOutput", ProductData.TYPE_FLOAT32);
        nnBand.setNoDataValue(Float.NaN);
        nnBand.setNoDataValueUsed(true);
        nnBand.setDescription("output of classification NN");

        InputStream in;
        try {
            in = Files.newInputStream(nnFile.toPath());
        } catch (IOException e) {
            throw new OperatorException("Cannot read specified Neural Net " + nnFile + " . - please check!", e);
        }
        nn = SchillerNeuralNetWrapper.create(in);

        setTargetProduct(nnProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        SchillerNeuralNetWrapper nnWrapper;
        try {
            nnWrapper = nn.get();
        } catch (Exception e) {
            throw new OperatorException("Cannot get values from Neural Net file - check format! " + e.getMessage());
        }
        final Rectangle targetRectangle = targetTile.getRectangle();
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                double[] nnInput = nnWrapper.getInputVector();
                boolean isValid = true;
                for (int b = 0; b < getSourceBands().length; ++b) {
                    float value = getSourceBands()[b].getSampleFloat(x, y);
                    if (Float.isNaN(value)) {
                        isValid = false;
                        break;
                    }
                    switch (transformer) {
                        case SQRT_TRANSFORMER:
                            nnInput[b] = Math.sqrt(value);
                            break;
                        case LOG_TRANSFORMER:
                            nnInput[b] = Math.log10(value);
                            break;
                        default:
                            nnInput[b] = value;
                    }
                }
                if (isValid) {
                    double[] nnResult = nnWrapper.getNeuralNet().calc(nnInput);
                    targetTile.setSample(x, y, nnResult[0] * outputScaleFactor + outputAddOffset);
                } else {
                    targetTile.setSample(x, y, Float.NaN);
                }
            }
        }
    }

    /**
      * The Service Provider Interface (SPI) for the operator.
      * It provides operator meta-data and is a factory for new operator instances.
      */
     public static class Spi extends OperatorSpi {

         public Spi() {
             super(SchillerNNOp.class);
         }
     }
}
