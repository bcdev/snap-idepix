package org.esa.snap.idepix.avhrr;

import org.esa.snap.core.gpf.OperatorException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

/**
 * AVHRR auxiliary data utility class
 *
 * @author olafd
 */
class AvhrrAuxdata {

    private static final int VZA_TABLE_LENGTH = 2048;
    private static final String VZA_FILE_NAME = "view_zenith.txt";

    private static final int RAD2BT_TABLE_LENGTH = 3;
    private static final String RAD2BT_FILE_NAME_PREFIX = "rad2bt_noaa";

    private static AvhrrAuxdata instance;

    static AvhrrAuxdata getInstance() {
        if (instance == null) {
            instance = new AvhrrAuxdata();
        }

        return instance;
    }


    Line2ViewZenithTable createLine2ViewZenithTable() throws IOException {
        final InputStream inputStream = getClass().getResourceAsStream(VZA_FILE_NAME);
        Line2ViewZenithTable vzaTable = new Line2ViewZenithTable();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        try {
            int i = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null && i < VZA_TABLE_LENGTH) {
                line = line.trim();
                st = new StringTokenizer(line, "\t", false);

                if (st.hasMoreTokens()) {
                    // x (whatever that is)
                    vzaTable.setxIndex(i, Integer.parseInt(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // y
                    vzaTable.setVza(i, Double.parseDouble(st.nextToken()));
                }
                i++;
            }
        } catch (IOException | NumberFormatException e) {
            throw new OperatorException("Failed to load Line2ViewZenithTable: \n" + e.getMessage(), e);
        } finally {
            inputStream.close();
        }
        return vzaTable;
    }

    Rad2BTTable createRad2BTTable(String noaaId) throws IOException {

        final String filename = RAD2BT_FILE_NAME_PREFIX + noaaId + ".txt";
        final InputStream inputStream = getClass().getResourceAsStream(filename);
        Rad2BTTable rad2BTTable = new Rad2BTTable();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        try {
            int i = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null && i < RAD2BT_TABLE_LENGTH) {
                line = line.trim();
                st = new StringTokenizer(line, "\t", false);

                if (st.hasMoreTokens()) {
                    // channel index (3, 4, 5), skip
                    st.nextToken();
                }
                if (st.hasMoreTokens()) {
                    // A
                    rad2BTTable.setA(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // B
                    rad2BTTable.setB(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // D
                    rad2BTTable.setD(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // nu_low
                    rad2BTTable.setNuLow(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // nu_mid
                    rad2BTTable.setNuMid(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // nu_high_land
                    rad2BTTable.setNuHighland(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // nu_high_water
                    rad2BTTable.setNuHighWater(i, Double.parseDouble(st.nextToken()));
                }
                i++;
            }
        } catch (IOException | NumberFormatException e) {
            throw new OperatorException("Failed to load Rad2BTTable: \n" + e.getMessage(), e);
        } finally {
            inputStream.close();
        }
        return rad2BTTable;
    }


    /**
     * Class providing a temperature-radiance conversion data table
     */
    class Line2ViewZenithTable {
        private int[] xIndex = new int[VZA_TABLE_LENGTH];
        private double[] vza = new double[VZA_TABLE_LENGTH];

        void setxIndex(int index, int xIndex) {
            this.xIndex[index] = xIndex;
        }

        double getVza(int index) {
            return vza[index];
        }

        void setVza(int index, double vza) {
            this.vza[index] = vza;
        }

    }

    /**
     *  Class providing a radiance-to-BT coefficients table
     */
    class Rad2BTTable {
        private final int OFFSET = 3;

        private double[] A = new double[RAD2BT_TABLE_LENGTH];
        private double[] B = new double[RAD2BT_TABLE_LENGTH];
        private double[] D = new double[RAD2BT_TABLE_LENGTH];
        private double[] nuLow = new double[RAD2BT_TABLE_LENGTH];
        private double[] nuMid = new double[RAD2BT_TABLE_LENGTH];
        private double[] nuHighland = new double[RAD2BT_TABLE_LENGTH];
        private double[] nuHighWater = new double[RAD2BT_TABLE_LENGTH];

        double getA(int index) {
            return A[index - OFFSET];
        }

        void setA(int index, double a) {
            this.A[index] = a;
        }

        double getB(int index) {
            return B[index - OFFSET];
        }

        void setB(int index, double b) {
            this.B[index] = b;
        }

        double getD(int index) {
            return D[index - OFFSET];
        }

        void setD(int index, double d) {
            this.D[index] = d;
        }

        double getNuLow(int index) {
            return nuLow[index - OFFSET];
        }

        void setNuLow(int index, double nuLow) {
            this.nuLow[index] = nuLow;
        }

        double getNuMid(int index) {
            return nuMid[index - OFFSET];
        }

        void setNuMid(int index, double nuMid) {
            this.nuMid[index] = nuMid;
        }

        double getNuHighLand(int index) {
            return nuHighland[index - OFFSET];
        }

        void setNuHighland(int index, double nuHighland) {
            this.nuHighland[index] = nuHighland;
        }

        double getNuHighWater(int index) {
            return nuHighWater[index - OFFSET];
        }

        void setNuHighWater(int index, double nuHighWater) {
            this.nuHighWater[index] = nuHighWater;
        }
    }
}
