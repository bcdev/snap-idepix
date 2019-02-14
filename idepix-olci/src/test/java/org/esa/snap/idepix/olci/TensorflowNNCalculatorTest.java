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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TensorflowNNCalculatorTest {

    private final static float szaRad = 0.15601175f;
    private final static float ozaRad = 0.57917833f;
    private final static float aziDiff = 0.7634207f;
    private final static float refl12 = 0.02903139f;
    private final static float logTra13 = 1.7085681f;
    private final static float logTra14 = 0.96827781f;
    private final static float logTra15 = 0.15205044f;

    private final static float[] input = new float[]{szaRad, ozaRad, aziDiff, refl12, logTra13, logTra14, logTra15};

    @Test
    public void testNNTensorflowApplyModel() {
        TensorflowNNCalculator nntest = new TensorflowNNCalculator("nn_training_20190131_I7x24x24x24xO1", "none", null);
        nntest.setNnTensorInput(input);

        float[][] result = nntest.getNNResult();
        for (int i = 0; i< nntest.getNnTensorOut(); i++) {
            System.out.println(result[0][i]);
        }
        // from DM: static float[] EXPECTED_OUTPUT_1 = {1.3985262F};
        assertEquals(1.3985262f, result[0][0], 1.E-6);
        float ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x24x24x24xO1 = " + ctp);

        nntest = new TensorflowNNCalculator("nn_training_20190131_I7x30x30x30xO1", "none", input);
        result = nntest.getNNResult();
        assertEquals(1.60261976f, result[0][0], 1.E-6);
        ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x30x30x30xO1 = " + ctp);

        nntest = new TensorflowNNCalculator("nn_training_20190131_I7x32x64x64x10xO1", "none", input);
        result = nntest.getNNResult();
        assertEquals(0.9555169f, result[0][0], 1.E-6);
        ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x32x64x64x10xO1 = " + ctp);

        nntest = new TensorflowNNCalculator("nn_training_20190131_I7x32x64x64x64xO1", "none", input);
        result = nntest.getNNResult();
        assertEquals(1.2884411f, result[0][0], 1.E-6);
        ctp = TensorflowNNCalculator.convertNNResultToCtp(result[0][0]);
        System.out.println("ctp for I7x32x64x64x64xO1 = " + ctp);
    }

    @Test
    public void testSetFirstAndLastNodeName() {
        try {
            TensorflowNNCalculator nntest = new TensorflowNNCalculator("nn_training_20190131_I7x24x24x24xO1", "none", input);
            nntest.setFirstAndLastNodeNameFromBinaryProtocolBuffer(nntest.getModel());
            String firstNodeNameFromBinary = nntest.getFirstNodeName();
            String lastNodeNameFromBinary = nntest.getLastNodeName();
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            String firstNodeNameFromText = nntest.getFirstNodeName();
            String lastNodeNameFromText = nntest.getLastNodeName();
            assertEquals("dense_33_input", firstNodeNameFromBinary);
            assertEquals("dense_36/BiasAdd", lastNodeNameFromBinary);
            assertEquals(firstNodeNameFromText, firstNodeNameFromBinary);
            assertEquals(lastNodeNameFromText, lastNodeNameFromBinary);

            nntest = new TensorflowNNCalculator("nn_training_20190131_I7x30x30x30xO1", "none", input);
            nntest.setFirstAndLastNodeNameFromBinaryProtocolBuffer(nntest.getModel());
            firstNodeNameFromBinary = nntest.getFirstNodeName();
            lastNodeNameFromBinary = nntest.getLastNodeName();
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            firstNodeNameFromText = nntest.getFirstNodeName();
            lastNodeNameFromText = nntest.getLastNodeName();
            assertEquals("dense_29_input", firstNodeNameFromBinary);
            assertEquals("dense_32/BiasAdd", lastNodeNameFromBinary);
            assertEquals(firstNodeNameFromText, firstNodeNameFromBinary);
            assertEquals(lastNodeNameFromText, lastNodeNameFromBinary);

            nntest = new TensorflowNNCalculator("nn_training_20190131_I7x32x64x64x10xO1", "none", input);
            nntest.setFirstAndLastNodeNameFromBinaryProtocolBuffer(nntest.getModel());
            firstNodeNameFromBinary = nntest.getFirstNodeName();
            lastNodeNameFromBinary = nntest.getLastNodeName();
            nntest.setFirstAndLastNodeNameFromTextProtocolBuffer();
            firstNodeNameFromText = nntest.getFirstNodeName();
            lastNodeNameFromText = nntest.getLastNodeName();
            assertEquals("dense_24_input", firstNodeNameFromBinary);
            assertEquals("dense_28/BiasAdd", lastNodeNameFromBinary);
            assertEquals(firstNodeNameFromText, firstNodeNameFromBinary);
            assertEquals(lastNodeNameFromText, lastNodeNameFromBinary);

            nntest = new TensorflowNNCalculator("nn_training_20190131_I7x32x64x64x64xO1", "none", input);
            nntest.setFirstAndLastNodeNameFromBinaryProtocolBuffer(nntest.getModel());
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
}
