package com.dime.app.data.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed result from the AI extraction.
 */
data class ParsedTransaction(
    val title: String,
    val amount: Double,
    val category: String,   // One of: Food, Transport, Subscriptions, Shopping, Bills, Income, Others
    val date: LocalDate,
    val type: String,        // "expense" or "income"
    val recurring: String = "none"  // "none", "daily", "weekly", "monthly"
)

/**
 * Sealed class representing the result of an AI parse attempt.
 */
sealed class AiParseResult {
    data class Success(val transaction: ParsedTransaction) : AiParseResult()
    data class Error(val message: String) : AiParseResult()
}

/**
 * Service that uses Google Gemini to extract structured financial data
 * from natural language input.
 *
 * Maps the AI-returned category string to the app's existing CategoryEntity IDs.
 */
@Singleton
class AiTransactionService @Inject constructor() {

    /**
     * Map AI category names → app CategoryEntity IDs.
     * See DimeRepository.defaultCategories() for the definitive list.
     *
     * Expense: cat_food, cat_transport, cat_shopping, cat_bills, cat_entertain, cat_health
     * Income:  cat_salary, cat_freelance, cat_gift
     */
    val categoryMapping = mapOf(
        "Food"          to "cat_food",
        "Transport"     to "cat_transport",
        "Subscriptions" to "cat_entertain",
        "Shopping"      to "cat_shopping",
        "Bills"         to "cat_bills",
        "Health"        to "cat_health",
        "Entertainment" to "cat_entertain",
        "Income"        to "cat_salary",
        "Freelance"     to "cat_freelance",
        "Gift"          to "cat_gift",
        "Others"        to "cat_food"         // fallback
    )

    /**
     * Build the system prompt, injecting today's date.
     */
    private fun buildPrompt(userInput: String): String {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return """
You are a strict financial data extractor API. Your only job is to analyze the user's natural language input and output a single, perfectly formatted JSON object.

DO NOT output any conversational text, explanations, or markdown formatting (like ```json). ONLY output the raw JSON object.

### RULES:
1. "amount": Must be a clean number. Remove "RM", currency symbols, or words. (e.g., 15.50).
2. "category": Must strictly be one of the following exact strings: ["Food", "Transport", "Subscriptions", "Shopping", "Bills", "Income", "Others"].
3. "title": A clean, capitalized 1-3 word summary of the transaction.
4. "date": Format as YYYY-MM-DD. Use the provided [CURRENT_DATE] as your reference point to resolve words like "today", "yesterday", or day names.
5. "type": Must be either "expense" (if money is spent) or "income" (if money is received, earned, or deposited).
6. "recurring": Detect if this is a recurring/repeating transaction. Must be one of: "none", "daily", "weekly", "monthly", "yearly".
   - Use "daily" for phrases like: "every day", "daily", "each day".
   - Use "weekly" for phrases like: "every week", "weekly", "each week".
   - Use "monthly" for phrases like: "every month", "monthly", "each month", "per month", "subscription", "membership".
   - Use "yearly" for phrases like: "every year", "yearly", "each year", "annually", "per year".
   - Use "none" for one-time transactions with no indication of recurring.

[CURRENT_DATE]: $today

### EXAMPLES:

User: "makan nasi lemak RM5 today"
Output: {"title": "Nasi Lemak", "amount": 5.00, "category": "Food", "date": "$today", "type": "expense", "recurring": "none"}

User: "sold some cookies and got rm50 today"
Output: {"title": "Cookie Sales", "amount": 50.00, "category": "Income", "date": "$today", "type": "income", "recurring": "none"}

User: "paid 80 for internet bill yesterday"
Output: {"title": "Internet Bill", "amount": 80.00, "category": "Bills", "date": "${LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)}", "type": "expense", "recurring": "none"}

User: "grab to work rm15"
Output: {"title": "Grab Ride", "amount": 15.00, "category": "Transport", "date": "$today", "type": "expense", "recurring": "none"}

User: "netflix subscription rm45 every month"
Output: {"title": "Netflix", "amount": 45.00, "category": "Subscriptions", "date": "$today", "type": "expense", "recurring": "monthly"}

User: "parking rm7 daily"
Output: {"title": "Parking", "amount": 7.00, "category": "Transport", "date": "$today", "type": "expense", "recurring": "daily"}

User: "gym membership rm150 per month"
Output: {"title": "Gym Membership", "amount": 150.00, "category": "Subscriptions", "date": "$today", "type": "expense", "recurring": "monthly"}

User: "weekly allowance rm100"
Output: {"title": "Weekly Allowance", "amount": 100.00, "category": "Income", "date": "$today", "type": "income", "recurring": "weekly"}

User: "pay expenses rm7 each month"
Output: {"title": "Monthly Expenses", "amount": 7.00, "category": "Bills", "date": "$today", "type": "expense", "recurring": "monthly"}

User: "car insurance rm800 annually"
Output: {"title": "Car Insurance", "amount": 800.00, "category": "Bills", "date": "$today", "type": "expense", "recurring": "yearly"}

### USER INPUT TO PROCESS:
User: "$userInput"
Output:
""".trimIndent()
    }

    /**
     * Send user input to Gemini and parse the structured JSON response.
     *
     * @param apiKey  The Gemini API key (from BuildConfig or user-entered).
     * @param userInput  The natural language transaction description.
     * @return [AiParseResult] with either parsed data or an error.
     */
    suspend fun parseTransaction(apiKey: String, userInput: String): AiParseResult {
        if (apiKey.isBlank()) {
            return AiParseResult.Error("No API key configured. Add your Gemini API key in Settings.")
        }
        if (userInput.isBlank()) {
            return AiParseResult.Error("Please type something to log.")
        }

        return try {
            val prompt = buildPrompt(userInput)
            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 256)
                })
            }

            val responseBody = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                connection.outputStream.use { os ->
                    val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val statusCode = connection.responseCode
                if (statusCode != 200) {
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown HTTP error"
                    throw RuntimeException("API error ($statusCode): $errorResponse")
                }

                connection.inputStream.bufferedReader().use { it.readText() }
            }

            val responseJson = JSONObject(responseBody)
            val candidates = responseJson.optJSONArray("candidates") ?: JSONArray()
            if (candidates.length() == 0) {
                return AiParseResult.Error("No candidates returned.")
            }
            
            val contentObj = candidates.getJSONObject(0).optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts") ?: JSONArray()
            val rawText = if (parts.length() > 0) parts.getJSONObject(0).optString("text", "") else ""
            
            if (rawText.isBlank()) return AiParseResult.Error("Empty response from AI.")

            // Strip any markdown fencing the model might add despite instructions
            val jsonStr = rawText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(jsonStr)

            val parsed = ParsedTransaction(
                title    = json.optString("title", "Transaction"),
                amount   = json.optDouble("amount", 0.0),
                category = json.optString("category", "Others"),
                date     = try {
                    LocalDate.parse(json.optString("date"), DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (_: Exception) {
                    LocalDate.now()
                },
                type     = json.optString("type", "expense"),
                recurring = json.optString("recurring", "none").lowercase()
            )

            AiParseResult.Success(parsed)
        } catch (e: Exception) {
            val trace = android.util.Log.getStackTraceString(e)
            android.util.Log.e("AiTransactionService", "Error parsing transaction: $trace")
            AiParseResult.Error("AI error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
