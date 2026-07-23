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
 * Function interface (univariate).
 */
public interface Function {
    /**
     * Returns the function value at a given abscissa value.
     * If the given abscissa value is not in the domain of the function, @code Double.Nan is returned.
     *
     * @param x The abscissa value.
     * @return the function value.
     */
    double getY(double x);

    /**
     * Returns the maximum abscissa value.
     *
     * @return the maximum abscissa value.
     */
    double getMaxX();

    /**
     * Returns the minimum abscissa value.
     *
     * @return the minimum abscissa value.
     */
    double getMinX();

    /**
     * Returns the maximum function value.
     *
     * @return the maximum function value.
     */
    double getMaxY();

    /**
     * Returns the minimum function value.
     *
     * @return the minimum function value.
     */
    double getMinY();
}
