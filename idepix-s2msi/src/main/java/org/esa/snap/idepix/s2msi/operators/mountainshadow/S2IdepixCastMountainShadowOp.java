package org.esa.snap.idepix.s2msi.operators.mountainshadow;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.dataop.dem.ElevationModel;
import org.esa.snap.core.dataop.dem.ElevationModelDescriptor;
import org.esa.snap.core.dataop.dem.ElevationModelRegistry;
import org.esa.snap.core.dataop.resamp.Resampling;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Map;

/**
 * Computes terrain cast shadows from a DEM using directional terrain envelopes.
 *
 * <p>This internal operator materializes one scene-wide result on its first tile request. The
 * calculation uses the solar geometry and grid orientation at the scene centre. That is a
 * practical approximation for a Sentinel-2 tile, but it is not a per-pixel solar-geometry
 * model. A fixed 5 km DEM margin represents terrain outside the input scene.</p>
 */
@OperatorMetadata(alias = "Idepix.S2.CastMountainShadow",
        version = "1.0",
        internal = true,
        authors = "Brockmann Consult",
        copyright = "(c) 2026 by Brockmann Consult",
        description = "Computes terrain cast shadows for a Sentinel-2 product with elevation and solar-angle data.")
public class S2IdepixCastMountainShadowOp extends Operator {

    public static final String MOUNTAIN_SHADOW_FLAG_BAND_NAME = "mountain_shadow";
    private static final double DEM_MARGIN_KM = 5.0;

    @SourceProduct
    private Product sourceProduct;

    @Parameter(defaultValue = "Copernicus 30m Global DEM", label = "Digital Elevation Model")
    private String demName = "Copernicus 30m Global DEM";

    @TargetProduct
    private Product targetProduct;

    private Band elevationBand;
    private Band sunZenithBand;
    private Band sunAzimuthBand;
    private Band mountainShadowFlagBand;
    private volatile float[] shadowDepth;

    @Override
    public void initialize() throws OperatorException {
        sourceProduct = getSourceProduct();
        elevationBand = requireBand(S2IdepixConstants.ELEVATION_BAND_NAME);
        sunZenithBand = requireBand(S2IdepixConstants.SUN_ZENITH_BAND_NAME);
        sunAzimuthBand = requireBand(S2IdepixConstants.SUN_AZIMUTH_BAND_NAME);

        targetProduct = new Product(sourceProduct.getName() + "_cast_mountain_shadow",
                "cast-mountain-shadow", sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight());
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        mountainShadowFlagBand = targetProduct.addBand(MOUNTAIN_SHADOW_FLAG_BAND_NAME,
                ProductData.TYPE_INT8);
        mountainShadowFlagBand.setDescription("Terrain cast shadow from the input DEM");
        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {
        float[] depth = getShadowDepth();
        Tile targetTile = targetTiles.get(mountainShadowFlagBand);
        int width = targetProduct.getSceneRasterWidth();
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            if (pm.isCanceled()) {
                return;
            }
            int row = y * width;
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                targetTile.setSample(x, y, depth[row + x] > 0.0f);
            }
        }
    }

    private float[] getShadowDepth() {
        float[] result = shadowDepth;
        if (result == null) {
            synchronized (this) {
                result = shadowDepth;
                if (result == null) {
                    shadowDepth = result = computeShadowDepth();
                }
            }
        }
        return result;
    }

    private float[] computeShadowDepth() {
        int width = sourceProduct.getSceneRasterWidth();
        int height = sourceProduct.getSceneRasterHeight();
        try {
            float sunZenith = readCentreSample(sunZenithBand);
            float sunAzimuth = readCentreSample(sunAzimuthBand);
            if (!Float.isFinite(sunZenith) || !Float.isFinite(sunAzimuth) || sunZenith < 0 || sunZenith >= 90) {
                throw new OperatorException("Finite solar zenith in [0, 90) and solar azimuth are required for cast mountain shadow");
            }
            double orientationDegrees = Math.toDegrees(computeCentreGridOrientation());
            double gridSunAzimuth = sunAzimuth - orientationDegrees;
            double spatialResolution = S2IdepixUtils.determineResolution(sourceProduct);
            float noData = elevationBand.isNoDataValueUsed() ? (float) elevationBand.getNoDataValue() : Float.NaN;
            int marginPixels = marginPixels(spatialResolution);
            float[] sourceElevation = elevationBand.readPixels(0, 0, width, height, (float[]) null);
            ExpandedDem expandedDem = createExpandedDem(sourceElevation, width, height, marginPixels, noData);
            float[] expandedDepth = computeDepth(expandedDem, spatialResolution, gridSunAzimuth, 90.0 - sunZenith, noData);
            return crop(expandedDepth, expandedDem.width, width, height, marginPixels);
        } catch (IOException e) {
            throw new OperatorException("Could not read source data for cast mountain shadow", e);
        }
    }

    private float[] computeDepth(ExpandedDem dem, double spatialResolution, double gridSunAzimuth,
                                 double sunElevation, float noData) {
        return CastMountainShadow.compute(dem.elevation, dem.width, dem.height, spatialResolution, spatialResolution,
                gridSunAzimuth, sunElevation, noData, 1.0e-4, Math.max(1, dem.width));
    }

    private int marginPixels(double spatialResolution) {
        return (int) Math.ceil(DEM_MARGIN_KM * 1000.0 / spatialResolution);
    }

    private ExpandedDem createExpandedDem(float[] sourceElevation, int width, int height, int marginPixels, float sourceNoData) {
        int expandedWidth;
        int expandedHeight;
        try {
            expandedWidth = Math.addExact(width, Math.multiplyExact(2, marginPixels));
            expandedHeight = Math.addExact(height, Math.multiplyExact(2, marginPixels));
        } catch (ArithmeticException e) {
            throw new OperatorException("Cast mountain-shadow DEM margin is too large", e);
        }
        float[] elevation;
        try {
            elevation = new float[Math.multiplyExact(expandedWidth, expandedHeight)];
        } catch (OutOfMemoryError | ArithmeticException e) {
            throw new OperatorException("Not enough memory for cast mountain-shadow DEM margin", e);
        }
        java.util.Arrays.fill(elevation, Float.NaN);

        for (int y = 0; y < height; y++) {
            int sourceRow = y * width;
            int targetRow = (y + marginPixels) * expandedWidth + marginPixels;
            for (int x = 0; x < width; x++) {
                float sample = sourceElevation[sourceRow + x];
                elevation[targetRow + x] = isSourceNoData(sample, sourceNoData) ? Float.NaN : sample;
            }
        }
        fillMarginFromDem(elevation, expandedWidth, expandedHeight, marginPixels);
        return new ExpandedDem(elevation, expandedWidth, expandedHeight);
    }

    private void fillMarginFromDem(float[] elevation, int width, int height, int marginPixels) {
        ElevationModelDescriptor descriptor = ElevationModelRegistry.getInstance().getDescriptor(demName);
        if (descriptor == null) {
            throw new OperatorException("Unknown elevation model '" + demName + "' for cast mountain-shadow DEM margin");
        }
        ElevationModel dem = descriptor.createDem(Resampling.BILINEAR_INTERPOLATION);
        if (dem == null) {
            throw new OperatorException("Could not create elevation model '" + demName + "' for cast mountain-shadow DEM margin");
        }
        try {
            PixelPos pixelPos = new PixelPos();
            GeoPos geoPos = new GeoPos();
            float demNoData = descriptor.getNoDataValue();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (x >= marginPixels && x < width - marginPixels && y >= marginPixels && y < height - marginPixels) {
                        continue;
                    }
                    pixelPos.setLocation(x - marginPixels, y - marginPixels);
                    geoPos = sourceProduct.getSceneGeoCoding().getGeoPos(pixelPos, geoPos);
                    if (geoPos == null || !geoPos.isValid()) {
                        continue;
                    }
                    double sample = dem.getElevation(geoPos);
                    if (Double.isFinite(sample) && sample != demNoData) {
                        elevation[y * width + x] = (float) sample;
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Could not read terrain outside the input scene for cast mountain shadow", e);
        } finally {
            dem.dispose();
        }
    }

    static float[] crop(float[] source, int sourceWidth, int targetWidth, int targetHeight, int marginPixels) {
        float[] cropped = new float[Math.multiplyExact(targetWidth, targetHeight)];
        for (int y = 0; y < targetHeight; y++) {
            System.arraycopy(source, (y + marginPixels) * sourceWidth + marginPixels,
                    cropped, y * targetWidth, targetWidth);
        }
        return cropped;
    }

    private static boolean isSourceNoData(float sample, float noData) {
        return Float.isNaN(sample) || (!Float.isNaN(noData) && sample == noData);
    }

    private static final class ExpandedDem {
        private final float[] elevation;
        private final int width;
        private final int height;

        private ExpandedDem(float[] elevation, int width, int height) {
            this.elevation = elevation;
            this.width = width;
            this.height = height;
        }
    }


    private float readCentreSample(Band band) throws IOException {
        int x = sourceProduct.getSceneRasterWidth() / 2;
        int y = sourceProduct.getSceneRasterHeight() / 2;
        return band.readPixels(x, y, 1, 1, (float[]) null)[0];
    }

    private double computeCentreGridOrientation() {
        if (sourceProduct.getSceneGeoCoding() == null) {
            throw new OperatorException("A geo-coding is required for cast mountain shadow");
        }
        float x = sourceProduct.getSceneRasterWidth() / 2.0f;
        float y = sourceProduct.getSceneRasterHeight() / 2.0f;
        GeoPos left = sourceProduct.getSceneGeoCoding().getGeoPos(new PixelPos(x - 1, y), null);
        GeoPos right = sourceProduct.getSceneGeoCoding().getGeoPos(new PixelPos(x + 1, y), null);
        if (!left.isValid() || !right.isValid()) {
            throw new OperatorException("Could not determine grid orientation from the source geo-coding");
        }
        return Math.atan2(-(right.lat - left.lat), (right.lon - left.lon) * Math.cos(Math.toRadians(left.lat)));
    }

    private Band requireBand(String name) {
        Band band = sourceProduct.getBand(name);
        if (band == null) {
            throw new OperatorException("Band '" + name + "' is required for cast mountain shadow");
        }
        return band;
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(S2IdepixCastMountainShadowOp.class);
        }
    }
}
