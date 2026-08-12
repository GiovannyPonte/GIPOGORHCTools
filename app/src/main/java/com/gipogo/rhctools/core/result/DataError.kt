package com.gipogo.rhctools.core.result

sealed class DataError {
    data object NotFound : DataError()
    data object DuplicateCode : DataError()
    data class Validation(val field: String, val reason: String) : DataError()
    data class Db(val message: String? = null) : DataError()
    data class Unknown(val message: String? = null) : DataError()
}
