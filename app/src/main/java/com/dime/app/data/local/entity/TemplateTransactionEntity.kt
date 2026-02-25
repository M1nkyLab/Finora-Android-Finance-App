package com.dime.app.data.local.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TemplateTransactionEntity(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val income: Boolean = false,
    val note: String = "",
    @SerialName("order") val order: Int = 0,
    @SerialName("recurring_type") val recurringType: Int = 0,
    @SerialName("recurring_coefficient") val recurringCoefficient: Int = 0,
    @SerialName("category_id") val categoryId: String? = null
)
