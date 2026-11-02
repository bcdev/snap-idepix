package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.core.gpf.Tile;

import java.awt.*;

/**
 * todo: add comment
 * todo: we should try to get rid of this
 *
 */
class FlagDetector {

    private int[] classifData;
    private int roiWidth;
    private final int invalid_byte;
    private final int land_byte;
    private final boolean includeCloudBuffer;

    FlagDetector(Tile classifSourceTile, Rectangle roi) {
        this(classifSourceTile, roi, false);
    }

    FlagDetector(Tile classifSourceTile, Rectangle roi, boolean includeCloudBuffer) {
        invalid_byte = (int) Math.pow(2, S2IdepixConstants.IDEPIX_INVALID);
        land_byte = (int) Math.pow(2, S2IdepixConstants.IDEPIX_LAND);
        this.includeCloudBuffer = includeCloudBuffer;
        classifData = classifSourceTile.getSamplesInt();
        roiWidth = roi.width;
    }

    boolean isLand(int x, int y) {
        final int sample = classifData[y * roiWidth + x];
        return (sample & land_byte) != 0;
    }

    boolean isCloud(int x, int y) {
        final int classifSample = classifData[y * roiWidth + x];
        return isCloudSample(classifSample, includeCloudBuffer);
    }

    static boolean isCloudSample(int classifSample, boolean includeCloudBuffer) {
        final int cloudMask = 1 << S2IdepixConstants.IDEPIX_CLOUD |
                1 << S2IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS |
                1 << S2IdepixConstants.IDEPIX_CLOUD_SURE;
        final int optionalBufferMask = includeCloudBuffer ? 1 << S2IdepixConstants.IDEPIX_CLOUD_BUFFER : 0;
        return (classifSample & (cloudMask | optionalBufferMask)) != 0;
    }

    boolean isInvalid(int x, int y) {
        final int sample = classifData[y * roiWidth + x];
        return (sample & invalid_byte) != 0;
    }
}

