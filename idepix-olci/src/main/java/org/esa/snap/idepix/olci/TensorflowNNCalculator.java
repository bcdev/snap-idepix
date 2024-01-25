package org.esa.snap.idepix.olci;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;

import static org.esa.snap.idepix.olci.IdepixOlciConstants.*;

/**
 * Applies a tensorflow model and provides corresponding NN output for given input.
 *
 * @author olafd
 */
class TensorflowNNCalculator {

    private final String modelDir;
    private final String transformMethod;

    private final String nnName;
    private final String firstNodeName;
    private final String lastNodeName;
    private SavedModelBundle model;

    /**
     * Provides NN result for given input, applying a neural net which is based on a tensorflow model .
     *
     * @param modelDir        - the path of the directory containing the Tensorflow model
     *                        (e.g. 'nn_training_20190131_I7x24x24x24xO1')
     * @param transformMethod - the input transformation method. Supported values are 'sqrt' and 'log',
     *                        otherwise this is ignored.
     * @param nnName          - name of NN.
     */

    TensorflowNNCalculator(String modelDir, String transformMethod, String nnName) {
        // init of TensorFlow can fail, so we should handle this and give appropriate error message
        try {
            TensorFlow.version(); // triggers init of TensorFlow
        } catch (LinkageError e) {
            throw new IllegalStateException(
                    "TensorFlow could not be initialised. " +
                            "Make sure that your CPU supports 64Bit and AVX instruction set " +
                            "(Are you using a VM?) and that you have installed the Microsoft Visual C++ 2015 " +
                            "Redistributable when you are on windows.", e);
        }

        this.transformMethod = transformMethod;
        this.modelDir = modelDir;
        this.nnName = nnName;

        if (nnName.equals(CTP_TF_NN_NAME_RQ)) {
            this.firstNodeName = CTP_TF_NN__FIRST_NODE_NAME_RQ;
            this.lastNodeName = CTP_TF_NN__LAST_NODE_NAME_RQ;
        } else if (nnName.equals(CTP_TF_NN_NAME_DM)) {
            this.firstNodeName = CTP_TF_NN__FIRST_NODE_NAME_DM;
            this.lastNodeName = CTP_TF_NN__LAST_NODE_NAME_DM;
        } else {
            throw new IllegalArgumentException("NN '" + nnName + "' not supported.");
        }

        try {
            loadModel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Converts an element of NNResult to a CTP value. Taken from DM: CTP_for_OLCI_cloud_shadow.docx, 06 Feb 2019.
     * No conversion required for RQ NN (Jan 2024)
     *
     * @param nnResult - a raw result value from the NNResult float[][] array
     * @return - the value converted to CTP
     */
    float convertNNResultToCtp(float nnResult) {
        return nnName.equals(CTP_TF_NN_NAME_DM) ? nnResult * 228.03508502f + 590.0f : nnResult;
    }

    /**
     * Getter for the Tensorflow model
     *
     * @return model
     */
    SavedModelBundle getModel() {
        return model;
    }

    ////////////////// private methods ////////////////////////////////////////////

    private void loadModel() {
        // Load a model previously saved by tensorflow Python package
        model = SavedModelBundle.load(modelDir, "serve");
    }

    /**
     * Applies NN to vector and returns converted array.
     * Functional implementation of setNnTensorInput(.) plus getNNResult().
     * Makes sure the Tensors are closed after use.
     * Requires that loadModel() is run once before.
     *
     * @param nnInput - input vector for neural net
     * @return float[][] - the converted result array
     */
    float[][] calculate(float[] nnInput) {
        return calculate(new float[][]{nnInput});
    }

    /**
     * Applies NN to vector of pixel band stacks and returns converted array.
     * Functional implementation of setNnTensorInput(.) plus getNNResult().
     * Makes sure the Tensors are closed after use.
     * Requires that loadModel() is run once before.
     *
     * @param nnInput - image vector of band vectors, band vectors are input for neural net
     * @return float[][] - image vector of output band vector (length 1)
     */
    float[][] calculate(float[][] nnInput) {
        if (transformMethod.equals("sqrt")) {
            for (int i = 0; i < nnInput.length; i++) {
                for (int j = 0; j < nnInput[i].length; j++) {
                    nnInput[i][j] = (float) Math.sqrt(nnInput[i][j]);
                }
            }
        } else if (transformMethod.equals("log")) {
            for (int i = 0; i < nnInput.length; i++) {
                for (int j = 0; j < nnInput[i].length; j++) {
                    nnInput[i][j] = (float) Math.log10(nnInput[i][j]);
                }
            }
        }
        final Session.Runner runner = model.session().runner();

        return nnName.equals(CTP_TF_NN_NAME_DM) ? calculateForNNDM(nnInput, runner) : calculateForNNRQ(nnInput, runner);
    }

    private float[][] calculateForNNDM(float[][] nnInput, Session.Runner runner) {
        try (
                Tensor<?> inputTensor = Tensor.create(nnInput);
                Tensor<?> outputTensor = runner.feed(firstNodeName, inputTensor).fetch(lastNodeName).run().get(0)
        ) {
            long[] ts = outputTensor.shape();
            int numPixels = (int) ts[0];
            int numOutputVars = (int) ts[1];
            float[][] m = new float[numPixels][numOutputVars];
            outputTensor.copyTo(m);
            inputTensor.close();
            outputTensor.close();

            return m;
        }
    }

    private float[][] calculateForNNRQ(float[][] nnInput, Session.Runner runner) {
        try (
                Tensor<?> inputTensor = Tensor.create(new float[][][]{nnInput});
                Tensor<?> outputTensor = runner.feed(firstNodeName, inputTensor).fetch(lastNodeName).run().get(0)
        ) {
            long[] ts = outputTensor.shape();
            int numPixels = (int) ts[0];
            int numOutputVars = (int) ts[1];
            float[][][] m = new float[1][numOutputVars][numPixels];
            outputTensor.copyTo(m);
            inputTensor.close();
            outputTensor.close();

            return m[0];
        }
    }
}
