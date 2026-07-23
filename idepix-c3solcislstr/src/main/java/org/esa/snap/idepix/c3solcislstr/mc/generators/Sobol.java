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
import org.esa.snap.idepix.c3solcislstr.mc.RandomNumberGenerator;
import org.esa.snap.idepix.c3solcislstr.mc.UniformVariate;
import org.esa.snap.idepix.c3solcislstr.mc.variates.AbstractRandomVariate;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;

/**
 * A Sobol sequence generator.
 * <p>
 * A Sobol sequence is a low-discrepancy sequence with the property that for all values of N,
 * its subsequence (x_1, ... x_n) has a low discrepancy. It can be used to generate quasi-random
 * points in a space S, which are equidistributed.
 * <p>
 * The implementation supports up to 21201 dimensions with direction numbers calculated
 * by [Stephen Joe and Frances Kuo](http://web.maths.unsw.edu.au/~fkuo/sobol/).
 */
public final class Sobol implements Multivariate {

    private final int dimensionality;
    private long start;

    /**
     * Creates a new 1-D Sobol sequence.
     */
    public Sobol() {
        this(1);
    }

    /**
     * Creates a new Sobol sequence of given dimensionality.
     *
     * @param dimensionality The dimensionality of the Sobol sequence.
     */
    public Sobol(int dimensionality) {
        this.dimensionality = dimensionality;
        this.start = dimensionality;
    }

    /**
     * Sets the start position of the Sobol sequence to its default, which equals its dimensionality.
     *
     * @return a Sobol sequence starting at the default position.
     */
    public Sobol start() {
        this.start = this.dimensionality;
        return this;
    }

    /**
     * Sets the start position of the Sobol sequence.
     *
     * @param start The start position.
     * @return a Sobol sequence starting at the given position.
     */
    public Sobol start(long start) {
        this.start = start;
        return this;
    }

    /**
     * Returns a new uniform variate for a given dimension, based on this Sobol sequence.
     *
     * @param dimension The dimension.
     * @return a new uniform variate.
     */
    @Override
    public UniformVariate get(int dimension) {
        return new Impl(dimensionality, start, dimension);
    }

    private static class Impl extends AbstractRandomVariate implements RandomNumberGenerator, UniformVariate {

        /**
         * The maximum dimensionality.
         */
        private static final int MAX_DIMENSIONALITY = 21201;

        /**
         * The number of bits used.
         */
        private static final int W = 52;

        /**
         * To mask the significant bits.
         */
        private static final long M = (1L << W) - 1;

        /**
         * To convert integral values into real values in [0, 1).
         */
        private static final double TO_DOUBLE = 1L << W;

        /**
         * The dimensionality.
         */
        private final int n;

        /**
         * The selected dimension.
         */
        private final int d;

        /**
         * The direction vector for each dimension.
         */
        private final long[][] directions;

        /**
         * The state of the generator.
         */
        private final long[] x;

        /**
         * The current position within the Sobol sequence.
         */
        private long p;

        /**
         * Creates a new instance of this class.
         *
         * @param dimensionality The dimensionality of the Sobol sequence.
         * @param start          The start position within the Sobol sequence.
         */
        private Impl(int dimensionality, long start, int dimension) {
            if (dimensionality < 1 || dimensionality > MAX_DIMENSIONALITY) {
                throw new IllegalArgumentException("Illegal dimensionality: " + dimensionality);
            }
            if (dimension < 0 || dimension >= dimensionality) {
                throw new IllegalArgumentException("Illegal dimension: " + dimension);
            }
            n = dimensionality;
            d = dimension;
            directions = new long[n][W + 1];
            x = new long[n];
            initDirections();
            setPosition(start & M);
        }

        @Override
        public long nextLong() {
            synchronized (this) {
                next();
                return x[d];
            }
        }

        @Override
        public double nextDouble() {
            return nextLong() / TO_DOUBLE;
        }

        private void initDirections() {
            // initialize the first dimension
            for (int i = 1; i <= W; i++) {
                directions[0][i] = 1L << (W - i);
            }
            // initialize the remaining dimensions
            if (n > 1) {
                final InputStream is = getClass().getResourceAsStream("new-joe-kuo-6.21201.txt");
                if (is == null) {
                    throw new IllegalStateException("The internal resource file could not be read.");
                }
                try (final Scanner scanner = new Scanner(is, "US-ASCII")) {
                    scanner.nextLine();  // skip the header
                    while (scanner.hasNextLine()) {
                        final int d = scanner.nextInt();  // dimension
                        final int s = scanner.nextInt();
                        final int a = scanner.nextInt();
                        final long[] m = new long[s + 1];
                        for (int i = 1; i <= s; i++) {
                            m[i] = scanner.nextLong();
                        }
                        initDirection(d - 1, a, m);
                        if (d == n) {
                            break;
                        }
                    }
                }
            }
        }

        private void initDirection(int d, int a, long[] m) {
            final int s = m.length - 1;
            for (int i = 1; i <= s; i++) {
                directions[d][i] = m[i] << (W - i);
            }
            for (int i = s + 1; i <= W; i++) {
                directions[d][i] = directions[d][i - s] ^ (directions[d][i - s] >>> s);
                for (int k = 1; k <= s - 1; k++) {
                    directions[d][i] ^= ((a >>> (s - 1 - k)) & 1) * directions[d][i - k];
                }
            }
        }

        private void setPosition(long position) {
            if (position == 0) {
                Arrays.fill(x, 0);
            } else {
                final long l = position - 1;
                final long grayCode = l ^ (l >>> 1);
                for (int i = 0; i < n; i++) {
                    long result = 0;
                    for (int k = 1; k <= W; k++) {
                        final long shift = grayCode >>> (k - 1);
                        if (shift == 0) { // stop, as all remaining bits will be zero
                            break;
                        }
                        // the k-th bit of i
                        final long ik = shift & 1;
                        result ^= ik * directions[i][k];
                    }
                    x[i] = result;
                }
            }
            p = position;
        }

        private void next() {
            if (p != 0) {
                // find the index c of the rightmost 0
                int c = 1;
                long value = p - 1;
                while ((value & 1) == 1) {
                    value >>>= 1;
                    c++;
                }
                for (int i = 0; i < n; i++) {
                    x[i] = x[i] ^ directions[i][c];
                }
            }
            p = ++p & M;
        }
    }

}