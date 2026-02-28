package com.dime.app.data.local.relation

import androidx.compose.runtime.Immutable
import com.dime.app.data.local.entity.BudgetEntity
import com.dime.app.data.local.entity.CategoryEntity
import com.dime.app.data.local.entity.TemplateTransactionEntity
import com.dime.app.data.local.entity.TransactionEntity

/**
 * Transaction joined with its Category (for list display and insights).
 * Plain data class — no Room annotations.
 */
@Immutable
data class TransactionWithCategory(
    val transaction: TransactionEntity,
    val category: CategoryEntity?
)

/**
 * Budget joined with its linked Category.
 */
@Immutable
data class BudgetWithCategory(
    val budget: BudgetEntity,
    val category: CategoryEntity?
)

/**
 * Template joined with its linked Category.
 */
@Immutable
data class TemplateWithCategory(
    val template: TemplateTransactionEntity,
    val category: CategoryEntity?
)
