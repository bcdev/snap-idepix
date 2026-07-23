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
 * A permuted congruential random number generator (PCG). This PCG is formally designated
 * 'PCG-XSH-RR'. It has a 64-bit state and yields a 32-bit output.
 *
 * @implNote [PCG, A Family of Better Random Number Generators](https://www.pcg-random.org)
 *
 * @author Ralf Quast
 */
public final class Pcg extends AbstractRandomVariate implements Multivariate, UniformVariate, RandomNumberGenerator {

    /**
     * The state of the random number generator (ANSI C type: uint64_t).
     */
    private long state;

    /**
     * The increment (ANSI C type: uint64_t).
     */
    private long increment;

    /**
     * Creates a new instance of this class.
     */
    public Pcg() {
        init();
    }

    private void init() {
        state = 0x853c49e6748fea9bL;
        increment = 0xda3e39cb94b95bdbL;
    }

    /**
     * Creates a new instance of this class.
     *
     * @param selector The stream selector.
     */
    public Pcg(long selector) {
        init(selector);
    }

    private void init(long selector) {
        state = 0x853c49e6748fea9bL;
        increment = (selector << 1L) | 1L;
    }

    /**
     * Creates a new instance of this class.
     *
     * @param seed The initial state.
     * @param selector The stream selector.
     */
    public Pcg(long seed, long selector) {
        init(seed, selector);
    }

    private void init(long seed, long selector) {
        state = 0L;
        increment = (selector << 1L) | 1L;
        nextLong();
        state += seed;
        nextLong();
    }

    /**
     * Creates a new instance of this class.
     *
     * @param seeds The seeds (only the first and the second seed are used).
     */
    public Pcg(long... seeds) {
        if (seeds == null) {
            init();
        } else if (seeds.length == 1) {
            init(seeds[0]);
        } else {
            init(seeds[0], seeds[1]);
        }
    }

    @Override
    public long nextLong() {
        final long saved;

        synchronized (this) {
            saved = state;
            state = saved * 6364136223846793005L + increment;
        }

        final long s = (((saved >>> 18) ^ saved) >>> 27) & 0xffffffffL;
        final long r = saved >>> 59;
        return ((s >>> r) | (s << ((-r) & 31))) & 0xffffffffL;
    }

    @Override
    public double nextDouble() {
        return nextLong() * (1.0 / 4294967295.0);
    }

    @Override
    public UniformVariate get(int dimension) {
        return this;
    }
}
