package org.esa.snap.idepix.olci;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Applies a tensorflow model and provides corresponding NN output for given input.
 *
 * @author olafd
 */
public class NNTensorflowApplyModel {
    
//    public static String[] NNOutNames = {"CTP"};

    private String firstNodeName;
    private String lastNodeName;
    private Session session;

    private String transformMethod;
    private float[] nnTensorInput;
    private Tensor tensorResult;
    private int nnTensorOut;

    private String modelDir;

    /**
     * Applies a tensorflow model and provides corresponding NN output for given input.
     *
     * @param nnTensorInput - float[] input vector (i.e. OLCI L1 values per pixel)
     *
     * @throws IOException -
     */
    NNTensorflowApplyModel(float[] nnTensorInput) throws IOException{
        this("saved_model.pb", "sqrt", nnTensorInput);
    }

    NNTensorflowApplyModel(String modelFileName, String transformMethod, float[] nnTensorInput) throws IOException{
        this.transformMethod = transformMethod;
        this.nnTensorInput = nnTensorInput;
        loadModel(modelFileName);
        findNodeNames();
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
     * Provides the shape of the tensor result todo: clarify what this means
     *
     * @return int
     */
    int getNnTensorOut() {
        return nnTensorOut;
    }

    ////////////////// private methods ////////////////////////////////////////////

    private void loadModel(String modelFile) {
        // Load a model previously saved by tensorflow Python package
        modelDir = new File(getClass().getResource(modelFile).getFile()).getParent();
        SavedModelBundle bundle = SavedModelBundle.load(modelDir, "serve");
        session =  bundle.session();
    }

    private void findNodeNames() throws IOException{
        /* wie findet man die richtige Stelle im Modell mit den entsprechenden Namen?
         aus dem .pbtxt File:
         der input-Name sollte der des ersten Nodes im Modell sein.
         der output-Name ist der letzte Node-Name, der mit 'dense' für den Modell-Typ beginnt.

         In Python läßt sich diese Information zur den Namen auslesen!
         from keras.models import load_model
         model = load_model(nnpath_full)
        	[ node.op.name for node in model.inputs]
        	[ node.op.name for node in model.outputs]*/

        File[] files = new File(modelDir).listFiles((dir, name) -> name.endsWith(".pbtxt"));

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
            throw new IllegalStateException("Cannot access Tensorflow NN files in specified folder: " + modelDir);
        }
    }

    private void computeTensorResult(){

//        if(transformMethod == "sqrt"){
//            for(int i=0; i <nnTensorInput.length; i++) nnTensorInput[i] = (float) Math.sqrt(nnTensorInput[i]);
//        }
//        else if(transformMethod == "log"){
//            for(int i=0; i <nnTensorInput.length; i++) nnTensorInput[i] = (float) Math.log10(nnTensorInput[i]);
//        }

        float[][] inputData = new float[1][nnTensorInput.length];
        inputData[0] = nnTensorInput;
        Tensor inputTensor = Tensor.create(inputData);

        // todo: check why test fails here
        tensorResult = session.runner().feed(firstNodeName, inputTensor).fetch(lastNodeName).run().get(0);

        long[] ts = tensorResult.shape();
        nnTensorOut = (int) ts[1];
    }
}
