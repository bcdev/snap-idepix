## Notes for the development of the AATSR Cloud Shadow (IdePix) Processor
***

The processor shall compute a cloud shadow flag based on the cloud flag provided 
in AATSR 4th reprocessing data. It shall be provided as output combined with the input flags.
This flag band shall follow the other pixel_classif_flags bands generated in other 
IdePix processors. Additional work beyond this scope is to be discussed.

Time being it is not intended to make this processor publicly available.
It will probably only be used within this project.

### The Algorithm Prototype
The prototype has been implemented by D. Müller.
The code is located at [aatsr_cloud_shadow_dev.py]( 
https://github.com/bcdev/geoinfopy/blob/d8fc400b6aa11e145e0f4524191337babfec626a/sandbox/dagmar/QA4EO/aatsr_cloud_shadow_dev.py) on GitHub 
and in this project at *resource/aatsr_cloud_shadow_dev.py*.

A TN for the prototype is located at: 
*fs1\projects\ongoing\QA4EO\WorkingArea\CloudShadow_AATSR\Technical Note_cloudshadow_draft_v0.1.docx*

### Test Data
Data for testing can be found at *H:\related\QA4EO\AATSR4th Cloud Shadow*
More 4th reprocessing data is currently not yet availabe, but 
can be obtained from Darren Ghent (partner in this project) or from Pauline Cocevar.