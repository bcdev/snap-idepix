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

import java.util.Arrays;

abstract class AbstractInterpolationFunction implements InterpolationFunction {
    private final int n;
    private final double[] x;
    private final double[] y;
    private final double maxY;
    private final double minY;

    AbstractInterpolationFunction(int n, double[] x, double[] y) {
        this.n = n;
        this.x = x;
        this.y = y;
        maxY = Arrays.stream(y, 1, n).filter(i -> i > y[0]).max().orElse(y[0]);
        minY = Arrays.stream(y, 1, n).filter(i -> i < y[0]).min().orElse(y[0]);
    }

    protected final int getN() {
        return n;
    }

    protected final double getX(int i) {
        return x[i];
    }

    protected final double getY(int i) {
        return y[i];
    }

    @Override
    public final double getY(double x) {
        if (x < getMinX() || x > getMaxX()) {
            return Double.NaN;
        }

        int inf = 0;
        int sup = n - 1;
        while (sup > inf + 1) {
            final int m = (inf + sup) >> 1;
            if (x < getX(m)) {
                sup = m;
            } else {
                inf = m;
            }
        }

        return interpolation(x, inf, n);
    }

    /**
     * Returns the interpolated function value.
     *
     * @param x The abscissa value.
     * @param i The index of the infimum vertex.
     * @param n The number of vertices.
     * @return the interpolated function value.
     */
    abstract protected double interpolation(double x, int i, int n);

    @Override
    public final double getMaxX() {
        return x[n - 1];
    }

    @Override
    public final double getMinX() {
        return x[0];
    }

    @Override
    public final double getMaxY() {
        return this.maxY;
    }

    @Override
    public final double getMinY() {
        return this.minY;
    }
}
