package org.esa.snap.idepix.olci;

import org.esa.snap.idepix.olci.IdepixOlciSlopeAspectOrientationOp;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Tonio Fincke
 */
public class IdepixOlciSlopeAspectOrientationOpTest {

//    @Test
//    public void computeSlopeAndAspect() {
//        // flat plane over 3x3 box
//        float[] altitude = new float[]{
//                0.0f, 0.0f, 0.0f,
//                0.0f, 0.0f, 0.0f,
//                0.0f, 0.0f, 0.0f};
//        float[] slopeAndAspect = IdepixOlciSlopeAspectOrientationOp.computeSlopeAspect(altitude, 300);
//        assertEquals(slopeAndAspect[0], 0.0f, 1e-7);
//        assertEquals(slopeAndAspect[1], Float.NaN, 1e-8);
//
//        // ratio 1:1 (45deg) inclined plane over 3x3 box, ascending to the north
//        altitude = new float[]{
//                600.0f, 600.0f, 600.0f,
//                300.0f, 300.0f, 300.0f,
//                0.0f, 0.0f, 0.0f};
//        slopeAndAspect = IdepixOlciSlopeAspectOrientationOp.computeSlopeAspect(altitude, 300);
//        assertEquals(slopeAndAspect[0], Math.PI / 4, 1e-7);
//        assertEquals(slopeAndAspect[1], Math.PI, 1e-6);
//
//        // ratio 1:1 (45deg) inclined plane over 3x3 box, ascending to the west
//        altitude = new float[]{
//                600.0f, 300.0f, 0.0f,
//                600.0f, 300.0f, 0.0f,
//                600.0f, 300.0f, 0.0f};
//        slopeAndAspect = IdepixOlciSlopeAspectOrientationOp.computeSlopeAspect(altitude, 300);
//        assertEquals(slopeAndAspect[0], Math.PI / 4, 1e-7);
//        assertEquals(slopeAndAspect[1], Math.PI / 2, 1e-6);
//
//        // ratio 1:2 inclined plane over 3x3 box, ascending to the east
//        altitude = new float[]{
//                0.0f, 300.0f, 600.0f,
//                0.0f, 300.0f, 600.0f,
//                0.0f, 300.0f, 600.0f};
//        slopeAndAspect = IdepixOlciSlopeAspectOrientationOp.computeSlopeAspect(altitude, 600);
//        assertEquals(slopeAndAspect[0], Math.atan(0.5), 1e-7);
//        assertEquals(slopeAndAspect[1], Math.PI * 3.0 / 2.0, 1e-6);
//    }

    @Test
    @Ignore
    public void testComputeOrientation() {

        // todo: does not work after DM's latest changes, check why
        // east-west 3x3 box, orientaton is 0 deg
        float[] latitudes = new float[]{50.0f, 50.0f, 50.0f,
                50.1f, 50.1f, 50.1f,
                50.2f, 50.2f, 50.2f};
        float[] longitudes = new float[]{10.0f, 10.1f, 10.2f,
                10.0f, 10.1f, 10.2f,
                10.0f, 10.1f, 10.2f};
        float orientation = IdepixOlciSlopeAspectOrientationOp.computeOrientation(latitudes, longitudes);
        assertEquals(0.0f, orientation, 1e-8);

        // 3x3 box tilted 90deg clockwise --> orientation is -90 deg
        latitudes = new float[]{50.0f, 50.1f, 50.2f,
                50.0f, 50.1f, 50.2f,
                50.0f, 50.1f, 50.2f};
        longitudes = new float[]{10.2f, 10.2f, 10.2f,
                10.1f, 10.1f, 10.1f,
                10.0f, 10.0f, 10.0f};
        orientation = IdepixOlciSlopeAspectOrientationOp.computeOrientation(latitudes, longitudes);
        assertEquals(-Math.PI / 2.0f, orientation, 1e-6);

        // 3x3 box tilted 45deg counterclockwise --> orientation is 45 deg
        latitudes = new float[]{-0.1f, -0.05f, 0.0f,
                -0.05f, 0.0f, 0.05f,
                0.0f, 0.05f, 0.1f};
        longitudes = new float[]{10.0f, 10.05f, 10.1f,
                10.05f, 10.1f, 10.15f,
                10.1f, 10.15f, 10.2f};
        orientation = IdepixOlciSlopeAspectOrientationOp.computeOrientation(latitudes, longitudes);
        assertEquals(-Math.PI / 4.0f, orientation, 1e-3);
    }
}