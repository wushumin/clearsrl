# liblinearbinary
Specialized liblinear w/ binary feature values.

This has a much smaller memory foot-print (4-byte int (index) per feature value) than the [standard java liblinear port](https://github.com/bwaldvogel/liblinear-java) (represents each feature value as an Object with an int (index) & a double field (value), requiring up to 32 bytes on 64 bit JVM).

This makes it a lot easier for training SRL or other ML applications w/ large number of training examples on systems w/ more limited amount of memory.

The source is from version 1.4.x of the standard java liblinear port and version 1.9.x port of Tron.java, alleviating BLAS library dependencies.

A more memory efficient liblinear java port accepting a combination of binary and double features values (using an array of indices + an array of feature values, totaling 12 bytes per double feature value) is being considered.
