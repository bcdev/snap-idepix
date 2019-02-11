package org.esa.snap.idepix.olci;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import java.io.*;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 11.02.2019
 * Time: 14:59
 *
 * @author olafd
 */
public class NNTensorflowApplyModel {
    
    //for the example "I13x30x30x30x30x30x10xO6_sqrt_S2_6OutNodes")
//    final static double[] INPUT_1 =
//            {0.6851, 0.6961, 0.6699, 0.7329, 0.7584, 0.7809, 0.8009, 0.7963, 0.812, 0.5375, 0.0407, 0.5998, 0.4881};
//    final static float[] EXPECTED_OUTPUT_1 =
//            {0.0012113489F, 1.0016897F,0.029165775F, 1.7926563e-5F, 5.213171e-4F, -0.0031922571F};

    static double[] INPUT_1 = {0.15601175, 0.57917833, 0.7634207, 0.02903139, 1.7085681, 0.96827781, 0.15205044};
    static float[] EXPECTED_OUTPUT_1 = {1.3985262F};
    public static String[] NNOutNames = {"CTP"};

    final static String TRANSFORM_METHOD  = "sqrt";

    final static String MODEL_FILE = "saved_model.pb";  // todo: preliminary


    private String firstNodeName;
    private String lastNodeName;
    private Session session;
    private int NTensorOut;

    private String modelDir;

    public NNTensorflowApplyModel() throws IOException{
        session = loadModel(MODEL_FILE);
        findNodeNames(modelDir);
        setNTensorOut();
    }

    public int getNTensorOut() {
        return NTensorOut;
    }

    float[][] computeOutput(double[] inputDouble) {

        float[] inputFloat = new float[inputDouble.length];
        for (int i = 0; i < inputFloat.length; i++) {
            inputFloat[i] = (float) inputDouble[i];
        }

        /*if(transformMethod == "sqrt"){
            for(int i=0; i <inputFloat.length; i++) inputFloat[i] = (float) Math.sqrt(inputFloat[i]);
        }
        else if(transformMethod == "log"){
            for(int i=0; i <inputFloat.length; i++) inputFloat[i] = (float) Math.log10(inputFloat[i]);
        }*/

        float[][] data = new float[1][inputFloat.length];
        data[0] = inputFloat;
        Tensor inputTensor = Tensor.create(data);

        Tensor result = session
                .runner()
                .feed(firstNodeName, inputTensor)
                .fetch(lastNodeName)
                .run()
                .get(0);

        float[][] m = new float[1][NTensorOut];
        result.copyTo(m);

        return m;
    }

    private Session loadModel(String modelFile) {
        // Load a model previously saved by tensorflow Python package
        modelDir = new File(getClass().getResource(modelFile).getFile()).getParent();
        SavedModelBundle bundle = SavedModelBundle.load(modelDir, "serve");
        return bundle.session();
    }

    private void findNodeNames(String modelDir) throws IOException{
        /* wie findet man die richtige Stelle im Modell mit den entsprechenden Namen?
         aus dem .pbtxt File:
         der input-Name sollte der des ersten Nodes im Modell sein.
         der output-Name ist der letzte Node-Name, der mit 'dense' für den Modell-Typ beginnt.

         In Python läßt sich diese Information zur den Namen auslesen!
         from keras.models import load_model
         model = load_model(nnpath_full)
        	[ node.op.name for node in model.inputs]
        	[ node.op.name for node in model.outputs]*/

        File[] files = new File(modelDir).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".pbtxt");
            }
        });

        boolean setFirstNodeName = true;

        try(BufferedReader br = new BufferedReader(new FileReader(files[0]))) {
            for(String line; (line = br.readLine()) != null; ) {
                if(line.equals("node {")){
                    line = br.readLine();
                    if(setFirstNodeName){
                        if(line.contains("name") && line.contains("dense")){
                            firstNodeName = line.substring(line.indexOf("dense"),line.length()-1);
                            setFirstNodeName = false;
                        }
                    }
                    else{
                        if(line.contains("name") && line.contains("dense")){
                            lastNodeName = line.substring(line.indexOf("dense"),line.length()-1);
                        }
                    }
                }
            }
        }
    }

    private void setNTensorOut(){

        float[] inputFloat = new float[INPUT_1.length];
        for (int i = 0; i < inputFloat.length; i++) {
            inputFloat[i] = (float) INPUT_1[i];
        }

        /*if(transformMethod == "sqrt"){
            for(int i=0; i <inputFloat.length; i++) inputFloat[i] = (float) Math.sqrt(inputFloat[i]);
        }
        else if(transformMethod == "log"){
            for(int i=0; i <inputFloat.length; i++) inputFloat[i] = (float) Math.log10(inputFloat[i]);
        }*/

        float[][] data = new float[1][inputFloat.length];
        data[0] = inputFloat;
        Tensor inputTensor = Tensor.create(data);

        Tensor result = session
                .runner()
                .feed(firstNodeName, inputTensor)
                .fetch(lastNodeName)
                .run()
                .get(0);

        long[] ts = result.shape();
        NTensorOut = (int) ts[1];
    }
}
