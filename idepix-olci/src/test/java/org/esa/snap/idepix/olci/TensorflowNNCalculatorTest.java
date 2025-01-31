/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.idepix.olci;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tensorflow.Graph;
import org.tensorflow.Output;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.op.Ops;
import org.tensorflow.op.linalg.MatMul;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TensorflowNNCalculatorTest {

    private String auxdataPath;

    private final static float szaRad = 0.15601175f;
    private final static float ozaRad = 0.57917833f;
    private final static float aziDiff = 0.7634207f;
    private final static float refl12 = 0.02903139f;
    private final static float logTra13 = 1.7085681f;
    private final static float logTra14 = 0.96827781f;
    private final static float logTra15 = 0.15205044f;

    private final static float[] input = new float[]{szaRad, ozaRad, aziDiff, refl12, logTra13, logTra14, logTra15};

    @Before
    public void setUp() throws Exception {
        auxdataPath = IdepixOlciUtils.installAuxdataNNCtp();
    }

    @Test
    public void testNNTensorflowApplyModel_fromAuxdataInstalled() {
        // the standard setup
        String modelDir = auxdataPath + File.separator + CtpOp.DEFAULT_TENSORFLOW_NN_DIR_NAME;
        TensorflowNNCalculator nntest = new TensorflowNNCalculator(modelDir, "none");

        float[][] result = nntest.calculate(input);
        assertEquals(1.2295055f, result[0][0], 1.E-6);
        float ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x24x24x24xO1 = " + ctp);
    }

    @Test
    public void testNNTensorflowApplyModel_fromAuxdataInstalled_performance() {
        final long startTime = System.currentTimeMillis();

        // the standard setup
        String modelDir = auxdataPath + File.separator + CtpOp.DEFAULT_TENSORFLOW_NN_DIR_NAME;
        TensorflowNNCalculator nntest = new TensorflowNNCalculator(modelDir, "none");

        float[][] result = nntest.calculate(input);
        assertEquals(1.2295055f, result[0][0], 1.E-6);
        float ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x24x24x24xO1 = " + ctp);

        final long endTime = System.currentTimeMillis();
        System.out.println("total time = " + (endTime - startTime)/1000.);
    }


    @Test
    public void testNNTensorflowApplyModel() {
        // testing results from various test NNs against DM results
        // These NNs are stored as test resources
        String modelDir = new File(getClass().getResource("nn_training_20190131_I7x24x24x24xO1").getFile()).getAbsolutePath();
        TensorflowNNCalculator nntest = new TensorflowNNCalculator(modelDir, "none");

        float[][] result = nntest.calculate(input);
        for (int i = 0; i< result[0].length; i++) {
            System.out.println(result[0][i]);
        }
        // from DM: static float[] EXPECTED_OUTPUT_1 = {1.3985262F};
        assertEquals(1.3985262f, result[0][0], 1.E-6);
        float ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x24x24x24xO1 = " + ctp);

        modelDir = new File(getClass().getResource("nn_training_20190131_I7x30x30x30xO1").getFile()).getAbsolutePath();
        nntest = new TensorflowNNCalculator(modelDir, "none");
        result = nntest.calculate(input);
        assertEquals(1.60261976f, result[0][0], 1.E-6);
        ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x30x30x30xO1 = " + ctp);

        modelDir = new File(getClass().getResource("nn_training_20190131_I7x32x64x64x10xO1").getFile()).getAbsolutePath();
        nntest = new TensorflowNNCalculator(modelDir, "none");
        result = nntest.calculate(input);
        assertEquals(0.9555169f, result[0][0], 1.E-6);
        ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x32x64x64x10xO1 = " + ctp);

        modelDir = new File(getClass().getResource("nn_training_20190131_I7x32x64x64x64xO1").getFile()).getAbsolutePath();
        nntest = new TensorflowNNCalculator(modelDir, "none");
        result = nntest.calculate(input);
        assertEquals(1.2884411f, result[0][0], 1.E-6);
        ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x32x64x64x64xO1 = " + ctp);
    }

    @Test
    public void testSetFirstAndLastNodeName() {
        try {
            String modelDir = new File(getClass().getResource("nn_training_20190131_I7x24x24x24xO1").getFile()).getAbsolutePath();
            TensorflowNNCalculator nntest = new TensorflowNNCalculator(modelDir, "none");
//            nntest.setFirstAndLastNodeNameFromBinaryProtocolBuffer(nntest.getModel());
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            String firstNodeNameFromBinary = nntest.getFirstNodeName();
            String lastNodeNameFromBinary = nntest.getLastNodeName();
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            String firstNodeNameFromText = nntest.getFirstNodeName();
            String lastNodeNameFromText = nntest.getLastNodeName();
            assertEquals("dense_33_input", firstNodeNameFromBinary);
            assertEquals("dense_36/BiasAdd", lastNodeNameFromBinary);
            assertEquals(firstNodeNameFromText, firstNodeNameFromBinary);
            assertEquals(lastNodeNameFromText, lastNodeNameFromBinary);

            modelDir = new File(getClass().getResource("nn_training_20190131_I7x30x30x30xO1").getFile()).getAbsolutePath();
            nntest = new TensorflowNNCalculator(modelDir, "none");
//            nntest.setFirstAndLastNodeNameFromBinaryProtocolBuffer(nntest.getModel());
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            firstNodeNameFromBinary = nntest.getFirstNodeName();
            lastNodeNameFromBinary = nntest.getLastNodeName();
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            firstNodeNameFromText = nntest.getFirstNodeName();
            lastNodeNameFromText = nntest.getLastNodeName();
            assertEquals("dense_29_input", firstNodeNameFromBinary);
            assertEquals("dense_32/BiasAdd", lastNodeNameFromBinary);
            assertEquals(firstNodeNameFromText, firstNodeNameFromBinary);
            assertEquals(lastNodeNameFromText, lastNodeNameFromBinary);

            modelDir = new File(getClass().getResource("nn_training_20190131_I7x32x64x64x10xO1").getFile()).getAbsolutePath();
            nntest = new TensorflowNNCalculator(modelDir, "none");
//            nntest.setFirstAndLastNodeNameFromBinaryProtocolBuffer(nntest.getModel());
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            firstNodeNameFromBinary = nntest.getFirstNodeName();
            lastNodeNameFromBinary = nntest.getLastNodeName();
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            firstNodeNameFromText = nntest.getFirstNodeName();
            lastNodeNameFromText = nntest.getLastNodeName();
            assertEquals("dense_24_input", firstNodeNameFromBinary);
            assertEquals("dense_28/BiasAdd", lastNodeNameFromBinary);
            assertEquals(firstNodeNameFromText, firstNodeNameFromBinary);
            assertEquals(lastNodeNameFromText, lastNodeNameFromBinary);

            modelDir = new File(getClass().getResource("nn_training_20190131_I7x32x64x64x64xO1").getFile()).getAbsolutePath();
            nntest = new TensorflowNNCalculator(modelDir, "none");
//            nntest.setFirstAndLastNodeNameFromBinaryProtocolBuffer(nntest.getModel());
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            firstNodeNameFromBinary = nntest.getFirstNodeName();
            lastNodeNameFromBinary = nntest.getLastNodeName();
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            firstNodeNameFromText = nntest.getFirstNodeName();
            lastNodeNameFromText = nntest.getLastNodeName();
            assertEquals("dense_19_input", firstNodeNameFromBinary);
            assertEquals("dense_23/BiasAdd", lastNodeNameFromBinary);
            assertEquals(firstNodeNameFromText, firstNodeNameFromBinary);
            assertEquals(lastNodeNameFromText, lastNodeNameFromBinary);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testTensorflowCompute() {
        float[][] inputF = new float[][]{
                {1.0f, 2.0f, 3.0f},
                {9.0f, 8.0f, 7.0f}
        };

        final long startTime = System.currentTimeMillis();
        try (Tensor<?> inputTensor = Tensor.create(inputF);) {
            assertEquals(2, inputTensor.shape().length);

            // no TF run here, just set output tensor to input tensor. For runs see the *ApplyModel* tests.
            Tensor outputTensor = inputTensor;

            // extract result from output tensor:
            long[] ts = outputTensor.shape();
            int numPixels = (int) ts[0];
            int numOutputVars = (int) ts[1];
            float[][] m = new float[numPixels][numOutputVars];
            outputTensor.copyTo(m);
            Assert.assertArrayEquals(m, inputF);
        }
        final long endTime = System.currentTimeMillis();
        System.out.println("total time testTensorflowCompute = " + (endTime - startTime)/1000.);

    }

    @Test
    public void testTensorCreate() {
        float[][] inputF = new float[512*512][7];
        for (int i = 0; i < inputF.length; i++) {
            for (int j = 0; j < inputF[0].length; j++) {
                inputF[i][j] = (i + j) *1.0f;
            }
        }

        final long startTime = System.currentTimeMillis();

        final Tensor<?> inputTensor = Tensor.create(inputF);
        long intermediateTime = System.currentTimeMillis();
        System.out.println("intermediateTime (2) testTensorOf = " + (intermediateTime - startTime)/1000.);
    }

}
