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

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Objects;

import static org.esa.snap.idepix.olci.IdepixOlciConstants.CTP_TF_NN_NAME_DM;
import static org.esa.snap.idepix.olci.IdepixOlciConstants.CTP_TF_NN_NAME_RQ;
import static org.junit.Assert.assertEquals;

public class TensorflowNNCalculatorTest {

    private String auxdataPath;

    private final static float szaRad = 0.15601175f;
    private final static float ozaRad = 0.57917833f;
    private final static float aziDiff = 0.7634207f;
    private final static float refl12 = 0.02903139f;
    private final static float logTra13 = 1.7085681f;
    private final static float logTra14 = 0.96827781f;
    private final static float logTra15 = 0.15205044f;

    @Before
    public void setUp() throws Exception {
        auxdataPath = IdepixOlciUtils.installAuxdataNNCtp();
    }

    @Test
    public void testNNTensorflowApplyModel_fromAuxdataInstalled() {
        // the standard setup
        String modelDir = auxdataPath + File.separator + CtpOp.DEFAULT_TENSORFLOW_NN_DIR_NAME;
        TensorflowNNCalculator nntest = new TensorflowNNCalculator(modelDir, "none", CTP_TF_NN_NAME_DM);

        float[] input = new float[]{szaRad, ozaRad, aziDiff, refl12, logTra13, logTra14, logTra15};
        float[][] result = nntest.calculate(input);
        // from DM: static float[] EXPECTED_OUTPUT_1 = {1.2295055F};
        assertEquals(1.2295055f, result[0][0], 1.E-6);
        float ctp = nntest.convertNNResultToCtp(result[0][0]);
        assertEquals(870.37036, ctp, 1.E-3);
    }

    @Test
    public void testNNTensorflowApplyModel_DM() {
        // testing results from various test NNs against DM results
        // These NNs are stored as test resources
        String modelDir = new File(Objects.requireNonNull(getClass().
                getResource("nn_training_20190131_I7x30x30x30x10x2xO1")).getFile()).getAbsolutePath();
        TensorflowNNCalculator nntest = new TensorflowNNCalculator(modelDir, "none", CTP_TF_NN_NAME_DM);

        // first pixel
        float[] input1 = new float[]{szaRad, ozaRad, aziDiff, refl12, logTra13, logTra14, logTra15};
        float[][] result = nntest.calculate(input1);
        // from DM: static float[] EXPECTED_OUTPUT_1 = {1.2295055F};
        assertEquals(1.2295055f, result[0][0], 1.E-6);

        float ctp = nntest.convertNNResultToCtp(result[0][0]);
        assertEquals(870.37036, ctp, 1.E-3);

        // a second pixel, a bit different:
        float eps = 1.E-2f;
        float[] input2 = new float[]{szaRad + eps, ozaRad - eps, aziDiff + eps, refl12 - eps,
                logTra13 + eps, logTra14 - eps, logTra15 + eps};
        float[][] result2 = nntest.calculate(input2);
        assertEquals(1.28f, result2[0][0], 1.E-6);

        // do both pixels at once, as in CtpOp:
        float[][] input2D = new float[][]{input1, input2};
        float[][] result2D = nntest.calculate(input2D);
        assertEquals(1.2295055f, result2D[0][0], 1.E-6);
        assertEquals(1.28f, result2D[1][0], 1.E-6);
    }

    @Test
    public void testNNTensorflowApplyModel_RQ() {
        // testing results from various test NNs against RQ results
        // These NNs are stored as test resources
        String modelDir = new File(Objects.requireNonNull(getClass().
                getResource("ctp-i7x14x7o1-0011")).getFile()).getAbsolutePath();
        TensorflowNNCalculator nntest = new TensorflowNNCalculator(modelDir, "none", CTP_TF_NN_NAME_RQ);

        float[] input1 =
                new float[]{0.949078f, 0.968533f, 0.527856f, 0.115890f, 0.573868f, 0.318571f, 0.056420f};
        float[][] input1_2D =
                new float[][]{{0.949078f, 0.968533f, 0.527856f, 0.115890f, 0.573868f, 0.318571f, 0.056420f}};
        float[][] result = nntest.calculate(input1);
        float[][] result_2D = nntest.calculate(input1_2D);

        // from RQ:
        float expected_ctp_1 = 110.577606f;
        assertEquals(expected_ctp_1, result[0][0], 1.E-1);
        assertEquals(expected_ctp_1, result_2D[0][0], 1.E-1);

        float[] input2 =
                new float[]{0.9453334f, 0.955802f, 0.222959f, 0.254912f, 1.179646f, 0.652283f, 0.113749f};
        result = nntest.calculate(input2);
        float expected_ctp_2 = 926.159302f;
        assertEquals(expected_ctp_2, result[0][0], 1.E-2);

        float[] input3 =
                new float[]{0.893111f, 0.706519f, 0.787735f, 0.254498f, 1.117459f, 0.612721f, 0.104478f};
        result = nntest.calculate(input3);
        float expected_ctp_3 = 777.337524f;
        assertEquals(expected_ctp_3, result[0][0], 1.E-2);

        float[] input4 =
                new float[]{0.787874f, 0.830178f, 0.431576f, 0.210399f, 0.472848f, 0.276827f, 0.055080f};
        result = nntest.calculate(input4);
        float expected_ctp_4 = 81.297630f;
        assertEquals(expected_ctp_4, result[0][0], 1.E-3);

        // do all 4 pixels at once, as in CtpOp:
        float[][] input2D = new float[][]{input1, input2, input3, input4};
        float[][] result2D = nntest.calculate(input2D);
        assertEquals(expected_ctp_1, result2D[0][0], 1.E-1);
        assertEquals(expected_ctp_2, result2D[1][0], 1.E-2);
        assertEquals(expected_ctp_3, result2D[2][0], 1.E-2);
        assertEquals(expected_ctp_4, result2D[3][0], 1.E-3);
    }
}
