The changelog will not be maintaned aynmore.
For the track of changes please referr to the releas not on GitHub.
https://github.com/bcdev/snap-idepix/releases

**Changes in Idepix Olci v9.0.0**
* Update Tensorflow library to latest version (#14)
* Duplicated cloud shadow near scene edge in OLCI data (#26)

**Changes in Idepix Olci v8.0.1**
* OLCI mountain shadow around nadir view creates artefact (#52)
* Adapt Idepix OLCI to updated OLCI O2A Harmonisation (#13)
* made hillshade algorithm available (#28)

**Changes in Idepix Olci v8.0.0**
* Snow/ice flag was not correct over fresh inland water

**Changes in Idepix Olci v7.0.2**
* Flag of inland water is now correctly used in classification
* Cloud shadow direction on southern hemisphere is fixed.

**Changes in Idepix Olci v7.0.1**
* Fixed memory leaks caused by wrong usage of Tensorflow
* Cloud shadow data correctly set to target tiles
* Reduced memory usage by directly using watermask classifier instead of the operator
* Cloud buffer was not correctly set if corresponding cloudy pixel was inside cloud buffer, but outside tile boundary

**Changes in Idepix Olci v7.0.0**
* Changed operator aliases to have common naming convention
* Minor update of the help files
* Handle error in TensorFlow init
* Adaptation for new parameter names in OlciO2aHarmonisation
* Architectural change - idepix has been removed from the snap distribution 

