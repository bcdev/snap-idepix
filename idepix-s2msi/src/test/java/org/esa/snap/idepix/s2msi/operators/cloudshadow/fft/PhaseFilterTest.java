package org.esa.snap.idepix.s2msi.operators.cloudshadow.fft;

import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class PhaseFilterTest {

    @Test
    public void optimizedGapFinderMatchesLegacyForRandomBinaryMasks() {
        assertMatchesLegacy(17, 19, 4.0, 3.2, 1.0, 183746L, 0.25);
        assertMatchesLegacy(18, 15, 5.0, 4.0, 1.0, 937461L, 0.65);
        assertMatchesLegacy(13, 14, 4.0, 3.2, 1.0, 537198L, 0.0);
        assertMatchesLegacy(13, 14, 4.0, 3.2, 1.0, 719835L, 1.0);
    }

    @Test
    public void optimizedGapFinderMatchesLegacyAtBordersAndThreshold() {
        DoubleMatrix mask = DoubleMatrix.zeros(15, 16);
        int[][] positions = {{0, 0}, {0, 15}, {14, 0}, {14, 15}, {4, 4}, {5, 4}, {4, 5},
                {7, 8}, {8, 8}, {9, 8}, {8, 9}};
        for (int[] position : positions) {
            mask.put(position[0], position[1], 1.0);
        }
        assertMatricesEquivalent(mask, 4.0, 3.2, 1.0);
    }

    private static void assertMatchesLegacy(int rows, int columns, double radius, double innerRadius,
                                            double spacing, long seed, double cloudProbability) {
        Random random = new Random(seed);
        DoubleMatrix mask = DoubleMatrix.zeros(rows, columns);
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) {
                if (random.nextDouble() < cloudProbability) {
                    mask.put(row, column, 1.0);
                }
            }
        }
        assertMatricesEquivalent(mask, radius, innerRadius, spacing);
    }

    private static void assertMatricesEquivalent(DoubleMatrix mask, double radius, double innerRadius,
                                                  double spacing) {
        double[] pixelSpacing = {spacing, spacing};
        int blockSize = 2 * (int) Math.ceil(radius / spacing) + 1;
        int overlap = (int) Math.ceil(radius / spacing);
        PhaseFilter filter = new PhaseFilter(new ComplexDoubleMatrix(mask), blockSize, overlap,
                                             radius, innerRadius, pixelSpacing);

        DoubleMatrix expected = filter.convolutionSimpleGapFinderLegacy();
        DoubleMatrix actual = filter.convolutionSimpleGapFinder();

        assertEquals(expected.rows, actual.rows);
        assertEquals(expected.columns, actual.columns);
        for (int index = 0; index < expected.length; index++) {
            assertEquals("gap value at linear index " + index, expected.get(index), actual.get(index), 1.0e-12);
            assertEquals("threshold decision at linear index " + index,
                         expected.get(index) < -0.1, actual.get(index) < -0.1);
        }
    }
}
