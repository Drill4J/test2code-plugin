package com.epam.drill.plugins.test2code.api

import kotlinx.serialization.*

@Serializable
class FieldErrorsDto(
    val fieldErrors: List<FieldErrorDto>
)

@Serializable
data class FieldErrorDto(
    val field: String,
    val message: String
)
