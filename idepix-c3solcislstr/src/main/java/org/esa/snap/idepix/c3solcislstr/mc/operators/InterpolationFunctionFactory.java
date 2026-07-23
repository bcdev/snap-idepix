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

package org.esa.snap.idepix.c3solcislstr.mc.operators;

import org.esa.snap.idepix.c3solcislstr.mc.InterpolationFunction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;

public class InterpolationFunctionFactory {

    private InterpolationFunctionFactory() {
    }

    @SuppressWarnings("SameParameterValue")
    static InterpolationFunction create(String type, File file, String resource) throws FileNotFoundException {
        if (file == null) {
            return create(type, resource);
        } else {
            return create(type, file);
        }
    }

    @NotNull
    static InterpolationFunction create(String type, File file) throws FileNotFoundException {
        return create(type, new FileInputStream(file));
    }

    @SuppressWarnings("SameParameterValue")
    public static InterpolationFunction create(String type, String resource) {
        return create(type, InterpolationFunctionFactory.class.getResourceAsStream(resource));
    }

    @NotNull
    private static InterpolationFunction create(String type, InputStream is) {
        final ArrayList<Double> x = new ArrayList<>(11);
        final ArrayList<Double> y = new ArrayList<>(11);

        try (final Scanner scanner = new Scanner(is)) {
            while (scanner.hasNextDouble()) {
                x.add(scanner.nextDouble());
                y.add(scanner.nextDouble());
            }
        }

        switch (type) {
            case "Linear":
                return new InterpolationFunction.Linear(x.size(),
                        x.stream().mapToDouble(Double::doubleValue).toArray(),
                        y.stream().mapToDouble(Double::doubleValue).toArray());
            case "PrimitiveStep":
                return new InterpolationFunction.PrimitiveStep(x.size(),
                        x.stream().mapToDouble(Double::doubleValue).toArray(),
                        y.stream().mapToDouble(Double::doubleValue).toArray());
            case "InversePrimitiveStep":
                return new InterpolationFunction.InversePrimitiveStep(x.size(),
                        x.stream().mapToDouble(Double::doubleValue).toArray(),
                        y.stream().mapToDouble(Double::doubleValue).toArray());
            default:
                return new InterpolationFunction.Step(x.size(),
                        x.stream().mapToDouble(Double::doubleValue).toArray(),
                        y.stream().mapToDouble(Double::doubleValue).toArray());
        }
    }
}
