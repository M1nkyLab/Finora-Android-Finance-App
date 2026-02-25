package com.dime.app.data.local.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class BudgetEntity(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    @SerialName("show_green") val showGreen: Boolean = false,
    @SerialName("start_date") val startDate: Long = System.currentTimeMillis(),
    @SerialName("date_created") val dateCreated: Long = System.currentTimeMillis(),
    val type: Int = 2,
    @SerialName("category_id") val categoryId: String? = null
)

@Serializable
data class MainBudgetEntity(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    @SerialName("show_green") val showGreen: Boolean = false,
    @SerialName("start_date") val startDate: Long = System.currentTimeMillis(),
    @SerialName("date_created") val dateCreated: Long = System.currentTimeMillis(),
    val type: Int = 2
)
