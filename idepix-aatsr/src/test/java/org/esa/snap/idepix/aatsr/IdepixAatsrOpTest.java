/*
 * Copyright (C) 2022 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.idepix.aatsr;

import org.esa.s3tbx.dataio.s3.meris.reprocessing.Meris3rd4thReprocessingAdapter;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.OperatorSpiRegistry;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

public class IdepixAatsrOpTest {

    @Test
    public void testOperatorSpiIsLoaded() {
        OperatorSpiRegistry registry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        OperatorSpi operatorSpi = registry.getOperatorSpi("Idepix.Aatsr");
        assertNotNull(operatorSpi);
        assertEquals("Idepix.Aatsr", operatorSpi.getOperatorAlias());
        assertNotNull(operatorSpi.getOperatorDescriptor());
        assertSame(operatorSpi.getOperatorClass(), operatorSpi.getOperatorDescriptor().getOperatorClass());
    }


}