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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.idepix.c3solcislstr.mc.Function;
import org.esa.snap.idepix.c3solcislstr.mc.RandomVariate;
import org.esa.snap.idepix.c3solcislstr.mc.UniformVariate;
import org.esa.snap.idepix.c3solcislstr.mc.UniformVariateFactory;
import org.esa.snap.idepix.c3solcislstr.mc.variates.BernoulliVariate;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.pointop.*;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.c3solcislstr.rad2refl.Rad2ReflConstants;
import org.esa.snap.idepix.core.IdepixConstants;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

/**
 * Operator to mutate the IdePix cloud mask (over land). For use in Monte Carlo simulations.
 *
 * @author Ralf Quast
 */
@OperatorMetadata(alias = "CloudMaskMutator",
        category = "OLCI",
        version = "0.1",
        authors = "Ralf Quast",
        copyright = "(c) 2021 by Brockmann Consult",
        description = "Mutates the OLCI IdePix cloud mask (over land). For use in Monte Carlo simulations.")
public class CloudMaskMutationOp extends PixelOperator {

    @Parameter(label = "Random number generator",
            description = "The type of random number generator",
            defaultValue = "MELG", valueSet = {"MELG", "PCG"})
    private String rngType;

    @Parameter(label = "Seed number",
            description = "A numeric value to seed the random number generator",
            defaultValue = "5489")
    private long seedNumber;

    @Parameter(label = "Seed string",
            description = "An alphanumeric value to seed the random number generator (US-ASCII character set). If empty, the seed value is determined by the date and time associated with the source product.")
    private String seedString;

    @Parameter(label = "Cloud reflectance threshold (442 nm)",
            description = "The minimum TOA reflectance (442 nm) of a cloud above land.",
            defaultValue = "0.25",
            interval = "[0.0, 0.5]")
    private double reflectanceThreshold442;

    @Parameter(label = "Cloud reflectance threshold (865 nm)",
            description = "The minimum TOA reflectance (865 nm) of a cloud above water.",
            defaultValue = "0.08",
            interval = "[0.0, 0.2]")
    private double reflectanceThreshold865;

    @Parameter(label = "Cloud-clear NN threshold over land",
            description = "The NN value threshold to separate clouds from cloud-free land surface.",
            defaultValue = "4.0",
            interval = "[3.0, 5.0]")
    private double cloudyClearThresholdLnd;

    @Parameter(label = "Cloud-clear NN threshold over water",
            description = "The NN value threshold to separate clouds from cloud-free water surface.",
            defaultValue = "4.0",
            interval = "[3.0, 5.0]")
    private double cloudyClearThresholdWtr;

    @Parameter(label = "Randomly mutant",
            description = "If checked, the cloud mask is mutated randomly.",
            defaultValue = "true")
    private boolean randomlyMutant;

    @Parameter(label = "Cloud-to-clear mutation probability over land",
            description = "The path to the cloud-to-clear mutation probability file (if empty, an internal configuration is used).")
    private File cloudToClearTableLnd;

    @Parameter(label = "Clear-to-cloud mutation probability over land",
            description = "The path to the clear-to-cloud mutation probability file (if empty, an internal configuration is used).")
    private File clearToCloudTableLnd;

    @Parameter(label = "Cloud-to-clear mutation probability over water",
            description = "The path to the cloud-to-clear mutation probability file (if empty, an internal configuration is used).")
    private File cloudToClearTableWtr;

    @Parameter(label = "Clear-to-cloud mutation probability over water",
            description = "The path to the clear-to-cloud mutation probability file (if empty, an internal configuration is used).")
    private File clearToCloudTableWtr;

    @Parameter(label = "Only land",
            description = "Only process land and leave water areas unchanged.",
            defaultValue = "false")
    private boolean onlyLand;

    @Parameter(label = "Clone all data",
            description = "If checked, all  data are copied from source to target, if not conflicting.",
            defaultValue = "true")
    private boolean cloneAll;

    @SourceProduct(label = "Source product", description = "The source product")
    private Product sourceProduct;

    private boolean[][] randomCloudToClearLnd;
    private boolean[][] randomClearToCloudLnd;
    private boolean[][] randomCloudToClearWtr;
    private boolean[][] randomClearToCloudWtr;

    @Override
    protected void configureTargetProduct(ProductConfigurer c) {
        c.getTargetProduct().setProductType(c.getSourceProduct().getProductType());
        c.copyMetadata();
        c.copyTimeCoding();
        c.copyTiePointGrids();
        final Band sourceBand = getSourceProduct().getBand(IdepixConstants.CLASSIF_BAND_NAME);
        final Band targetBand = c.addBand(sourceBand.getName(), sourceBand.getDataType());
        ProductUtils.copyRasterDataNodeProperties(sourceBand, targetBand);
        if (cloneAll) {
            c.copyBands(band -> !c.getTargetProduct().containsBand(band.getName()));
        }
        c.copyGeoCoding();
        c.copyMasks();
        c.getTargetProduct().setAutoGrouping(c.getSourceProduct().getAutoGrouping());
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        final Sample c = sourceSamples[0];  // classification
        final Sample v = sourceSamples[1];  // neural network
        final WritableSample t = targetSamples[0];

        // firstly, copy original flag bits
        t.set(c.getInt());

        if (c.getNode().isPixelValid(x, y) && !c.getBit(IdepixConstants.IDEPIX_INVALID)) {
            if (c.getBit(IdepixConstants.IDEPIX_LAND)) {
                final Sample r = sourceSamples[2];  // Oa03 (412 nm)
                mutate(x, y, c, v, r, t, cloudyClearThresholdLnd, reflectanceThreshold442, randomCloudToClearLnd,
                        randomClearToCloudLnd);
            } else if (!onlyLand) {
                final Sample r = sourceSamples[3];  // Oa17 (865 nm)
                mutate(x, y, c, v, r, t, cloudyClearThresholdWtr, reflectanceThreshold865, randomCloudToClearWtr,
                        randomClearToCloudWtr);
            }
        }
    }

    private void mutate(int x, int y, Sample c, Sample v, Sample r, WritableSample t, double cloudyClearThreshold,
                        double reflectanceThreshold, boolean[][] randomCloudToClear, boolean[][] randomClearToCloud) {
        if (v.getNode().isPixelValid(x, y) && r.getNode().isPixelValid(x, y)) {
            final boolean clear = !c.getBit(IdepixConstants.IDEPIX_CLOUD);
            final boolean cloud = c.getBit(IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS);  // really consider ambiguous cases
            final boolean large = v.getDouble() > cloudyClearThreshold;
            final boolean light = r.getDouble() > reflectanceThreshold;
            final boolean noIce = !c.getBit(IdepixConstants.IDEPIX_SNOW_ICE);
            final boolean vague = clear && light && noIce;  //  is clear but might be a cloud
            final boolean mutateCloudToClear = randomCloudToClear[y][x];
            final boolean mutateVagueToCloud = randomClearToCloud[y][x];
            final boolean mutate;

            if (cloud && !large) {  // original cloud
                mutate = mutateCloudToClear;
            } else if (vague && large) {  // original vague
                mutate = mutateVagueToCloud;
            } else if (cloud) {  // original cloud, but systematically changed to vague
                mutate = !mutateVagueToCloud;
            } else if (vague) {  // original vague, but systematically changed to cloud
                mutate = !mutateCloudToClear;
            } else {
                mutate = false;
            }
            if (mutate) {  // then flip the original (ambiguous) cloud mask
                t.set(IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, !cloud);
                t.set(IdepixConstants.IDEPIX_CLOUD, !cloud);
            }
            // always clear cloud shadow and cloud buffer
            t.set(IdepixConstants.IDEPIX_CLOUD_SHADOW, false);
            t.set(IdepixConstants.IDEPIX_CLOUD_BUFFER, false);
        }
    }

    @Override
    public void doExecute(ProgressMonitor pm) {
        try {
            initializeRandomNumbers();
        } catch (Exception e) {
            throw new OperatorException("Random noise could not be initialized.", e);
        }
    }

    private void initializeRandomNumbers() {
        final long[] seeds = {seedNumber, anotherSeedNumber(seedString, seedNumber)};
        final UniformVariate u = new UniformVariateFactory(rngType).newUniformVariate(seeds);
        try {
            randomCloudToClearLnd = randomNumbers(u, cloudyClearThresholdLnd, cloudToClearTableLnd,
                    "cloud_to_clear_lnd.dat");
            randomClearToCloudLnd = randomNumbers(u, cloudyClearThresholdLnd, clearToCloudTableLnd,
                    "clear_to_cloud_lnd.dat");
            randomCloudToClearWtr = randomNumbers(u, cloudyClearThresholdWtr, cloudToClearTableWtr,
                    "cloud_to_clear_wtr.dat");
            randomClearToCloudWtr = randomNumbers(u, cloudyClearThresholdWtr, clearToCloudTableWtr,
                    "clear_to_cloud_wtr.dat");
        } catch (Exception e) {
            throw new OperatorException("Random numbers could not be initialized.", e);
        }
    }

    private boolean[][] randomNumbers(UniformVariate u, double t, File table, String resource) throws FileNotFoundException {
        final int h = sourceProduct.getSceneRasterHeight();
        final int w = sourceProduct.getSceneRasterWidth();
        final boolean[][] random = new boolean[h][w];  // all false, i.e., no mutation by default

        if (randomlyMutant) {
            final Function f = InterpolationFunctionFactory.create("Step", table, resource);
            final RandomVariate variate = new BernoulliVariate(f.getY(t), u);

            for (int i = 0; i < h; i++) {
                for (int j = 0; j < w; j++) {
                    random[i][j] = variate.nextDouble() == 1.0;
                }
            }
        }

        return random;
    }

    private long anotherSeedNumber(String seedString, long seedNumber) {
        if (seedString != null) {
            for (byte b : seedString.getBytes(StandardCharsets.US_ASCII)) {
                seedNumber = 31 * seedNumber + Byte.toUnsignedLong(b);
            }
        }
        if (sourceProduct.getStartTime() != null) {
            seedNumber = 31 * seedNumber + Double.doubleToLongBits(sourceProduct.getStartTime().getMJD());
        }
        return seedNumber;
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer c) throws OperatorException {
        c.defineSample(0, IdepixConstants.CLASSIF_BAND_NAME);
        c.defineSample(1, IdepixConstants.NN_OUTPUT_BAND_NAME);
        c.defineSample(2, Rad2ReflConstants.OLCI_REFL_BAND_NAMES[2]);
        c.defineSample(3, Rad2ReflConstants.OLCI_REFL_BAND_NAMES[16]);
    }

    @Override
    protected void configureTargetSamples(TargetSampleConfigurer c) throws OperatorException {
        c.defineSample(0, IdepixConstants.CLASSIF_BAND_NAME);
    }

    @Override
    public void dispose() {
        super.dispose();

        randomClearToCloudLnd = null;
        randomCloudToClearLnd = null;
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(CloudMaskMutationOp.class);
        }
    }

}
