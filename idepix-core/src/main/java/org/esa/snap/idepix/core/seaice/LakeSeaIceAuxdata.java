/*
 * Copyright (c) 2023.  Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.idepix.core.seaice;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.util.ResourceInstaller;
import org.esa.snap.core.util.SystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class LakeSeaIceAuxdata {

    public static final Path AUXDATA_DIRECTORY = SystemUtils.getAuxDataPath().resolve("idepix/icemaps");
    private static final AtomicBoolean installed = new AtomicBoolean(false);

    public static void install() throws IOException {
        if (!installed.getAndSet(true)) {
            Path codeBasePath = ResourceInstaller.findModuleCodeBasePath(LakeSeaIceClassification.class);
            final Path sourceDirPath = codeBasePath.resolve("auxdata/icemaps");
            final ResourceInstaller resourceInstaller = new ResourceInstaller(sourceDirPath, AUXDATA_DIRECTORY);
            resourceInstaller.install(".*", ProgressMonitor.NULL);
        }
    }
}
