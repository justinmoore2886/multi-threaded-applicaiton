#!/bin/bash

#PBS -N HW5b
#PBS -q cs671
#PBS -l walltime=10:00

sbt -Dsbt.log.format=false clean 'test:runMain grading.Grade'
