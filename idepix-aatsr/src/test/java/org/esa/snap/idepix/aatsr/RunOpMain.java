/*
 * Copyright (c) 2022.  Brockmann Consult GmbH (info@brockmann-consult.de)
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
 *
 */

package org.esa.snap.idepix.aatsr;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.util.SystemUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.logging.Level;

/**
 * @author Marco Peters
 */
public class RunOpMain {

    public static void main(String[] args) throws IOException {
        SystemUtils.init3rdPartyLibs(RunOpMain.class);
//        final Product aatsr = ProductIO.readProduct("H:/SENTINEL3/AATSR4RP/v2.0.5/ENV_AT_1_RBT____20021129T235200_20021130T013735_20210315T024827_6334_011_359______DSI_R_NT_004.SEN3/xfdumanifest.xml");
//        final Product aatsr = ProductIO.readProduct("H:\\related\\QA4EO\\AATSR4th Cloud Shadow\\ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004.dim");
        final Product aatsr = ProductIO.readProduct("H:\\related\\QA4EO\\AATSR4th Cloud Shadow\\ENV_AT_1_RBT____20090615T133401_20090615T151936_20210625T140648_6334_079_496______DSI_R_NT_004.SEN3");

        Instant start = Instant.now();

        final HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("copySourceBands", false);
        parameters.put("cloudTopHeight", 6000);
        final Product shadowProduct = GPF.createProduct("Idepix.Aatsr", parameters, aatsr);
        ProductIO.writeProduct(shadowProduct, "H:\\related\\QA4EO\\AATSR4th Cloud Shadow\\" + aatsr.getName() + "_shadowOnly.dim", "BEAM-DIMAP");
        Instant stop = Instant.now();
        SystemUtils.LOG.log(Level.INFO, "DURATION: " + Duration.between(start, stop));
    }
}
