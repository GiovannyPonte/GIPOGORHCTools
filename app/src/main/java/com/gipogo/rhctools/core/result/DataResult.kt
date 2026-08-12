package com.gipogo.rhctools.core.result

sealed class DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>()
    data class Failure(val error: DataError) : DataResult<Nothing>()
}
