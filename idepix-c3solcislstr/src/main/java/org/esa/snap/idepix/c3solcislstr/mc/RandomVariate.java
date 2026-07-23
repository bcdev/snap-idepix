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

package org.esa.snap.idepix.c3solcislstr.mc;

/**
 * Interface to generate real-valued random numbers.
 *
 * @author Ralf Quast
 */
public interface RandomVariate {

    /**
     * Generates a new real-valued random number.
     *
     * @return a new real-valued random number.
     */
    double nextDouble();

    /**
     * Generates a sequence of many real-valued random numbers.
     *
     * @param storage The storage.
     * @return the @code storage filled with new real-valued random numbers.
     */
    double[] nextDoubles(double[] storage);
}
