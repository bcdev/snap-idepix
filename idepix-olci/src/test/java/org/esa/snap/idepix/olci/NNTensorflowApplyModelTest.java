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

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class NNTensorflowApplyModelTest {

    @Test
    @Ignore
    public void testNNTensorflowApplyModel() throws IOException {
        // todo: check why test fails
        // static double[] INPUT_1 = {0.15601175, 0.57917833, 0.7634207, 0.02903139, 1.7085681, 0.96827781, 0.15205044};
        final float[] input =
                new float[]{0.15601175f, 0.57917833f, 0.7634207f, 0.02903139f, 1.7085681f, 0.96827781f, 0.15205044f};
        NNTensorflowApplyModel nntest = new NNTensorflowApplyModel(input);

        float[][] result = nntest.getNNResult();
        for (int i = 0; i< nntest.getNnTensorOut(); i++) {
            System.out.println(result[0][i]);
        }
        // static float[] EXPECTED_OUTPUT_1 = {1.3985262F};
        assertEquals(1.3985262f, result[0][0], 1.E-6);
    }
}
