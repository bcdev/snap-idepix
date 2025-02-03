package org.esa.snap.idepix.olci;

import org.tensorflow.*;
import org.tensorflow.ndarray.FloatNdArray;
import org.tensorflow.ndarray.StdArrays;
import org.tensorflow.types.TFloat32;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Applies a tensorflow model and provides corresponding NN output for given input.
 *
 * @author olafd
 */
class TensorflowNNCalculator {

    private final String modelDir;
    private final String transformMethod;

    private String firstNodeName;
    private String lastNodeName;
    private SavedModelBundle model;

    /**
     * Provides NN result for given input, applying a neural net which is based on a tensorflow model .
     *
     * @param modelDir        - the path of the directory containing the Tensorflow model
     *                        (e.g. 'nn_training_20190131_I7x24x24x24xO1')
     * @param transformMethod - the input transformation method. Supported values are 'sqrt' and 'log',
     *                        otherwise this is ignored.
     */
    TensorflowNNCalculator(String modelDir, String transformMethod) {
        // init of TensorFlow can fail, so we should handle this and give appropriate error message
//        try {
//            TensorFlow.version(); // triggers init of TensorFlow
//        } catch (LinkageError e) {
//            throw new IllegalStateException(
//                    "TensorFlow could not be initialised. " +
//                            "Make sure that your CPU supports 64Bit and AVX instruction set " +
//                            "(Are you using a VM?) and that you have installed the Microsoft Visual C++ 2015 " +
//                            "Redistributable when you are on windows.", e);
//        }

        this.transformMethod = transformMethod;
        this.modelDir = modelDir;
        try {
            loadModel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Converts an element of NNResult to a CTP value. Taken from DM: CTP_for_OLCI_cloud_shadow.docx, 06 Feb 2019.
     *
     * @param nnResult - a raw result value from the NNResult float[][] array
     * @return - the value converted to CTP
     */
    static float convertNNResultToCtp(float nnResult) {
        return nnResult * 228.03508502f + 590.0f;
    }

    /**
     * Getter for the Tensorflow model
     *
     * @return model
     */
    SavedModelBundle getModel() {
        return model;
    }

    /**
     * Getter for the first node name
     *
     * @return firstNodeName
     */
    String getFirstNodeName() {
        return firstNodeName;
    }

    /**
     * Getter for the last node name
     *
     * @return lastNodeName
     */
    String getLastNodeName() {
        return lastNodeName;
    }


    // package local for testing
    void setFirstAndLastNodeNameFromTextProtocolBuffer() throws IOException {
        // from DM:
        // extract names of first and last relevant nodes (i.e. name contains 'dense') from text '*.pbtxt'
        /* wie findet man die richtige Stelle im Modell mit den entsprechenden Namen?
         aus dem .pbtxt File:
         der input-Name sollte der des ersten Nodes im Modell sein.
         der output-Name ist der letzte Node-Name, der mit 'dense' für den Modell-Typ beginnt.

         In Python läßt sich diese Information zur den Namen auslesen!
         from keras.models import load_model
         model = load_model(nnpath_full)
        	[ node.op.name for node in model.inputs]
        	[ node.op.name for node in model.outputs]*/

        final String pbtxtFileName = (new File(modelDir)).getName() + ".pbtxt";
        File[] files = new File(modelDir).listFiles((dir, name) -> name.equalsIgnoreCase(pbtxtFileName));

        boolean setFirstNodeName = false;

        if (files != null) {
            try (BufferedReader br = new BufferedReader(new FileReader(files[0]))) {
                for (String line; (line = br.readLine()) != null; ) {
                    if (line.equals("node {")) {
                        line = br.readLine();
                        final String denseSubstring = line.substring(line.indexOf("dense"), line.length() - 1);
                        if (!setFirstNodeName) {
                            if (line.contains("name") && line.contains("dense")) {
                                firstNodeName = denseSubstring;
                                setFirstNodeName = true;
                            }
                        } else {
                            if (line.contains("name") && line.contains("dense")) {
                                lastNodeName = denseSubstring;
                            }
                        }
                    }
                }
            }
        } else {
            throw new IllegalStateException("Cannot access Tensorflow text protocol buffer file in specified folder: "
                                                    + modelDir);
        }
    }

    ////////////////// private methods ////////////////////////////////////////////

    private void loadModel() throws Exception {
        // Load a model previously saved by tensorflow Python package
        model = SavedModelBundle.load(modelDir, "serve");
//        setFirstAndLastNodeNameFromBinaryProtocolBuffer(model);
        setFirstAndLastNodeNameFromTextProtocolBuffer();
    }

    /**
     * Applies NN to vector and returns converted array.
     * Functional implementation of setNnTensorInput(.) plus getNNResult().
     * Makes sure the Tensors are closed after use.
     * Requires that loadModel() is run once before.
     *
     * @param nnInput - input vector for neural net
     *
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

        FloatNdArray fMatrix = StdArrays.ndCopyOf(nnInput);

        final Session s = model.session();
        final Session.Runner runner = s.runner();

        try (
                final TFloat32 inputTensor = TFloat32.tensorOf(fMatrix);
                final Result result = runner.feed(firstNodeName, inputTensor).fetch(lastNodeName).run()
        ) {
            TFloat32 outputTensor = ((TFloat32) result.get(0));

            int numPixels = (int) outputTensor.shape().get(0);
            int numOutputVars = (int) outputTensor.shape().get(1);

            float[][] m = new float[numPixels][numOutputVars];
            for (int i = 0; i < numPixels; i++) {
                for (int j = 0; j < numOutputVars; j++) {
                    m[i][j] = outputTensor.getFloat(i, j);
                }
            }
            return m;
        }
    }

}
