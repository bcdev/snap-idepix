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

package org.esa.snap.idepix.c3solcislstr.mc.generators;

import org.esa.snap.idepix.c3solcislstr.mc.Multivariate;
import org.esa.snap.idepix.c3solcislstr.mc.UniformVariate;
import org.esa.snap.idepix.c3solcislstr.mc.variates.AbstractRandomVariate;

/**
 * A Latin hypercube sampling sequence generator.
 */
public final class LatinHypercube implements Multivariate {

    private final int dimensionality;
    private final long start;
    private final int sampleCount;
    private final UniformVariate u;
    private final double[][] x;

    /**
     * Creates a new Latin hypercube.
     *
     * @param dimensionality The dimensionality of the hypercube.
     * @param sampleCount    The number of samples.
     */
    public LatinHypercube(int dimensionality, int sampleCount) {
        this(dimensionality, sampleCount, 0, new Melg());
    }

    /**
     * Creates a new Latin hypercube.
     *
     * @param dimensionality The dimensionality of the hypercube.
     * @param sampleCount    The number of samples.
     * @param start          The start index.
     */
    public LatinHypercube(int dimensionality, int sampleCount, long start) {
        this(dimensionality, sampleCount, start, new Melg());
    }

    /**
     * Creates a new Latin hypercube.
     *
     * @param dimensionality The dimensionality of the hypercube.
     * @param sampleCount    The number of samples.
     * @param start          The start index.
     * @param u              The uniform variate used for randomization.
     */
    public LatinHypercube(int dimensionality, int sampleCount, long start, UniformVariate u) {
        this.dimensionality = dimensionality;
        this.start = start;
        this.sampleCount = sampleCount;
        this.u = u;

        x = new double[dimensionality][sampleCount];
        generate();
    }

    @Override
    public UniformVariate get(int dimension) {
        return new Impl(x[dimension], Math.toIntExact(Math.abs(start)));
    }

    private void generate() {
        final int h = sampleCount >> 1;
        for (int i = 0; i < dimensionality; ++i) {
            for (int k = 0; k < h; ++k) {
                x[i][k] = (u.nextDouble() + k) / sampleCount;
                x[i][k + h] = 1.0 - x[i][k];
            }
        }
        shuffle();
    }

    private void shuffle() {
        for (int d = 0; d < dimensionality; ++d) {
            for (int i = sampleCount; i > 1; --i) {
                swap(x[d], i - 1, (int) Math.min(i * u.nextDouble(), i - 1));
            }
        }
    }

    private void swap(double[] values, int i, int k) {
        final double v = values[i];
        values[i] = values[k];
        values[k] = v;
    }

    private class Impl extends AbstractRandomVariate implements UniformVariate {

        private final double[] values;
        private int index;

        public Impl(double[] values, int start) {
            this.values = values;
            this.index = start % sampleCount;
        }

        @Override
        public final double nextDouble() {
            synchronized (this) {
                final double result = values[index++];
                if (index >= values.length) {
                    index = 0;
                }
                return result;
            }
        }
    }
}