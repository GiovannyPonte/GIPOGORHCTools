package com.gipogo.rhctools.domain

data class FickResult(
    val cardiacOutputLMin: Double,
    val cardiacIndexLMinM2: Double?,
    val caO2_mlPerDl: Double,
    val cvO2_mlPerDl: Double,
    val avDiff_mlPerDl: Double
)

data class ResistanceResult(
    val woodUnits: Double,
    val dynesSecCm5: Double
)

data class CpoResult(
    val cpoWatts: Double,
    val cpiWattsPerM2: Double?
)

data class PapiResult(
    val papi: Double
)
