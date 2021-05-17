# Learning Shape Priors from Pieces

This repository contains all the code to reproduce our results from our ShaeMi2020 MICCAI workshop publication:
- Dennis Madsen, Jonathan Aellen, Andreas Morel-Forster, Thomas Vetter and Marcel Lüthi ["Learning Shape Priors from Pieces"](#) 

[Video presentation @ ShapeMi2020](https://www.youtube.com/watch?v=YqFhwr8VFOs)

BibTex:
```bibtex
@inproceedings{madsen2020learning,
  title={Learning Shape Priors from Pieces},
  author={Madsen, Dennis and Aellen, Jonathan and Morel-Forster, Andreas and Vetter, Thomas and Lüthi, Marcel},
  booktitle={International Workshop on Shape in Medical Imaging},
  pages={30--43},
  year={2020},
  organization={Springer}
}
```

This paper is an extension of our ECCV2020 paper ["A Closest Point Proposal for MCMC-based Probabilistic Surface Registration
"](https://github.com/unibas-gravis/icp-proposal)
The extension involves:
 - Registration of 2D linemesh data
 - Model building from random posterior samples
 - Model comparison based on specificity, generalization and compactness.

Markov Chain Monte Carlo for shape registration with examples using [Scalismo](https://github.com/unibas-gravis/scalismo).


## Overview
The project is written in Scala and constructed to use the interactive build tool [SBT](https://www.scala-sbt.org/).

After installing SBT, clone this repository to your local machine and envoke `sbt run` in the project folder. SBT will now show a list of available main files that can be executed. 
Below is a description of the individual scripts to use. 
## Hand experiments
All data for the hand experiment is already provided in the repository under **data/hand/**. The data is already landmark aligned. 

The first step is to create partial data for the experiments:
 - **hand/preprocessing/CreatePartialData**.
 
Then we need to create a *missing-data-model* (an analytically defined GPMM):
 - **hand/preprocessing/Create2DGPModel**

Now that all the data is prepared, we can begin to register the partial data:
 - **hand/RegistrationSingle**: registers a single partial hand (easy configurable to choose the target).
 - **hand/VisualizeLog**: show the MAP/mean and random samples from a log file.
 - **hand/VisualizeHandModel**: show the principal components and random samples from a 2D hand model.
Before we can build PDMs from multiple imputations from multiple targets, we need to register a lot of the target meshes:
 
 To register all partial hands, run the script:
 - **hand/RegistrationAll**: a log file for each registration is created.
 
 After the registrations, we can convert the log files into meshes:
 - **hand/Logs2Meshes** helper script to convert the log file data into meshes which are then stored on the disk. By default, the script converts hands which are cut 15% from a random finger.
 - **hand/CreatePDMsFromFiles** create PDM models from the dumped files (model from complete data, model from mean predictions, model from MAP predictions and model from random samples, i.e. multiple imputations).
 - **hand/CompareModels** finally we can compare the created PDM models based on their specificity, generalization and compactness.


## Femur experiments
The experiments are found under *apps/femur*. First we need to download the needed data from the SMIR page: 

### Data preparation
The test dataset we use is the same as used in the [Statistical Shape Modelling course on futurelearn](https://www.futurelearn.com/courses/statistical-shape-modelling) and can be downloaded from SMIR:
[comment]: <> (Registration procedure from the SSM course <https://www.futurelearn.com/courses/statistical-shape-modelling/0/steps/16884>)

- Go to the [SMIR registraiton page](https://www.smir.ch/Account/Register).
- Fill in your details, and select **SSM.FUTURELEARN.COM** as a research unit.
- *This will send a request to an administrator to authorize your account creation. Please bare in mind that this might take **up to 24h**. You will be informed by Email once your account creation is authorized.*
- Follow the [instructions on Sicas Medical Image Repository (SMIR)](https://www.smir.ch/courses/FutureLearnSSM/2016) to download the required femur surfaces and corresponding landmarks.
- We only use the data from **Step 2** of the project
- Extract the folder under **data/femur/SMIR**, such that the mesh **0.stl** can be found under **data/femur/SMIR/step2/meshes**
- Align all the test meshes to the model by running the **apps/femur/AlignShapes** script

The repository already contains Gaussian Process Morphable Models (GPMMs) of the femur - approximated with 50 basis functions. 

### Experiment
First the data need to be aligned. 
- **femur/preprocessing/AlignShapes**: Align all shapes from SMIR to the reference mesh using a few manually clicked landmarks.
- **femur/preprocessing/RegistrationOfCommpleteFemurs**: The next step is to register the complete shapes with the provided model.
- **femur/preprocessing/CreatePartialData**: Then we need to create the synthetic partial data.
The next step is to register the partial data:
- **femur/RegistrationSingle**: Allows you to analyze the registration of a single partial target
- **femur/RegistrationAll**: This will register all the partial femurs one-by-one without showing the UI.
When all the partial data has been registered, we need to convert the log files to meshes and create PDMs from the meshes:
- **femur/DumpMeshesFromLog**: This script will dump meshes from all the created log files.
- **femur/CreatePDMsFromFiles**
And finally we can compare the differently created models
- **femur/CompareModels**

- **femur/RegistrationOfCompleteFemurs**: Registration of complete femurs.

## Contributors
- [Dennis Madsen](https://github.com/madsendennis)
- [Jonathan Aellen](https://github.com/JonathanAellen)
- [Andreas Forster](https://github.com/Andreas-Forster)
- [Marcel Luethi](https://github.com/marcelluethi)

## Dependencies
- [scalismo](https://github.com/unibas-gravis/scalismo) `0.90-RC1`
