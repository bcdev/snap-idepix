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

/**
 * A maximally equidistributed F2-linear generator (MELG). This MELG is formally
 * designated 'MELG19937-64'. It has a state of 2,496 bytes and yields a 64-bit
 * output word.
 *
 * Further reading:
 *
 * S. Harase and T. Kimoto (2018).
 * Implementing 64-bit maximally equidistributed F2-linear generators with Mersenne prime period.
 * ACM Transactions on Mathematical Software, 44, 3, 30.
 * <http://doi.acm.org/10.1145/3159444>, <http://arxiv.org/abs/1505.06582>
 *
 * @author Ralf Quast
 */
public final class Melg extends AbstractRandomVariate implements Multivariate, UniformVariate, RandomNumberGenerator {

    private static final int L = 19;
    private static final int M = 81;
    private static final int N = 311;

    private final long[] state = new long[N + 1];
    private int state_i;
    private int state_c;

    /**
     * Constructs a new instance of this class.
     */
    public Melg() {
        init(0x853c49e6748fea9bL);
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param seed The seed.
     */
    public Melg(long seed) {
        init(seed);
    }

    private void init(long seed) {
        state[0] = seed;

        for (state_i = 1; state_i < N + 1; state_i++) {
            state[state_i] = (state[state_i - 1] ^ (state[state_i - 1] >>> 62)) * 6364136223846793005L + state_i;
        }

        state_i = 0;
        state_c = 1;
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param seeds The seeds.
     */
    public Melg(long[] seeds) {
        init(19650218L);

        int i = 1;
        int j = 0;
        for (int k = Math.max(N, seeds.length); k > 0; k--) {
            state[i] = (state[i] ^ ((state[i - 1] ^ (state[i - 1] >>> 62)) * 3935559000370003845L)) + seeds[j] + j;
            i++;
            j++;
            if (i >= N) {
                state[0] = state[N - 1];
                i = 1;
            }
            if (j >= seeds.length) {
                j = 0;
            }
        }
        for (int k = N - 1; k > 0; k--) {
            state[i] = (state[i] ^ ((state[i - 1] ^ (state[i - 1] >>> 62)) * 2862933555777941757L)) - i;
            i++;
            if (i >= N) {
                state[0] = state[N - 1];
                i = 1;
            }
        }
        state[N] = (state[N] ^ ((state[N - 1] ^ (state[N - 1] >>> 62)) * 2862933555777941757L)) - N;
        state[0] = (state[0] | (1L << 63));
        state_i = 0;
        state_c = 1;
    }

    @Override
    public long nextLong() {
        long next = 0L;

        synchronized (this) {
            switch (state_c) {
                case 1:
                    next = twist1(state_i, state_i + 1);
                    twist2(next, state_i + M);
                    next = twist3(next, state_i, state_i + L);
                    state_i++;
                    if (state_i == N - M) {
                        state_c = 2;
                    }
                    break;
                case 2:
                    next = twist1(state_i, state_i + 1);
                    twist2(next, state_i + M - N);
                    next = twist3(next, state_i, state_i + L);
                    state_i++;
                    if (state_i == N - L) {
                        state_c = 3;
                    }
                    break;
                case 3:
                    next = twist1(state_i, state_i + 1);
                    twist2(next, state_i + M - N);
                    next = twist3(next, state_i, state_i - (N - L));
                    state_i++;
                    if (state_i == N - 1) {
                        state_c = 4;
                    }
                    break;
                case 4:
                    next = twist1(N - 1, 0);
                    twist2(next, M - 1);
                    next = twist3(next, N - 1, state_i - (N - L));
                    state_i = 0;
                    state_c = 1;
                    break;
            }
        }

        return next;
    }

    private long twist1(int i1, int i2) {
        return (state[i1] & 0xffffffff80000000L) | (state[i2] & 0x7fffffffL);
    }

    private void twist2(long l1, int i1) {
        state[N] = (l1 >>> 1) ^ (((l1 & 1L) != 0L) ? 0x5c32e06df730fc42L : 0L) ^ state[i1] ^ (state[N] ^ (state[N] << 23));
    }

    private long twist3(long l1, int i1, int i2) {
        state[i1] = l1 ^ (state[N] ^ (state[N] >>> 33));
        return state[i1] ^ (state[i1] << 16) ^ (state[i2] & 0x6aede6fd97b338ecL);
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * (1.0 / 9007199254740991.0);
    }

    @Override
    public UniformVariate get(int dimension) {
        return this;
    }
}
