/*
 * Copyright (C) 2021 Brockmann Consult GmbH (info@brockmann-consult.de)
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
 * with this program; if not, see http://www.gnu.org/licenses/.
 */

package org.esa.snap.idepix.c3solcislstr.mc.variates;

import org.esa.snap.idepix.c3solcislstr.mc.Function;
import org.esa.snap.idepix.c3solcislstr.mc.UniformVariate;

/**
 * A random variate based on an empiric probability density function.
 * Uses the inverse transformation method.
 */
public class EmpiricVariate extends AbstractRandomVariate {

    private final org.esa.snap.idepix.c3solcislstr.mc.Function inverseCDF;
    private final org.esa.snap.idepix.c3solcislstr.mc.UniformVariate uniform;

    /**
     * Creates a new random variate, based on the inverse of an empiric cumulative
     * probability density function.
     *
     * @param inverseCDF The inverse cumulative probability density function.
     * @param uniform    The uniform random variate used to sample the ordinate values.
     */
    public EmpiricVariate(Function inverseCDF, UniformVariate uniform) {
        this.inverseCDF = inverseCDF;
        this.uniform = uniform;
    }

    @Override
    public double nextDouble() {
        final double u;

        synchronized (uniform) {
            u = uniform.nextDouble();
        }

        return inverseCDF.getY((1.0 - u) * inverseCDF.getMinX() + u * inverseCDF.getMaxX());
    }
}
