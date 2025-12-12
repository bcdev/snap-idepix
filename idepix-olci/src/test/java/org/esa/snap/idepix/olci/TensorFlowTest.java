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
import org.tensorflow.SavedModelBundle;
import org.tensorflow.TensorFlow;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class TensorFlowTest {

    private static final String SAVED_MODEL_PATH;
    private static final String SAVED_MODEL_PY_PATH;
    private static final String SAVED_MODEL_JW_PATH;

    private static final String AUXDATA_PATH;

    static {
        try {
            SAVED_MODEL_PATH =
                    Paths.get(TensorFlowTest.class.getResource("/saved_model").toURI()).toString();
            SAVED_MODEL_PY_PATH =
                    Paths.get(TensorFlowTest.class.getResource("/saved_model_using_python/model").toURI())
                            .toString();
            SAVED_MODEL_JW_PATH =
                    Paths.get(TensorFlowTest.class.getResource("/jw_model").toURI()).toString();
            AUXDATA_PATH = IdepixOlciUtils.installAuxdataNNCtp();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    public void testGetVersion() {
        final String version = TensorFlow.version();
        assertNotNull(version);
        assertEquals("2.16.2", version);
    }

    @Test
    public void testLoad_1() {
        try (SavedModelBundle bundle = SavedModelBundle.load(SAVED_MODEL_PATH)) {
            assertNotNull(bundle.session());
            assertNotNull(bundle.graph());
            assertNotNull(bundle.metaGraphDef());
        }
        try (SavedModelBundle bundle = SavedModelBundle.load(SAVED_MODEL_PY_PATH, "serve")) {
            assertNotNull(bundle.session());
            assertNotNull(bundle.graph());
            assertNotNull(bundle.metaGraphDef());
        }
    }

    @Test
    public void testLoad_JW() {
        try (SavedModelBundle bundle = SavedModelBundle.load(SAVED_MODEL_JW_PATH)) {
            assertNotNull(bundle.session());
            assertNotNull(bundle.graph());
            assertNotNull(bundle.metaGraphDef());
        }
    }

    @Test
    public void testLoad_JW_from_auxdata() {
        String modelDir = AUXDATA_PATH + File.separator + CtpOp.DEFAULT_TENSORFLOW_NN_DIR_NAME;
        try (SavedModelBundle bundle = SavedModelBundle.load(modelDir)) {
            assertNotNull(bundle.session());
            assertNotNull(bundle.graph());
            assertNotNull(bundle.metaGraphDef());
        }
    }



}
