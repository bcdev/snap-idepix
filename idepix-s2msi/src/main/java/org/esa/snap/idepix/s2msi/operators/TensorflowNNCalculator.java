package org.esa.snap.idepix.s2msi.operators;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Applies a tensorflow model and provides corresponding NN output for given input.
 *
 * @author olafd
 */
public class TensorflowNNCalculator {

    private final String modelDir;
    private String firstNodeName;
    private String lastNodeName;
    private SavedModelBundle model;

    /**
     * Provides NN result for given input, applying a neural net which is based on a tensorflow model .
     *
     * @param modelDir        - the path of the directory containing the Tensorflow model
     *                        (e.g. 'nn_training_20190131_I7x24x24x24xO1')
     */
    public TensorflowNNCalculator(String modelDir) {
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
        this.modelDir = modelDir;
        try {
            // Load a model previously saved by tensorflow Python package
            model = SavedModelBundle.load(this.modelDir, "serve");
            //setFirstAndLastNodeNameFromBinaryProtocolBuffer(model);
            setFirstAndLastNodeNameFromTextProtocolBuffer();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load tensorflow model from " + modelDir, e);
        }
    }


    /**
     * Getter for the Tensorflow model
     *
     * @return model
     */
    public SavedModelBundle getModel() {
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

    /**
     * Applies NN to vector of pixel band stacks and returns converted array.
     * Functional implementation of setNnTensorInput(.) plus getNNResult().
     * Makes sure the Tensors are closed after use.
     * Requires that loadModel() is run once before.
     *
     * @param nnInput - image vector of band vectors, band vectors are input for neural net
     * @return float[][] - image vector of output band vector (length 1)
     */
    public float[][] calculate(float[][] nnInput) {
        final Session.Runner runner = model.session().runner();
        try (
                Tensor<?> inputTensor = Tensor.create(nnInput);
                Tensor<?> outputTensor = runner.feed(firstNodeName, inputTensor).fetch(lastNodeName).run().get(0)
        ) {
            long[] ts = outputTensor.shape();
            int numPixels = (int) ts[0];
            int numOutputVars = (int) ts[1];
            float[][] m = new float[numPixels][numOutputVars];
            outputTensor.copyTo(m);
            return m;
        }
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
}
