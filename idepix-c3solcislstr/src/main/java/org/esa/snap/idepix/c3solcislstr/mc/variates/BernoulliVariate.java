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

import org.esa.snap.idepix.c3solcislstr.mc.UniformVariate;

/**
 * A Bernoulli random variate.
 */
public class BernoulliVariate extends AbstractRandomVariate {

    private final double p;
    private final UniformVariate uniform;

    /**
     * Creates a new random variate sampled from a Bernoulli distribution of given success probability.
     * @param p The success probability.
     * @param uniform The uniform random variate used to sample from the Bernoulli distribution.
     */
    public BernoulliVariate(double p, UniformVariate uniform) {
        this.p = p;
        this.uniform = uniform;
    }

    @Override
    public double nextDouble() {
        final boolean failure;

        synchronized (uniform) {
            failure = uniform.nextDouble() > p;
        }
        if (failure) {
            return 0.0;
        } else {
            return 1.0;
        }
    }
}
