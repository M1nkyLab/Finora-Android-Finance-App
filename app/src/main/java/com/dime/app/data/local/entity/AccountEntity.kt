package com.dime.app.data.local.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AccountEntity(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("account_name") val accountName: String = "",
    @SerialName("starting_balance") val startingBalance: Double = 0.0,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
)
