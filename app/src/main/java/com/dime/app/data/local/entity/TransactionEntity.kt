package com.dime.app.data.local.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TransactionEntity(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    val day: Long = 0L,
    val month: Long = 0L,
    val note: String = "",
    val income: Boolean = false,
    @SerialName("once_recurring") val onceRecurring: Boolean = false,
    @SerialName("recurring_type") val recurringType: Int = 0,
    @SerialName("recurring_coefficient") val recurringCoefficient: Int = 0,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("account_id") val accountId: String? = null
)
