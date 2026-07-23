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
 * Mere marker interface.
 */
public interface InterpolationFunction extends Function {

    class Linear extends AbstractInterpolationFunction {

        /**
         * Constructs a new linear interpolation function.
         *
         * @param n The number of vertices.
         * @param x The abscissa values (must be an array of at least length @code n).
         * @param y The function values (must be an array of at least length @code n).
         */
        public Linear(int n, double[] x, double[] y) {
            super(n, x, y);
        }

        @Override
        protected final double interpolation(double x, int i, int n) {
            return getY(i) + (x - getX(i)) / (getX(i + 1) - getX(i)) * (getY(i + 1) - getY(i));
        }
    }

    class Step extends AbstractInterpolationFunction {

        /**
         * Constructs a new step interpolation function.
         *
         * @param n The number of vertices.
         * @param x The abscissa values of the step interpolation function (must be an array of at least length @code n).
         * @param y The function values of the step interpolation function (must be an array of at least length @code n).
         */
        public Step(int n, double[] x, double[] y) {
            super(n, x, y);
        }

        @Override
        protected final double interpolation(double x, int i, int n) {
            return x - getX(i) < getX(i + 1) - x ? getY(i) : getY(i + 1);
        }
    }

    class PrimitiveStep extends Linear {

        /**
         * Constructs the primitive function of a step interpolation function.
         *
         * @param n The number of vertices.
         * @param x The abscissa values of the step interpolation function (must be an array of at least length @code n).
         * @param y The function values of the step interpolation function (must be an array of at least length @code n).
         */
        public PrimitiveStep(int n, double[] x, double[] y) {
            super(n + 1, primitiveAbscissaValues(n, x), primitiveFunctionValues(n, x, y));
        }

        private static double[] primitiveAbscissaValues(int n, double[] x) {
            final double[] u = new double[n + 1];

            u[0] = x[0];
            u[n] = x[n - 1];
            for (int i = 0; i < n - 1; i++) {
                u[i + 1] = 0.5 * (x[i] + x[i + 1]);
            }
            return u;
        }

        private static double[] primitiveFunctionValues(int n, double[] x, double[] y) {
            final double[] u = primitiveAbscissaValues(n, x);
            final double[] v = new double[n + 1];

            v[0] = 0.0;
            for (int i = 0; i < n; i++) {
                v[i + 1] = v[i] + (u[i + 1] - u[i]) * y[i];
            }
            return v;
        }
    }

    final class InversePrimitiveStep extends Linear {

        /**
         * Constructs the inverse of the primitive function of a step interpolation
         * function. The primitive function must be strictly monotonous.
         *
         * @param n The number of vertices.
         * @param x The abscissa values of the step interpolation function (must be an array of at least length @code n).
         * @param y The function values of the step interpolation function (must be an array of at least length @code n).
         */
        public InversePrimitiveStep(int n, double[] x, double[] y) {
            super(n + 1, primitiveFunctionValues(n, x, y), primitiveAbscissaValues(n, x));
        }

        private static double[] primitiveAbscissaValues(int n, double[] x) {
            final double[] u = new double[n + 1];

            u[0] = x[0];
            u[n] = x[n - 1];
            for (int i = 0; i < n - 1; i++) {
                u[i + 1] = 0.5 * (x[i] + x[i + 1]);
            }
            return u;
        }

        private static double[] primitiveFunctionValues(int n, double[] x, double[] y) {
            final double[] u = primitiveAbscissaValues(n, x);
            final double[] v = new double[n + 1];

            v[0] = 0.0;
            for (int i = 0; i < n; i++) {
                v[i + 1] = v[i] + (u[i + 1] - u[i]) * y[i];
            }
            return v;
        }
    }

}
