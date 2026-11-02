package org.esa.snap.idepix.s2msi.operators.cloudshadow.fft;

import org.jblas.ComplexDouble;
import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;

import static org.jblas.MatrixFunctions.powi;

public class PhaseFilter {

    // Original in s1tbx: PhaseFilter class for now only aggregates static methods for phase filtering of InSAR data
    // here: reduced to convolution

    private ComplexDoubleMatrix data;
    private ComplexDoubleMatrix kernel2d_ring;
    private ComplexDoubleMatrix kernel2d_circle;

    private double[] spacing = new double[2];

    public PhaseFilter(ComplexDoubleMatrix data, int blockSize, int overlap, double kernelRadius,
                       double kernelInnerRadius, double[] spacing) {
        this.data = data;
        setSpacing(spacing);

        this.kernel2d_ring = constructCircularKernel(kernelRadius, kernelInnerRadius);
        this.kernel2d_circle = constructCircularKernel(kernelRadius, 0.);
    }

    private void setSpacing(double[] spacing) {
        this.spacing[0] = spacing[0];
        this.spacing[1] = spacing[1];
    }

    public void setData(ComplexDoubleMatrix data) {
        this.data = data;
    }

    public ComplexDoubleMatrix getData() {
        return data;
    }


    public ComplexDoubleMatrix convolutionSimple(ComplexDoubleMatrix filter) {

        // allocate output - same dimensions as input
        int totalY = data.rows;
        int totalX = data.columns;
        final ComplexDoubleMatrix outData = new ComplexDoubleMatrix(totalY, totalX);
        int blockSize = filter.columns;
        int center = (int) Math.ceil(blockSize/2.)-1;
        // squared block assumed!
        // blockSize gives the length of the square filter side.
        // the position of the filter has to be iterated.
        // Only the central position receives a result.

        //Iteration over all central pixels, which can be at the center of the filter.
        for(int x = center; x < totalX - center; x++){    //column
            for(int y = center; y < totalY - center; y++){ //row

                ComplexDoubleMatrix block = new ComplexDoubleMatrix(blockSize, blockSize);
                for(int i=0; i<blockSize; i++){ //column
                    for(int j=0; j<blockSize; j++){ //row
                        block.put(j, i, data.get(j+y-center, i+x-center));
                    }
                }
                // Convolution: get spectrum + filter + ifft
                LinearAlgebraUtils.dotmult_inplace(block, filter); // the filter...
                outData.put(y,x, block.sum());
            }
        }
        return outData;
    }

    public DoubleMatrix convolutionSimpleGapFinder() {
        final int rowCount = data.rows;
        final int columnCount = data.columns;
        final int blockSize = kernel2d_circle.columns;
        final int center = (int) Math.ceil(blockSize / 2.) - 1;

        // Prefix sums along every matrix row turn each horizontal kernel segment into
        // two array lookups. A circular kernel has one segment per row; the ring has
        // at most two. This retains the legacy kernel and edge footprint while reducing
        // the work from O(pixels * radius^2) to O(pixels * radius).
        final int prefixStride = columnCount + 1;
        final double[] rowPrefix = new double[rowCount * prefixStride];
        for (int row = 0; row < rowCount; row++) {
            int prefixOffset = row * prefixStride;
            double sum = 0.0;
            for (int column = 0; column < columnCount; column++) {
                int complexDataIndex = 2 * (row + column * rowCount);
                sum += data.data[complexDataIndex];
                rowPrefix[prefixOffset + column + 1] = sum;
            }
        }

        final KernelSegments circle = KernelSegments.from(kernel2d_circle, blockSize);
        final KernelSegments ring = KernelSegments.from(kernel2d_ring, blockSize);
        final DoubleMatrix output = new DoubleMatrix(rowCount, columnCount);

        for (int column = 0; column < columnCount; column++) {
            int minimumColumnOffset = Math.max(-center, -column);
            int maximumColumnOffset = Math.min(center, columnCount - 1 - column);
            // Preserve the legacy top/left edge footprint, which omits the positive
            // outermost kernel row/column for pixels before the kernel center.
            if (column < center) {
                maximumColumnOffset = Math.min(maximumColumnOffset, center - 1);
            }
            for (int row = 0; row < rowCount; row++) {
                int minimumRowOffset = Math.max(-center, -row);
                int maximumRowOffset = Math.min(center, rowCount - 1 - row);
                if (row < center) {
                    maximumRowOffset = Math.min(maximumRowOffset, center - 1);
                }

                double circleSum = convolveAt(rowPrefix, prefixStride, row, column, center,
                                              minimumRowOffset, maximumRowOffset,
                                              minimumColumnOffset, maximumColumnOffset, circle);
                double ringSum = convolveAt(rowPrefix, prefixStride, row, column, center,
                                            minimumRowOffset, maximumRowOffset,
                                            minimumColumnOffset, maximumColumnOffset, ring);
                output.put(row, column, circleSum - ringSum);
            }
        }
        return output;
    }

    private static double convolveAt(double[] rowPrefix, int prefixStride, int outputRow, int outputColumn,
                                     int center, int minimumRowOffset, int maximumRowOffset,
                                     int minimumColumnOffset, int maximumColumnOffset,
                                     KernelSegments kernel) {
        double sum = 0.0;
        for (int rowOffset = minimumRowOffset; rowOffset <= maximumRowOffset; rowOffset++) {
            int kernelRow = rowOffset + center;
            int[] segments = kernel.segmentsByRow[kernelRow];
            int prefixOffset = (outputRow + rowOffset) * prefixStride;
            for (int segment = 0; segment < segments.length; segment += 2) {
                int fromOffset = Math.max(segments[segment] - center, minimumColumnOffset);
                int toOffset = Math.min(segments[segment + 1] - center, maximumColumnOffset);
                if (fromOffset <= toOffset) {
                    int fromColumn = outputColumn + fromOffset;
                    int toColumn = outputColumn + toOffset;
                    sum += (rowPrefix[prefixOffset + toColumn + 1] -
                            rowPrefix[prefixOffset + fromColumn]) * kernel.weight;
                }
            }
        }
        return sum;
    }

    private static final class KernelSegments {
        private final int[][] segmentsByRow;
        private final double weight;

        private KernelSegments(int[][] segmentsByRow, double weight) {
            this.segmentsByRow = segmentsByRow;
            this.weight = weight;
        }

        private static KernelSegments from(ComplexDoubleMatrix kernel, int blockSize) {
            int[][] segmentsByRow = new int[blockSize][];
            double weight = 0.0;
            for (int row = 0; row < blockSize; row++) {
                int[] segments = new int[4];
                int segmentCount = 0;
                int start = -1;
                for (int column = 0; column < blockSize; column++) {
                    double value = kernel.get(row, column).real();
                    if (value != 0.0) {
                        if (weight == 0.0) {
                            weight = value;
                        } else if (Double.doubleToLongBits(weight) != Double.doubleToLongBits(value)) {
                            throw new IllegalStateException("Gap-finder kernel must have uniform non-zero weights");
                        }
                        if (start < 0) {
                            start = column;
                        }
                    } else if (start >= 0) {
                        if (segmentCount == segments.length) {
                            int[] expanded = new int[segments.length * 2];
                            System.arraycopy(segments, 0, expanded, 0, segments.length);
                            segments = expanded;
                        }
                        segments[segmentCount++] = start;
                        segments[segmentCount++] = column - 1;
                        start = -1;
                    }
                }
                if (start >= 0) {
                    if (segmentCount == segments.length) {
                        int[] expanded = new int[segments.length * 2];
                        System.arraycopy(segments, 0, expanded, 0, segments.length);
                        segments = expanded;
                    }
                    segments[segmentCount++] = start;
                    segments[segmentCount++] = blockSize - 1;
                }
                int[] compact = new int[segmentCount];
                System.arraycopy(segments, 0, compact, 0, segmentCount);
                segmentsByRow[row] = compact;
            }
            return new KernelSegments(segmentsByRow, weight);
        }
    }

    DoubleMatrix convolutionSimpleGapFinderLegacy() {

        // filters have the same size (now)

        // allocate output - same dimensions as input
        int totalY = data.rows;
        int totalX = data.columns;
        final DoubleMatrix outData = new DoubleMatrix(totalY, totalX);

        int blockSize = kernel2d_circle.columns;
        int center = (int) Math.ceil(blockSize / 2.) - 1;

        // squared block assumed!
        // blockSize gives the length of the square filter side.
        // the position of the filter has to be iterated.
        // Only the central position receives a result.

        // Convolution by hand...
        //Iteration over all central pixels, which can be at the center of the filter.


        for (int x = center; x < totalX - center; x++) {    //column
            for (int y = center; y < totalY - center; y++) { //row

                ComplexDoubleMatrix block = new ComplexDoubleMatrix(blockSize, blockSize);
                for (int i = 0; i < blockSize; i++) { //column
                    for (int j = 0; j < blockSize; j++) { //row
                        block.put(j, i, data.get(j + y - center, i + x - center));
                    }
                }

                ComplexDoubleMatrix block2 = new ComplexDoubleMatrix(blockSize, blockSize);
                block2.copy(block);

                LinearAlgebraUtils.dotmult_inplace(block, kernel2d_circle);
                LinearAlgebraUtils.dotmult_inplace(block2, kernel2d_ring);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }
        // Consider edges. Adjusting size of analysed matrix according to largest possible extent.
        // todo corners??
        // left side
        for (int x = center; x < totalX - center; x++) {    //column
            for (int y = 0; y < center; y++) { //row

                ComplexDoubleMatrix block = new ComplexDoubleMatrix(center + y, blockSize);
                ComplexDoubleMatrix filterRing = new ComplexDoubleMatrix(center + y, blockSize);
                ComplexDoubleMatrix filterCircle = new ComplexDoubleMatrix(center + y, blockSize);

                for (int i = 0; i < blockSize; i++) { //column
                    for (int j = 0; j < center + y; j++) { //row
                        block.put(j, i, data.get(j, i + x - center));
                        filterCircle.put(j, i, kernel2d_circle.get(center - y + j, i));
                        filterRing.put(j, i, kernel2d_ring.get(center - y + j, i));
                    }
                }

                ComplexDoubleMatrix block2 = new ComplexDoubleMatrix(center + y, blockSize);
                block2.copy(block);


                LinearAlgebraUtils.dotmult_inplace(block, filterCircle);
                LinearAlgebraUtils.dotmult_inplace(block2, filterRing);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }
        // upper side
        for (int x = 0; x < center; x++) {    //column
            for (int y = center; y < totalY - center; y++) { //row

                ComplexDoubleMatrix block = new ComplexDoubleMatrix(blockSize, center + x);
                ComplexDoubleMatrix filterRing = new ComplexDoubleMatrix(blockSize, center + x);
                ComplexDoubleMatrix filterCircle = new ComplexDoubleMatrix(blockSize, center + x);
                for (int i = 0; i < center + x; i++) { //column
                    for (int j = 0; j < blockSize; j++) { //row

                        block.put(j, i, data.get(j + y - center, i));
                        filterCircle.put(j, i, kernel2d_circle.get(j, center - x + i));
                        filterRing.put(j, i, kernel2d_ring.get(j, center - x + i));
                    }
                }

                ComplexDoubleMatrix block2 = new ComplexDoubleMatrix(blockSize, center + x);
                block2.copy(block);

                LinearAlgebraUtils.dotmult_inplace(block, filterCircle);
                LinearAlgebraUtils.dotmult_inplace(block2, filterRing);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }

        // right side
        for (int x = center; x < totalX - center; x++) {    //column
            for (int y = totalY - center; y < totalY; y++) { //row

                ComplexDoubleMatrix block =
                        new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1, blockSize);
                ComplexDoubleMatrix filterRing =
                        new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1, blockSize);
                ComplexDoubleMatrix filterCircle =
                        new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1, blockSize);

                for (int i = 0; i < blockSize; i++) { //column
                    for (int j = 0; j < blockSize - (y - (totalY - center)) - 1; j++) { //row
                        block.put(j, i, data.get(y + j - center, i + x - center));
                        filterCircle.put(j, i, kernel2d_circle.get(j, i));
                        filterRing.put(j, i, kernel2d_ring.get(j, i));
                    }
                }

                ComplexDoubleMatrix block2 =
                        new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1, blockSize);
                block2.copy(block);

                LinearAlgebraUtils.dotmult_inplace(block, filterCircle);
                LinearAlgebraUtils.dotmult_inplace(block2, filterRing);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }

        //  bottom side
        for (int x = totalX - center; x < totalX; x++) {    //column
            for (int y = center; y < totalY - center; y++) { //row

                ComplexDoubleMatrix block = new ComplexDoubleMatrix(blockSize,
                        blockSize - (x - (totalX - center)) - 1);
                ComplexDoubleMatrix filterRing = new ComplexDoubleMatrix(blockSize,
                        blockSize - (x - (totalX - center)) - 1);
                ComplexDoubleMatrix filterCircle = new ComplexDoubleMatrix(blockSize,
                        blockSize - (x - (totalX - center)) - 1);

                for (int i = 0; i < blockSize - (x - (totalX - center)) - 1; i++) { //column
                    for (int j = 0; j < blockSize; j++) { //row
                        block.put(j, i, data.get(y + j - center, i + x - center));
                        filterCircle.put(j, i, kernel2d_circle.get(j, i));
                        filterRing.put(j, i, kernel2d_ring.get(j, i));
                    }
                }

                ComplexDoubleMatrix block2 = new ComplexDoubleMatrix(blockSize,
                        blockSize - (x - (totalX - center)) - 1);
                block2.copy(block);

                LinearAlgebraUtils.dotmult_inplace(block, filterCircle);
                LinearAlgebraUtils.dotmult_inplace(block2, filterRing);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }

        // corners
        // upper left
        for (int x = 0; x < center; x++) {    //column
            for (int y = 0; y < center; y++) { //row
                ComplexDoubleMatrix block = new ComplexDoubleMatrix(center + y, center + x);
                ComplexDoubleMatrix filterRing = new ComplexDoubleMatrix(center + y, center + x);
                ComplexDoubleMatrix filterCircle = new ComplexDoubleMatrix(center + y, center + x);

                for (int i = 0; i < center + x; i++) { //column
                    for (int j = 0; j < center + y; j++) { //row
                        block.put(j, i, data.get(j, i));
                        filterCircle.put(j, i, kernel2d_circle.get(center - y + j, center - x + i));
                        filterRing.put(j, i, kernel2d_ring.get(center - y + j, center - x + i));
                    }
                }

                ComplexDoubleMatrix block2 = new ComplexDoubleMatrix(center + y, center + x);
                block2.copy(block);

                LinearAlgebraUtils.dotmult_inplace(block, filterCircle);
                LinearAlgebraUtils.dotmult_inplace(block2, filterRing);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }
        // upper right
        for (int x = 0; x < center; x++) {    //column
            for (int y = totalY - center; y < totalY; y++) { //row

                ComplexDoubleMatrix block =
                        new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1, center + x);
                ComplexDoubleMatrix filterRing =
                        new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1, center + x);
                ComplexDoubleMatrix filterCircle =
                        new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1, center + x);

                for (int i = 0; i < center + x; i++) { //column
                    for (int j = 0; j < blockSize - (y - (totalY - center)) - 1; j++) { //row
                        block.put(j, i, data.get(y + j - center, i));
                        filterCircle.put(j, i, kernel2d_circle.get(j, center - x + i));
                        filterRing.put(j, i, kernel2d_ring.get(j, center - x + i));
                    }
                }

                ComplexDoubleMatrix block2 =
                        new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1, center + x);
                block2.copy(block);

                LinearAlgebraUtils.dotmult_inplace(block, filterCircle);
                LinearAlgebraUtils.dotmult_inplace(block2, filterRing);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }

        // lower left
        for (int x = totalX - center; x < totalX; x++) {
            for (int y = 0; y < center; y++) { //row

                ComplexDoubleMatrix block = new ComplexDoubleMatrix(center + y,
                        blockSize - (x - (totalX - center)) - 1);
                ComplexDoubleMatrix filterRing = new ComplexDoubleMatrix(center + y,
                        blockSize - (x - (totalX - center)) - 1);
                ComplexDoubleMatrix filterCircle = new ComplexDoubleMatrix(center + y,
                        blockSize - (x - (totalX - center)) - 1);

                for (int i = 0; i < blockSize - (x - (totalX - center)) - 1; i++) { //column
                    for (int j = 0; j < center + y; j++) { //row
                        block.put(j, i, data.get(j, x + i - center));
                        filterCircle.put(j, i, kernel2d_circle.get(center - y + j, i));
                        filterRing.put(j, i, kernel2d_ring.get(center - y + j, i));
                    }
                }

                ComplexDoubleMatrix block2 = new ComplexDoubleMatrix(center + y,
                        blockSize - (x - (totalX - center)) - 1);
                block2.copy(block);

                LinearAlgebraUtils.dotmult_inplace(block, filterCircle);
                LinearAlgebraUtils.dotmult_inplace(block2, filterRing);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }


        // lower right
        for (int x = totalX - center; x < totalX; x++) {    //column
            for (int y = totalY - center; y < totalY; y++) { //row

                ComplexDoubleMatrix block = new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1,
                        blockSize - (x - (totalX - center)) - 1);
                ComplexDoubleMatrix filterRing = new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1,
                        blockSize - (x - (totalX - center)) - 1);
                ComplexDoubleMatrix filterCircle = new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1,
                        blockSize - (x - (totalX - center)) - 1);

                for (int i = 0; i < blockSize - (x - (totalX - center)) - 1; i++) { //column
                    for (int j = 0; j < blockSize - (y - (totalY - center)) - 1; j++) { //row
                        block.put(j, i, data.get(y + j - center, i + x - center));
                        filterCircle.put(j, i, kernel2d_circle.get(j, i));
                        filterRing.put(j, i, kernel2d_ring.get(j, i));
                    }
                }

                ComplexDoubleMatrix block2 = new ComplexDoubleMatrix(blockSize - (y - (totalY - center)) - 1,
                        blockSize - (x - (totalX - center)) - 1);
                block2.copy(block);

                LinearAlgebraUtils.dotmult_inplace(block, filterCircle);
                LinearAlgebraUtils.dotmult_inplace(block2, filterRing);

                outData.put(y, x, block.sum().real() - block2.sum().real());
            }
        }

        return outData;
    }

    public ComplexDoubleMatrix convolutionSpecialTest(ComplexDoubleMatrix kernel) {

        // allocate output - same dimensions as input
        int totalY = data.rows;
        int totalX = data.columns;
        //final ComplexDoubleMatrix outData = new ComplexDoubleMatrix(totalY, totalX);

        int blockSize = kernel.columns;
        int center = (int) Math.ceil(blockSize / 2.);

        ///
        // kernel gets extended, padded with zeros.
        // int thisDim = totalX;
        // if( totalY < totalX) thisDim = totalY;
        ComplexDoubleMatrix kernelData = new ComplexDoubleMatrix(totalY, totalX);
        for (int i = 0; i < blockSize; i++) { //column
            for (int j = 0; j < blockSize; j++) { //row
                kernelData.put(j, i, kernel.get(j, i));
            }
        }

        SpectralUtils.fft2D_inplace(kernelData);
        ComplexDoubleMatrix thisData = new ComplexDoubleMatrix(totalY, totalX);
        thisData.copy(data);

        // Convolution: get spectrum + filter + ifft
        SpectralUtils.fft2D_inplace(thisData);
        LinearAlgebraUtils.dotmult_inplace(thisData, kernelData); // the filter...
        SpectralUtils.invfft2D_inplace(thisData);

        return thisData;
    }

    private ComplexDoubleMatrix constructCircularKernel(double radius, double radius_inner) {

        int[] nhkern = new int[2];
        for (int i = 0; i < 2; i++) nhkern[i] = (int) Math.ceil(radius / spacing[i]);

        int Nx = 2 * nhkern[1] + 1;
        int Ny = 2 * nhkern[0] + 1;

        /*
        Filter width is fixed at 1000m. If the resolution is getting coarser (resampling to 300m or more),
        the filter gets very small in pixel size. To avoid that its width reduces to one (or zero), it is now tested.
        minimum width of the filter is 9 pixels, the spacing is adjusted.
         */
        if (Nx < 9) {
            Nx = 9;
            spacing[0] = 2 * 1000. / (Nx - 1);
        }
        if (Ny < 9) {
            Ny = 9;
            spacing[1] = 2 * 1000. / (Ny - 1);
        }

        double ydist;
        double xdist;

        double[][] kernel = new double[Ny][Nx];
        int N = 0;

        for (int i = 0; i < Ny; i++) {
            for (int j = 0; j < Nx; j++) {
                ydist = (i - nhkern[0]) * spacing[0];
                xdist = (j - nhkern[1]) * spacing[1];

                if (radius > 0.) {
                    if (Math.sqrt(Math.pow(ydist, 2) + Math.pow(xdist, 2)) < radius) {
                        if (radius_inner > 0.) {
                            if (Math.sqrt(Math.pow(ydist, 2) + Math.pow(xdist, 2)) > radius_inner) {
                                kernel[i][j] = 1.;
                                N++;
                            }
                        } else {
                            kernel[i][j] = 1.;
                            N++;
                        }
                    }
                }
            }
        }

        if (N > 0) {
            for (int i = 0; i < Ny; i++) {
                for (int j = 0; j < Nx; j++) {
                    kernel[i][j] /= N;
                }
            }
        }

        ComplexDoubleMatrix kernel2d = new ComplexDoubleMatrix(kernel);
        return kernel2d;

    }

}
