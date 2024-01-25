# About the CTP neural network

The CTP neural network was trained on simulated data of optically thick clouds (COD greater than 10) and of clouds of any optical thickness over dark surface (Albedo less than 0.05). The network shall not be appied to data of optically thin clouds over brighter surface. When the surface is not dark, a brightness test (or similar means) shall be applied to identify optically thick clouds where the network is applicable.

Note that CTP is almost a bilinear function of two parameters (any normalized radiance next to the O2 absorption region and any transmittance within an O2 absorption line). The input to the neural network nevertheless comprises more parameters to increase the accuracy of the approximation. During the training, noise (of 0.4 percent) was added to radiance and transmittance inputs on the fly.

## Inputs to the CTP neural network

The shape of the input data is

    input_shape = (number of pixels, number of inputs per pixel = 7)

where the inputs per pixel consist of

1. Cosine of sun zenith angle (SZA, degrees)
2. Cosine of viewing zenith angle (VZA, degrees)
3. Azimuthal difference angle (degrees)
4. OLCI radiance 12 normalized by solar irradiance
5. Negative logarithm of OLCI de-smiled transmittance 13
6. Negative logarithm of OLCI de-smiled transmittance 14
7. Negative logarithm of OLCI de-smiled transmittance 15

### Computation of azimuthal difference angle input (3.)

    saa = ...  # sun azimuth angle (degrees)
    vaa = ...  # viewing azimuth angle (degrees)
    vza = ...  # viewing zenith angle (degrees)
    ada = arccos(cos(to_radian(saa - vaa))) * sin(to_radian(vza))

### Computation of normalized radiance input (4.)

    rad = radiance_12  # OLCI radiance 12
    irr = ...  # solar irradiance within OLCI channel 12
    rad = rad / irr  # normalized radiance

## Output of the CTP neural network

The shape of the output data is

    output_shape = (number of pixels, number of outputs per pixel = 1)

where the only output of the neural network is cloud top pressure (CTP, hPa).

## Training residuals

![Number density of CTP residuals over dark surface (albedo less than 0.05)](./ctp-hist2d-residuals-black-i7x14x7o1-0011.png 
"Number density of CTP residuals over dark surface (albedo less than 0.05)")

![Number density of CTP residuals for optically thick clouds (COD greater than 10)](./ctp-hist2d-residuals-i7x14x7o1-0011.png 
"Number density of CTP residuals for optically thick clouds (COD greater than 10)")

![Number density of CTP residuals for optically thin clouds (COD less than 10)](./ctp-hist2d-residuals-gray-i7x14x7o1-0011.png 
"Number density of CTP residuals for optically thin clouds (COD less than 10)")

![Number density of CTP residuals for all clouds and surface brightnesses](./ctp-hist2d-residuals-training-i7x14x7o1-0011.png 
"Number density of CTP residuals for all clouds and surface brightnesses")

## Training statistics

Training statistics are summarized in the file `ctp-statistics-i7x14x7o1-0011.json`.

