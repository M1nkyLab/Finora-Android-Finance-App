package com.dime.app.data.local.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CategoryEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "",
    val colour: String = "#6C63FF",
    val income: Boolean = false,
    @SerialName("order") val order: Long = 0L,
    @SerialName("date_created") val dateCreated: Long = System.currentTimeMillis()
)
