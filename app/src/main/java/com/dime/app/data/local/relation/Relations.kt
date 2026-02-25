package com.dime.app.data.local.relation

import com.dime.app.data.local.entity.BudgetEntity
import com.dime.app.data.local.entity.CategoryEntity
import com.dime.app.data.local.entity.TemplateTransactionEntity
import com.dime.app.data.local.entity.TransactionEntity

/**
 * Transaction joined with its Category (for list display and insights).
 * Plain data class — no Room annotations.
 */
data class TransactionWithCategory(
    val transaction: TransactionEntity,
    val category: CategoryEntity?
)

/**
 * Budget joined with its linked Category.
 */
data class BudgetWithCategory(
    val budget: BudgetEntity,
    val category: CategoryEntity?
)

/**
 * Template joined with its linked Category.
 */
data class TemplateWithCategory(
    val template: TemplateTransactionEntity,
    val category: CategoryEntity?
)
