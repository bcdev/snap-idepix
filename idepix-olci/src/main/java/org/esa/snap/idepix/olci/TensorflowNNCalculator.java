package org.esa.snap.idepix.olci;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

//import org.tensorflow.framework.MetaGraphDef;
//import org.tensorflow.framework.NodeDef;

/**
 * Applies a tensorflow model and provides corresponding NN output for given input.
 *
 * @author olafd
 */
public class TensorflowNNCalculator {

    private String firstNodeName;
    private String lastNodeName;

    private String transformMethod;
    private float[] nnTensorInput;
    private Tensor tensorResult;
    private int nnTensorOut;

    private String modelDir;
    private SavedModelBundle model;

    /**
     * Provides NN result for given input, applying a neural net which is based on a tensorflow model .
     *
     * @param modelDir        - the path of the directory containing the Tensorflow model
     *                        (e.g. 'nn_training_20190131_I7x24x24x24xO1')
     * @param transformMethod - the input transformation method. Supported values are 'sqrt' and 'log',
     *                        otherwise this is ignored.
     * @param nnTensorInput   - float[] input vector (i.e. OLCI L1 values per pixel)
     */
    TensorflowNNCalculator(String modelDir, String transformMethod, float[] nnTensorInput) {
        this.transformMethod = transformMethod;
        this.nnTensorInput = nnTensorInput;
        this.modelDir = modelDir;
        System.out.println("modelDir = " + modelDir);
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
     * Provides the NN result for given input and specified Tensorflow model.
     *
     * @return float[][]
     */
    float[][] getNNResult() {
        computeTensorResult();

        float[][] m = new float[1][nnTensorOut];
        tensorResult.copyTo(m);

        return m;
    }

    /**
     * Provides the shape of the tensor result
     *
     * @return int
     */
    int getNnTensorOut() {
        return nnTensorOut;
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
    public String getFirstNodeName() {
        return firstNodeName;
    }

    /**
     * Getter for the last node name
     *
     * @return lastNodeName
     */
    public String getLastNodeName() {
        return lastNodeName;
    }

    /**
     * Setter for NN input
     *
     * @param nnTensorInput - the array with NN input values
     */
    public void setNnTensorInput(float[] nnTensorInput) {
        this.nnTensorInput = nnTensorInput;
    }

    // package local for testing
    // todo: actually this code requires protobuf-java in a version >= 3, which may lead to conflicts in SNAP and at Calvalus
    // for the moment, read from the .pbtxt file with the method below
//    void setFirstAndLastNodeNameFromBinaryProtocolBuffer(SavedModelBundle model) throws Exception {
//        // extract names of first and last relevant nodes (i.e. name contains 'dense') from binary 'saved_model.pb'
//        // rather than text file '*.pbtxt'. So we do not need the pbtxt in case it is very large.
//        MetaGraphDef m = MetaGraphDef.parseFrom(model.metaGraphDef());
//        final List<NodeDef> nodeList = m.getGraphDef().getNodeList();
//        int index = 0;
//        for (int i = 1; i < nodeList.size(); i++) {
//            NodeDef nodeDefPrev = nodeList.get(i-1);
//            NodeDef nodeDef = nodeList.get(i);
//            if (!nodeDefPrev.getName().contains("dense") && nodeDef.getName().contains("dense")) {
//                firstNodeName = nodeDef.getName();
//                index = i;
//                break;
//            }
//        }
//
//        for (int i = index; i < nodeList.size()-1; i++) {
//            NodeDef nodeDef = nodeList.get(i);
//            NodeDef nodeDefNext = nodeList.get(i+1);
//            if (nodeDef.getName().contains("dense") && !nodeDefNext.getName().contains("dense")) {
//                lastNodeName = nodeDef.getName();
//            }
//        }
//    }

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
                        if (!setFirstNodeName) {
                            if (line.contains("name") && line.contains("dense")) {
                                firstNodeName = line.substring(line.indexOf("dense"), line.length() - 1);
                                setFirstNodeName = true;
                            }
                        } else {
                            if (line.contains("name") && line.contains("dense")) {
                                lastNodeName = line.substring(line.indexOf("dense"), line.length() - 1);
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

    private void computeTensorResult() {
        if (transformMethod.equals("sqrt")) {
            for (int i = 0; i < nnTensorInput.length; i++) {
                nnTensorInput[i] = (float) Math.sqrt(nnTensorInput[i]);
            }
        } else if (transformMethod.equals("log")) {
            for (int i = 0; i < nnTensorInput.length; i++) {
                nnTensorInput[i] = (float) Math.log10(nnTensorInput[i]);
            }
        }

        float[][] inputData = new float[1][nnTensorInput.length];
        inputData[0] = nnTensorInput;
        Tensor inputTensor = Tensor.create(inputData);

        tensorResult = model.session().runner().feed(firstNodeName, inputTensor).fetch(lastNodeName).run().get(0);

        long[] ts = tensorResult.shape();
        nnTensorOut = (int) ts[1];
    }
}
