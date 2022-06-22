import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import sys
import os
import time
from scipy import interpolate
from scipy.signal import fftconvolve

sys.path.append("C:\\Users\Dagmar\.snap\snap-python")
import snappy as snp
from snappy import Product
from snappy import ProductData
from snappy import ProductIO
from snappy import jpy
from snappy import PixelPos #org.esa.snap.core.datamodel.PixelPos

GeoPos = jpy.get_type('org.esa.snap.core.datamodel.GeoPos')
PixelPos = jpy.get_type('org.esa.snap.core.datamodel.PixelPos')
GPF = jpy.get_type('org.esa.snap.core.gpf.GPF')
File = jpy.get_type('java.io.File')
ProgressMonitor = jpy.get_type('com.bc.ceres.core.ProgressMonitor')

def getRefinedHeightFromCtp(ctp, slp, temperatures): #ctp: cloud top pressure, slp: sea level pressure, temperature-profile
    height = 0.0
    prsLevels = np.array((1000., 950., 925., 900., 850., 800., 700., 600., 500., 400., 300., 250., 200., 150., 100.,
                          70., 50., 30., 20., 10., 7., 5., 3., 2., 1.))

    if ctp >= prsLevels[-1]:
        for i in range(len(prsLevels)- 1):
            if ctp > prsLevels[0] or (ctp < prsLevels[i] and ctp > prsLevels[i + 1]):
                t1 = temperatures[i]
                t2 = temperatures[i + 1]
                ts = (t2 - t1) / (prsLevels[i + 1] - prsLevels[i]) * (ctp - prsLevels[i]) + t1
                height = getHeightFromCtp(ctp, slp, ts)
                break

    else:
        # CTP < 1 hPa? This should never happen...
        t1 = temperatures[-2]
        t2 = temperatures[-1]
        ts = (t2 - t1) / (prsLevels[-2] - prsLevels[-1]) * (ctp - prsLevels[-1]) + t1
        height = getHeightFromCtp(ctp, slp, ts)

    return height

def getRefinedHeightFromBT(BT, slp, temperatures): #BT: brightness temperature, slp: sea level pressure, temperature-profile
    #this function is not necessarily eindeutig! inversion in T(p) can lead to two (or more) solutions for p.
    height1, height2, height1b, height2b = None, None, None, None
    prsLevels = np.array((1000., 950., 925., 900., 850., 800., 700., 600., 500., 400., 300., 250., 200., 150., 100.,
                          70., 50., 30., 20., 10., 7., 5., 3., 2., 1.))

    if BT >= np.min(temperatures) and BT <= np.max(temperatures):
        for i in range(len(temperatures)- 1):
            if BT > temperatures[i] and BT < temperatures[i + 1]:
                ctp1 = prsLevels[i]
                ctp2 = prsLevels[i + 1]
                ctp = (ctp2 - ctp1) / (temperatures[i + 1] - temperatures[i]) * (BT - temperatures[i]) + ctp1
                height1 = getHeightFromCtp(ctp, slp, BT)
                height1b = computeHeightFromPressure(ctp)
            if BT < temperatures[i] and BT > temperatures[i + 1]: #inversion!
                ctp1 = prsLevels[i]
                ctp2 = prsLevels[i + 1]
                ctp = (ctp2 - ctp1) / (temperatures[i + 1] - temperatures[i]) * (BT - temperatures[i]) + ctp1
                height2 = getHeightFromCtp(ctp, slp, BT)
                height2b = computeHeightFromPressure(ctp)

    return height1, height2, height1b, height2b

def getHeightFromCtp(ctp, p0, ts):
    return -ts * (np.power(ctp / p0, 1. / 5.255) - 1) / 0.0065

def computeHeightFromPressure(pressure):
    return -8000 * np.log(pressure / 1013.0)

def investigate_AATSR4th_transect(plot_BT=False, plot_Temp=False):
    path ="E:\Documents\projects\QA4EO\data\\transect\\"
    # fname = "subset_0_of_ENV_AT_1_RBT____20021129T235200_20021130T013735_20210315T024827_6334_011_359______DSI_R_NT_004_TRANSECT.txt"
    # todo: unfortunately, no radiance or BT is extracted!
    fname = "subset_WATER_of_ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004_TRANSECT.txt"
    # fname = "subset_LAND_of_ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004_TRANSECT.txt"
    ##data from the snap-transect plot! Combine both for analysis.
    fname_BT = "subset_WATER_S8_S9_of_ENV_AT_1_RBT____20020810T083508_TRANSECT.txt"

    d = pd.read_csv(path + fname, header=0, comment='#', sep='\t')
    d_BT = pd.read_csv(path + fname_BT, header=0, comment='#', sep='\t')
    print(d.shape)
    print(d_BT.shape)
    print(d.columns.values)
    referencePressureLevels = np.array((1000., 950., 925., 900., 850., 800., 700., 600., 500., 400., 300., 250., 200.,
                                        150., 100., 70., 50., 30., 20., 10., 7., 5., 3., 2., 1.))

    col_BT = [a for a in d_BT.columns.values if 'BT' in a]
    col_BT = [a for a in col_BT if not 'sigma' in a]
    col_T = [a for a in d.columns.values if 'temperature_profile_tx_pressure' in a]
    col_slp = [a for a in d.columns.values if 'surface_pressure' in a]
    print(col_slp)
    if plot_BT:
        for v in col_BT:
            plt.plot(d_BT[v].values, '-', label=v)
        plt.legend()
        plt.show()

    if plot_Temp:
        ID = np.array(d_BT[col_BT[0]].values < 270) #todo: cloud flags?
        print(np.sum(ID))
        d_BT = d_BT.loc[ID,:]
        d_BT = d_BT.reset_index(drop=True)
        d = d.loc[ID,:]
        d = d.reset_index(drop=True)
        number = np.random.random(10)*d.shape[0]
        for i in number:
            fig, ax = plt.subplots(1, 1, figsize=(5,5))
            ax.plot(d[col_T].loc[int(i),:].values, referencePressureLevels, '-')
            for v in col_BT:
                BT = d_BT[v].values[int(i)]
                ax.plot((BT, BT), (np.min(referencePressureLevels), np.max(referencePressureLevels)), 'r-')
                height1, height2, h1, h2 = getRefinedHeightFromBT(BT, d[col_slp].values[int(i)], d[col_T].loc[int(i),:].values)
                print(i, BT, height1, height2, h1, h2)
            ax.invert_yaxis()
            ax.set_xlabel('Temperature T [K]')
            ax.set_ylabel('pressure [hPa]')
            fig.tight_layout()
            plt.show()

def get_band_or_tiePointGrid(product, name, dtype='float32', reshape=True, subset=None):
    ##
    # This function reads a band or tie-points, identified by its name <name>, from SNAP product <product>
    # The fuction returns a numpy array of shape (height, width)
    ##
    if subset is None:
        height = product.getSceneRasterHeight()
        width = product.getSceneRasterWidth()
        sline, eline, scol, ecol = 0, height-1, 0, width -1
    else:
        sline,eline,scol,ecol = subset
        height = eline - sline + 1
        width = ecol - scol + 1

    var = np.zeros(width * height, dtype=dtype)
    if name in list(product.getBandNames()):
        product.getBand(name).readPixels(scol, sline, width, height, var)
    elif name in list(product.getTiePointGridNames()):
        var.shape = (height, width)
        for i,iglob in enumerate(range(sline,eline+1)):
            for j,jglob in enumerate(range(scol,ecol+1)):
                var[i, j] = product.getTiePointGrid(name).getPixelDouble(jglob, iglob)
        var.shape = (height*width)
    else:
        raise Exception('{}: neither a band nor a tie point grid'.format(name))

    if reshape:
        var.shape = (height, width)

    return var

def set_cloud_mask_from_Bayesian(cloudB):
    single_moderate_mask = np.bitwise_and(cloudB, 2 ** 1) == 2 ** 1
    no_bayes_available_mask = np.bitwise_and(cloudB, 2 ** 7) == 2 ** 7
    out = np.zeros(cloudB.shape) + np.nan
    if np.sum(no_bayes_available_mask)==0:
        out = single_moderate_mask
    return out


def set_cloud_mask_from_Confidence(cloudB):
    summary_cloud_mask = np.bitwise_and(cloudB, 2 ** 14) == 2 ** 14
    return summary_cloud_mask


def set_cloud_mask_from_cloudBand(cloudB):
    visible_mask = np.bitwise_and(cloudB, 2 ** 0) == 2 ** 0
    gross_cloud_mask = np.bitwise_and(cloudB, 2 ** 7) == 2 ** 7
    thin_cirrus_mask = np.bitwise_and(cloudB, 2 ** 8) == 2 ** 8
    medium_high_mask = np.bitwise_and(cloudB, 2 ** 9) == 2 ** 9
    return np.array(visible_mask + gross_cloud_mask + thin_cirrus_mask + medium_high_mask > 0)

def set_land_mask(product, subset=None):
    confid_in = get_band_or_tiePointGrid(product, 'confidence_in', 'int32', subset=subset)
    #LAND : coastline, tidal, land, inland_water
    coastline_mask = np.bitwise_and(confid_in, 2 ** 0) == 2 ** 0
    tidal_mask = np.bitwise_and(confid_in, 2 ** 2) == 2 ** 2
    land_mask = np.bitwise_and(confid_in, 2 ** 3) == 2 ** 3
    inland_water_mask = np.bitwise_and(confid_in, 2 ** 4) == 2 ** 4
    return np.array(coastline_mask + tidal_mask + land_mask + inland_water_mask > 0)

def calculate_view_azimuth_interpolation_singleline( vaa_line, width, plot=False):

    nx0 = 0
    nx1 = 200
    nx2 = 340
    nx3 = width
    nx_change = 272 # nadir line between 271 + 272

    vaa_out = np.copy(vaa_line)

    ##
    # interpolation for row ny:
    y1 = vaa_line[nx0:nx1]
    y2 = vaa_line[nx2:]
    x1 = np.arange(nx0, nx1, 1.)
    x2 = np.arange(nx2, nx3, 1.)

    p = np.polyfit(x1, y1, 2)
    print(p)
    delta = vaa_line[nx1] - (p[0] * (nx1) ** 2 + p[1] * nx1 + p[2])
    print(vaa_line[nx1])
    print(p[0] * (nx1) ** 2 + p[1] * nx1 + p[2])
    x_out = np.arange(nx1, nx_change, 1.)
    y_out1 = p[0] * (x_out) ** 2 + p[1] * x_out + p[2] + delta
    vaa_out[nx1:nx_change] = y_out1

    p = np.polyfit(x2, y2, 2)
    delta = vaa_line[nx2] - (p[0] * (nx2) ** 2 + p[1] * nx2 + p[2])
    x_out = np.arange(nx_change, nx2, 1.)
    y_out2 = p[0] * (x_out) ** 2 + p[1] * x_out + p[2] + delta

    vaa_out[nx_change:nx2] = y_out2

    print(vaa_out[(nx_change-1):(nx_change+1)])
    if plot:
        plt.plot(vaa_line, '-', label='from TPG')
        plt.plot(vaa_out, 'r-', label='Interpolation')
        plt.xlabel('Pixel X')
        plt.ylabel('View Azimuth Angle')
        plt.legend()
        plt.show()

    return vaa_out


def calculate_view_zenith_interpolation_singleline_OLCI(vza, ny):

    nx0 = 3500
    nx1 = 3580
    nx2 = 3649
    nx3 = nx2 + 80
    nx_change = 3616

    vza_out = np.copy(vza)

    ##
    # interpolation for row ny:

    y1 = vza[ny, nx0:nx1]
    y2 = vza[ny, nx2:nx3]
    x1 = np.arange(nx0, nx1, 1.)
    x2 = np.arange(nx2, nx3, 1.)


    p = np.polyfit(x1, y1, 2)
    x_out = np.arange(nx1, nx_change, 1.)
    y_out1 = p[0] * (x_out) ** 2 + p[1] * x_out + p[2]
    vza_out[ny, nx1:nx_change] = y_out1

    p = np.polyfit(x2, y2, 2)
    x_out = np.arange(nx_change, nx2, 1.)
    y_out2 = p[0] * (x_out) ** 2 + p[1] * x_out + p[2]

    vza_out[ny, nx_change:nx2] = y_out2

    return vza_out

def setRelativePathIndex_and_TheoreticalHeight(sza, saa, oza, x_tx, orientation, spatialResolution, maxObjectAltitude, minSurfaceAltitude): #oaa replaced by x_tx
    shadow_angle_PixelCoord  = (saa - orientation) + 180.
    cosSaa = np.cos(shadow_angle_PixelCoord*np.pi/180. - np.pi / 2.)
    sinSaa = np.sin(shadow_angle_PixelCoord*np.pi/180. - np.pi / 2.)

    #modified sza for influence of oza:
    if saa - orientation < 180.:
        if x_tx < 0:
            sza = np.arctan(np.tan(sza * np.pi / 180.) - np.tan(oza * np.pi / 180.)) * 180. / np.pi #negative??
        else:
            sza = np.arctan(np.tan(sza * np.pi / 180.) + np.tan(oza * np.pi / 180.)) * 180. / np.pi
    else:
        if x_tx < 0:
            sza = np.arctan(np.tan(sza * np.pi / 180.) + np.tan(oza * np.pi / 180.)) * 180. / np.pi #negative
        else:
            sza = np.arctan(np.tan(sza * np.pi / 180.) - np.tan(oza * np.pi / 180.)) * 180. / np.pi #positive


    deltaProjX = ((maxObjectAltitude - minSurfaceAltitude) * np.tan(sza*np.pi/180.) * cosSaa) / spatialResolution
    deltaProjY = ((maxObjectAltitude - minSurfaceAltitude) * np.tan(sza*np.pi/180.) * sinSaa) / spatialResolution

    x0 = 0
    y0 = 0

    x1 = x0 + deltaProjX + np.sign(deltaProjX) * 1.5
    y1 = y0 + deltaProjY + np.sign(deltaProjY) * 1.5

    #create index steps
    # Path touches which pixels?
    #setup all pixel centers from x0/y0 to x1/y1.
    # calculate distance between pixel center and line (X0, X1)
    # all distances below/equal sqrt(2*0.5^2): the pixel is touched by the line and a potential shadow pixel.

    if x0 <x1:
        xCenters = np.arange(x0 + 0.5, np.round(x1, decimals=0)+0.5, 1.)
    else:
        xCenters = np.arange(np.round(x1, decimals=0) + 0.5, x0 + 0.5, 1.)
    if y0 < y1:
        yCenters = np.arange(y0 + 0.5, np.round(y1, decimals=0) + 0.5, 1.)
    else:
        yCenters = np.arange(np.round(y1, decimals=0) + 0.5, y0 + 0.5, 1.)


    distance = np.zeros((len(xCenters), len(yCenters)))
    xValue = np.zeros(distance.shape)
    yValue = np.zeros(distance.shape)
    xIndex = np.zeros(distance.shape)
    yIndex = np.zeros(distance.shape)

    NxCenter = len(xCenters)
    NyCenter = len(yCenters)

    for i in range(NxCenter):
        for j in range(NyCenter):
            r = -((x0 - xCenters[i])*(x0 - x1) + (y0 - yCenters[j])*(y0 - y1)) / ((x0-x1)**2+ (y0-y1)**2)
            d = np.sqrt( np.power( (x0 - xCenters[i]) + r*(x0-x1),2) +
                         np.power( (y0- yCenters[j]) + r*(y0-y1),2))

            distance[i, j] = d
            xValue[i, j] = xCenters[i]
            yValue[i, j] = yCenters[j]
            xIndex[i, j] = i
            yIndex[i, j] = j

    if deltaProjX < 0:
        xIndex = xIndex - (NxCenter-1)
    if deltaProjY < 0:
        yIndex = yIndex - (NyCenter-1)

    ID = np.array(distance <= 0.5*np.sqrt(2.))

    # plt.plot(xValue, yValue, '+')
    # plt.plot((x0, x1), (y0, y1), '-')
    # plt.plot(xValue[ID], yValue[ID], 'ro')
    # plt.show()

    stepIndex = np.zeros((np.sum(ID), 2), dtype='int16')
    stepIndex[:, 0] = xIndex[ID]
    stepIndex[:, 1] = yIndex[ID]

    # theoretHeight = np.zeros(len(stepIndex.shape[0]))
    # theoretHeight = np.sqrt( (stepIndex[:,0]-0.5)**2 + (stepIndex[:,1]-0.5)**2 )*spatialResolution / np.tan(sza*np.pi/180.)
    # height from cloud pixel to furtherst point!
    theoretHeight = maxObjectAltitude - np.sqrt((stepIndex[:, 0]) ** 2 + (stepIndex[:, 1]) ** 2) * spatialResolution / \
                    np.tan(sza * np.pi / 180.)

    threshHeigh = 1000./np.tan(sza * np.pi / 180.)

    return stepIndex, theoretHeight, threshHeigh

def calculate_orientation(lat1, lon1, lat2, lon2):
    return np.arctan2(-(lat2 - lat1), (lon2 - lon1) * np.cos(lat1*np.pi/180.))

def setup_round_kernel(radius, spacing, normalise=True, type='', radius2=0.):
    nhkern = np.ceil(radius / np.array(spacing)).astype('int')
    ydist = np.arange(-nhkern[0], nhkern[0] + 1) * spacing[0]
    xdist = np.arange(-nhkern[1], nhkern[1] + 1) * spacing[1]
    kernel = np.sqrt(ydist[:, np.newaxis] ** 2. + \
                     xdist[np.newaxis, :] ** 2.) <= radius

    print("kernel shape = ", kernel.shape)
    test = np.zeros(kernel.shape)
    test[kernel] = 1.
    kernel = np.copy(test)

    if type == 'ring':
        #print(kernel.shape)
        mid = np.floor_divide(kernel.shape, 2)
        #print(mid)

        for i in range(kernel.shape[0]):
            for j in range(kernel.shape[1]):
                if np.sqrt(np.power((i - mid[0]) * spacing[0], 2.) + np.power((j - mid[1]) * spacing[1], 2.)) < radius2:
                    kernel[i, j] = 0.

    if normalise:
        out = kernel / float(kernel.sum())
    else:
        out = kernel

    return out

def convolve_mask_kernel(convolveMatrixSubset, kernel):
    kNy, kNx = kernel.shape
    # convolveMatrixSubset = cloudMask[upperLimits[i]:up, :]
    convolveMatrix = np.zeros(
        (convolveMatrixSubset.shape[0] + 2 * kernel.shape[0], convolveMatrixSubset.shape[1] + 2 * kernel.shape[1]))
    convolveMatrix[kNy:(-kNy), kNx:(-kNx)] = convolveMatrixSubset
    for iy in range(kNy):
        convolveMatrix[iy, kNx:(-kNx)] = convolveMatrixSubset[0, :]
        convolveMatrix[-(iy + 1), kNx:(-kNx)] = convolveMatrixSubset[-1, :]
    for ix in range(kNx):
        convolveMatrix[:, ix] = convolveMatrix[:, kNx]
        convolveMatrix[:, -(ix + 1)] = convolveMatrix[:, -(kNx + 1)]

    perc_circle = fftconvolve(convolveMatrix, kernel, mode='same')
    return perc_circle[kNy:(-kNy), kNx:(-kNx)]


def cloud_shadow_processor_AASTR(cloudflagType = 'new'):
    #cth: fixed value at 3000m.
    #cloud mask: new 4th reprocessing cloud mask.
    # all data in nadir observations.

    #use the OLCI cloud shadow approach:

    cth = 6000.
    # todo: Processor for CTH (not included yet)
    # scene_path = "E:\Documents\projects\QA4EO\AATSR4th\\"    #dagmar
    scene_path = "H:\\related\QA4EO\AATSR4th Cloud Shadow\\"    #marco
    # filename = "subset_0_of_ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004.dim" # mediterranean Lybia
    # filename = "subset_SouthernHem_of_ENV_AT_1_RBT____20021129T235200_20021130T013735_20210315T024827_6334_011_359______DSI_R_NT_004.dim"
    # filename = "subset_LowSunNorth_of_ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004.dim"
    # filename = "subset_HalfSwathWidth_of_ENV_AT_1_RBT____20020810T083508.dim"
    # filename = "subset_HalfSwathWidthRight_of_ENV_AT_1_RBT____20020810T083508.dim"
    # filename = "ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004.SEN3" #full orbit: 7min
    filename = "ENV_AT_1_RBT____20090615T133401_20090615T151936_20210625T140648_6334_079_496______DSI_R_NT_004.SEN3"
    # filename = "ENV_AT_1_RBT____20021129T235200_20021130T013735_20210315T024827_6334_011_359______DSI_R_NT_004.dim" #full orbit: 55min

    # yline = None
    # if filename[:3] == 'ENV':
    #     yline = 8388
    # if filename[:3] == 'sub':
    #     yline = 364


    product = snp.ProductIO.readProduct(os.path.join(scene_path, filename))
    height = product.getSceneRasterHeight()
    width = product.getSceneRasterWidth()

    print(list(product.getBandNames()))

    sza = get_band_or_tiePointGrid(product, 'solar_zenith_tn')  # sline,eline,scol,ecol = subset
    confid_in = get_band_or_tiePointGrid(product, 'confidence_in', 'int32')
    dayMask = np.bitwise_and(confid_in, 2 ** 10) == 2 ** 10
    #check sun elevation and find first and last row with SZA<85°, daylight zone
    SZATest = np.logical_and(np.array(np.sum(sza < 85., axis=1) == width), np.sum(dayMask, axis=1)== width)
    sline = np.min(np.arange(0, height, 1)[SZATest])
    diffSZATest = np.diff(SZATest)
    if np.sum(diffSZATest>0) > 0:
        eline = np.arange(0, height-1, 1)[diffSZATest>0]
        eline = eline[eline > sline]
        eline = np.min(eline)
    else:
        eline = height
    sline = int(sline)
    eline = int(eline)
    print('startLine: ', sline)
    print('endLine: ', eline)
    # eline = np.max(np.arange(0, height, 1)[SZATest])
    sza = sza[sline:eline,:]
    subset = (int(sline), int(eline-1), 0, width-1)
    saa = get_band_or_tiePointGrid(product, 'solar_azimuth_tn', subset=subset)
    oza = get_band_or_tiePointGrid(product, 'sat_zenith_tn', subset=subset)
    x_tx = get_band_or_tiePointGrid(product, 'x_tx', subset=subset) # at nadir line x_tx changes sign. x<272 : x_tx<0; x>=272 : x_tx >0

    ## OAA is not needed! use fixed position of nadir line between x = 271, 272
    # oaa = get_band_or_tiePointGrid(product, 'sat_azimuth_tn') #needs adjustment!
    # test sat azimuth interpolation.
    # a = calculate_view_azimuth_interpolation_singleline(oaa[0,:], width, plot=True)
    # oaa_correct = np.zeros((height, width))
    # for i in range(height):
    #     oaa_correct[i,:] = a


    ## derive orientation at coarse raster positions.
    ## interpolate/extrapolate spatially to entire grid
    GridStep = 100
    X, Y = np.mgrid[1:(width-1):GridStep, 1:(eline-sline-1):GridStep]
    print("start Position")
    orientationS = np.zeros(X.shape)

    print('use latitude_tx')
    lat = get_band_or_tiePointGrid(product, 'latitude_tx', subset=subset)
    lon = get_band_or_tiePointGrid(product, 'longitude_tx', subset=subset)
    for iy in range(orientationS.shape[0]):
        for ix in range(orientationS.shape[1]):
            x = X[iy, ix]
            y = Y[iy, ix]
            # pixelPos1 = PixelPos(x - 1 + 0.5, sline + y + 0.5)
            # geoPos = product.getSceneGeoCoding().getGeoPos(pixelPos1, None)
            lat1 = lat[y, x-1]
            lon1 = lon[y, x-1]
            # pixelPos2 = PixelPos(x + 1 + 0.5, sline + y + 0.5)
            # geoPos = product.getSceneGeoCoding().getGeoPos(pixelPos2, None)
            lat2 = lat[y, x+1]
            lon2 = lon[y, x+1]
            orientationS[iy, ix] = calculate_orientation(lat1, lon1, lat2, lon2) * 180. / np.pi


    f = interpolate.RectBivariateSpline(y=np.arange(1,(eline-sline-1), GridStep), x=np.arange(1, (width-1), GridStep),
                                        z=orientationS, bbox=[0, width, 0, (eline-sline)] , kx=3, ky=3, s=0)
    xx = np.arange(0, width, 1)
    yy = np.arange(0, (eline-sline), 1)
    orientation = np.transpose(f(xx, yy)) # orientationFull,

    ### by Pixel: Lat, Lon, Orientation
    # deriving North direction on pixel grid, orientation
    # lat = np.zeros((eline - sline , width))
    # lon = np.zeros((eline - sline , width))
    # print("start Position")
    # for ix, x in enumerate(range(0, width)):
    #     for iy, y in enumerate(range(sline, eline)):
    #         pixelPos = PixelPos(x + 0.5, y + 0.5)
    #         geoPos = product.getSceneGeoCoding().getGeoPos(pixelPos, None)
    #         lat[iy, ix] = geoPos.getLat()
    #         lon[iy, ix] = geoPos.getLon()
    #
    # #direction of North on the grid. Used to adjust the sun azimuth angle to grid directions.
    # #in degree!
    # print("start orientation")
    # orientation = np.zeros(lat.shape)
    # for i in range(orientation.shape[0]):
    #     for j in range(width - 2):
    #         orientation[i, j + 1] = calculate_orientation(lat[i, j], lon[i, j], lat[i, j + 2], lon[i, j + 2]) * 180./np.pi
    # orientation[:, 0] = orientation[:, 1]
    # orientation[:, width-1] = orientation[:, width-2]

    ### plot orientation comparison
    # fig, ax = plt.subplots(1, 2, figsize=(8,5))
    # im = ax[0].imshow(orientationFull)
    # fig.colorbar(im, ax=ax[0], shrink=0.5)
    # ax[0].set_title('orient. Interpolated')
    # im=ax[1].imshow(orientation)
    # fig.colorbar(im, ax=ax[1], shrink=0.5)
    # ax[1].set_title('all pixl orientation')
    # plt.show()
    #
    # fig,ax = plt.subplots(1, 2, figsize=(8,5))
    # im = ax[0].imshow((orientationFull- orientation)/orientation*100.)
    # fig.colorbar(im, ax=ax[0], shrink=0.5)
    # ax[0].set_title('rel.Diff orient. Interpolated %')
    # im = ax[1].plot(orientation[:, 1], 'r-', label='all')
    # im = ax[1].plot(orientationFull[:, 1], 'b--', label='interpol.')
    # im = ax[1].plot( Y[0,:], orientationS[0, :], 'k+', label='points')
    # ax[1].legend()
    # plt.show()
    fig,ax = plt.subplots(1, 2, figsize=(8,5))
    im = ax[0].imshow(orientation)
    fig.colorbar(im, ax=ax[0], shrink=0.5)
    ax[0].set_title('orient. Interpolated ')
    im = ax[1].plot(orientation[:, 1], 'r-', label='all')
    im = ax[1].plot(Y[0,:], orientationS[0, :], 'k+', label='points')
    ax[1].legend()
    plt.show()

    ### cloud mask

    if cloudflagType == 'new':
        bayes_in = get_band_or_tiePointGrid(product, 'bayes_in', 'int32', subset=subset)
        bayesMask = set_cloud_mask_from_Bayesian(bayes_in)

        confid_in = get_band_or_tiePointGrid(product, 'confidence_in', 'int32', subset=subset)
        confidMask = set_cloud_mask_from_Confidence(confid_in)

        if np.sum(np.isnan(bayesMask)) > 0:
            cloudMask = confidMask
        else:
            #todo: what to do with partial information in bayesian cloud mask?
            print('cloud mask to be done')
            cloudMask = np.logical_or(bayesMask, confidMask)

    else:
        cloud_in = get_band_or_tiePointGrid(product, 'cloud_in', 'int32', subset=subset)
        cloudMask = set_cloud_mask_from_cloudBand(cloud_in)

    ## landmask
    landMask = set_land_mask(product, subset=subset)

    ### convolution cloudmask and search radius, convolution landmask and search radius
    # every 1000 or 2000 pixels (y-direction), the convolution is done with the current, mean search radius defined by SZA and CTH.

    ConvolStep = 2000
    upperLimits = np.arange(0, cloudMask.shape[0], ConvolStep)
    if len(upperLimits)> 1:
        upperLimits[-1] = cloudMask.shape[0]
    else:
        upperLimits = np.array((0, cloudMask.shape[0]))

    startSearchMask = np.zeros((cloudMask.shape))
    landConvolveMask = np.zeros((landMask.shape))
    for i, up in enumerate(upperLimits[1:]):
        #setup kernel
        radius = cth * np.tan(np.median(sza[upperLimits[i]:up,:])*np.pi/180.)
        print('kernel-radius', i, radius, 'at ', upperLimits[i])
        kernel = setup_round_kernel(radius=radius, spacing=(1000.,1000.)) #todo: use an elongated shape in direction of illumination!
        # kNy, kNx = kernel.shape
        # convolveMatrixSubset = cloudMask[upperLimits[i]:up,:]
        # convolveMatrix = np.zeros((convolveMatrixSubset.shape[0]+2*kernel.shape[0], convolveMatrixSubset.shape[1]+2*kernel.shape[1]))
        # convolveMatrix[kNy:(-kNy), kNx:(-kNx)] = convolveMatrixSubset
        # for iy in range(kNy):
        #     convolveMatrix[iy,kNx:(-kNx)] = convolveMatrixSubset[0,:]
        #     convolveMatrix[-(iy+1), kNx:(-kNx)] = convolveMatrixSubset[-1,:]
        # for ix in range(kNx):
        #     convolveMatrix[:, ix] = convolveMatrix[:, kNx]
        #     convolveMatrix[:, -(ix + 1)] = convolveMatrix[:, -(kNx+1)]
        #
        # perc_circle = fftconvolve(convolveMatrix, kernel, mode='same')
        startSearchMask[upperLimits[i]:up, :] = convolve_mask_kernel(cloudMask[upperLimits[i]:up, :], kernel)
        landConvolveMask[upperLimits[i]:up, :] = convolve_mask_kernel(landMask[upperLimits[i]:up, :], kernel)

    startSearchMask = np.logical_and(startSearchMask >0.001, startSearchMask < 0.998)
    startSearchMask = np.logical_and(startSearchMask, landConvolveMask > 0.001)
    # from PIL import Image
    # print('Shape of startSearchMask', startSearchMask.shape)
    # startSearchImage = Image.fromarray(startSearchMask)
    # startSearchImage.save('startSearchMask-python.png')
    
    print(np.sum(startSearchMask), np.sum(cloudMask))
    print(np.sum(np.logical_and(startSearchMask, cloudMask==1)))

    fig, ax = plt.subplots(1, 2, figsize=(5, 8))
    ax[0].imshow(cloudMask)
    ax[0].set_title('AATSR cloud mask')
    ax[1].imshow(startSearchMask)
    ax[1].set_title('Start Points')
    plt.show()

    elevation = get_band_or_tiePointGrid(product, 'elevation_in', subset=subset)
    spatialResolution = 1000.  # todo: read from product
    # maxObjectAltitude = np.nanmax(elevation)
    minSurfaceAltitude = np.nanmin(elevation)
    ShadowArray = np.zeros(elevation.shape)

    ###
    # print(filename)
    # print('SZA', sza[yline, :])
    # print('orientation', orientation[yline, :])
    # print('minSurfaceAltitude', minSurfaceAltitude)
    # print('startSearchMask', startSearchMask[yline, :])

    this_height = sza.shape[0]
    this_width = sza.shape[1]
    count = 0
    startTime = time.time()
    for i in range(this_height):
        for j in range(this_width):
            if cloudMask[i, j]==1 and startSearchMask[i, j]:
                count += 1
                if count % 10000 == 0:
                    print(count)
                #search for cloud shadow.
                # calculate theoretical height at search path position.
                illuPathSteps, illuPathHeight, threshHeight = setRelativePathIndex_and_TheoreticalHeight(sza=sza[i, j], saa=saa[i, j],
                                                                                                         oza=oza[i, j], x_tx = x_tx[i, j],#oaa = oaa_correct[i, j],
                                                                                                         orientation = orientation[i, j],
                                                                                                         spatialResolution=spatialResolution,
                                                                                                         maxObjectAltitude=cth,
                                                                                                         minSurfaceAltitude=minSurfaceAltitude)
                X = np.sqrt(illuPathSteps[:, 0] ** 2 + illuPathSteps[:, 1] ** 2)

                IndexArray = np.copy(illuPathSteps)
                IndexArray[:, 0] += j
                IndexArray[:, 1] += i
                # find cloud free positions along the search path:
                ID = np.logical_and(np.logical_and(IndexArray[:, 0] >= 0, IndexArray[:, 0] < this_width),
                                    np.logical_and(IndexArray[:, 1] >= 0, IndexArray[:, 1] < this_height))

                if np.sum(ID) > 3: # path positions
                    IndexArray = IndexArray[ID, :]
                    BaseHeightArray = illuPathHeight[ID]
                    # Xx = X[ID]
                    elevPath = elevation[IndexArray[:, 1], IndexArray[:, 0]]

                    # plt.plot(Xx, elevPath, 'g+')
                    # plt.plot(Xx, BaseHeightArray, 'rx')
                    # plt.show()
                    ID2 = np.logical_and(np.abs(BaseHeightArray - elevPath) < threshHeight ,
                                         np.logical_not(cloudMask[IndexArray[:, 1], IndexArray[:, 0]]))

                    ShadowArray[IndexArray[ID2, 1], IndexArray[ID2, 0]] = 1

    # plt.imshow(ShadowArray)
    # plt.show()
    #
    # out = cloudMask + ShadowArray *2
    # plt.imshow(out)
    # plt.show()
    endTime = time.time()

    print("Time", startTime, endTime)

    outpath = scene_path + filename[:42] + "_testCloudShadow_Cloud_" +cloudflagType+ ".dim"
    outProduct = Product('AASTR_cloudShadow', 'AASTR_cloudShadow', width, this_height) #height
    outProduct.setFileLocation(File(outpath))

    ProductSubsetDef = jpy.get_type('org.esa.snap.core.dataio.ProductSubsetDef')
    subset_def = ProductSubsetDef()
    subset_def.setRegion(0, sline, width, eline) # (0,0,width, height)
    product.transferGeoCodingTo(outProduct, subset_def)

    rad = get_band_or_tiePointGrid(product, 'S2_radiance_in', subset=subset)
    band = outProduct.addBand("S2_radiance_in", ProductData.TYPE_FLOAT32)
    band.setNoDataValue(np.nan)
    band.setNoDataValueUsed(True)
    sourceData = rad.reshape(cloudMask.shape).astype('float32')
    band.setRasterData(ProductData.createInstance(sourceData))

    band = outProduct.addBand("cloudMask", ProductData.TYPE_INT16)
    band.setNoDataValue(np.nan)
    band.setNoDataValueUsed(True)
    sourceData = cloudMask.reshape(cloudMask.shape).astype('int16')
    band.setRasterData(ProductData.createInstance(sourceData))

    band = outProduct.addBand("shadowMask", ProductData.TYPE_INT16)
    band.setNoDataValue(np.nan)
    band.setNoDataValueUsed(True)
    sourceData = ShadowArray.reshape(cloudMask.shape).astype('int16')
    band.setRasterData(ProductData.createInstance(sourceData))

    ProductIO.writeProduct(outProduct, outpath, 'BEAM-DIMAP')

    product.closeIO()
    outProduct.closeIO()


def analyse_AATSR4th_transect(varname='sat_azimuth_tn', start = 10950, end = 12950, step=50):
    # scene_path = "E:\Documents\projects\QA4EO\AATSR4th\\"     #dagmar
    scene_path = "H:\\related\QA4EO\AATSR4th Cloud Shadow\\"    #marco

    # filename = "ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004.SEN3"
    filename = "ENV_AT_1_RBT____20090615T133401_20090615T151936_20210625T140648_6334_079_496______DSI_R_NT_004.SEN3"
    # filename = "subset_0_of_ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004.dim"
    # filename = "subset_SouthernHem_of_ENV_AT_1_RBT____20021129T235200_20021130T013735_20210315T024827_6334_011_359______DSI_R_NT_004.dim"
    # filename = "subset_LowSunNorth_of_ENV_AT_1_RBT____20020810T083508_20020810T102042_20210303T040313_6334_008_264______DSI_R_NT_004.dim"
    product = snp.ProductIO.readProduct(os.path.join(scene_path, filename))
    height = product.getSceneRasterHeight()
    width = product.getSceneRasterWidth()

    # sza = get_band_or_tiePointGrid(product, 'solar_zenith_tn')  # sline,eline,scol,ecol = subset
    subset = (start, end, 0, width - 1)
    vaa = get_band_or_tiePointGrid(product, varname, subset=subset)


    yList = np.arange(start, end, step, dtype='int16')
    latPart = np.zeros((len(yList), width))
    lonPart = np.zeros((len(yList), width))
    orientationPart = np.zeros((len(yList), width))

    for j, y in enumerate(yList):
        for ix, x in enumerate(range(0, width)):
            pixelPos = PixelPos(x + 0.5, y + 0.5)
            geoPos = product.getSceneGeoCoding().getGeoPos(pixelPos, None)
            latPart[j, ix] = geoPos.getLat()
            lonPart[j, ix] = geoPos.getLon()

    for i in range(orientationPart.shape[0]):
        for j in range(width - 2):
            orientationPart[i, j + 1] = calculate_orientation(latPart[i, j], lonPart[i, j], latPart[i, j + 2],
                                                              lonPart[i, j + 2]) * 180. / np.pi
    orientationPart[:, 0] = orientationPart[:, 1]
    orientationPart[:, width - 1] = orientationPart[:, width - 2]

    fig, ax = plt.subplots(1, 2, figsize=(8,5))
    for j,y in enumerate(yList):
        ax[0].plot(vaa[y-start, :], '-', label=y)
        ax[1].plot(vaa[y-start, :]-orientationPart[j,:], '-', label=y)
    ax[0].set_xlabel('pixel No X')
    ax[0].set_ylabel('view azimuth angle')
    ax[1].set_xlabel('pixel No X')
    ax[1].set_ylabel('view azimuth angle - orientation')
    plt.show()

    # yList = np.arange(end, end+2000, 50, dtype='int16')
    # for y in yList:
    #     plt.plot(vaa[y,:], '-', label=y)
    # plt.legend()
    # plt.show()


# investigate_AATSR4th_transect(plot_BT=True)
# investigate_AATSR4th_transect(plot_Temp=True)

### cloud shadow processor
cloud_shadow_processor_AASTR(cloudflagType='old')

#testing setRelativePathIndex_and_TheoreticalHeight with specific values
# illuPathSteps, illuPathHeight, threshHeight = setRelativePathIndex_and_TheoreticalHeight(83.9982, 313.2662, 16.4364, 173500.0, 154.91488, 1000, 6000, -65)

# analyse_AATSR4th_transect()
# analyse_AATSR4th_transect(start=11600, end=30860, step=1000)