package com.dime.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single transaction parsed from a "brain dump" multi-item input.
 */
data class BrainDumpItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String,
    val amount: Double,
    val type: String,       // "expense" | "income"
    val category: String,   // Food, Transport, Shopping, Bills, Health, Entertainment, Income, Freelance, Gift, Others
    val icon: String,       // lucide/material icon name hint
    val date: LocalDate,
    val recurring: String = "none"  // none | daily | weekly | monthly | yearly
)

sealed class BrainDumpResult {
    data class Success(val items: List<BrainDumpItem>) : BrainDumpResult()
    data class Error(val message: String) : BrainDumpResult()
}

/**
 * AI service that converts a free-form "brain dump" string into multiple
 * structured BrainDumpItem objects via Gemini.
 */
@Singleton
class BrainDumpService @Inject constructor() {

    private fun buildPrompt(userInput: String): String {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

        return """
You are a high-speed financial data parser for a premium budgeting app.
Your task is to convert a natural language string into a JSON array of transaction objects.

DO NOT output any conversational text, explanations, or markdown code fences. Output ONLY the raw JSON array.

### EXTRACTION RULES:
1. MULTI-ITEM DETECTION: Identify separate transactions divided by commas, "and", "+" or new lines.
2. AMOUNTS: Extract the numerical value only. Strip "RM", "${'$'}", currency symbols.
3. CATEGORIES: Assign exactly one of: Food, Transport, Subscriptions, Shopping, Bills, Health, Entertainment, Income, Freelance, Gift, Others.
4. TYPE: Determine "expense" or "income".
   - Income keywords: got, received, allowance, salary, earned, paid me, income, freelance, sell, sold, bonus, refund, reimbursement.
   - Everything else is "expense".
5. ICON: Assign one of: utensils, fuel, car, shopping_bag, receipt, heart, music, wallet, gift, dollar_sign, zap, home, coffee, plane, train, phone, tv, gamepad, book.
6. DATE: Format YYYY-MM-DD. Use [TODAY] as reference. "today" = [TODAY], "yesterday" = [YESTERDAY].
7. RECURRING: Detect "none", "daily", "weekly", "monthly", "yearly".

[TODAY]: $today
[YESTERDAY]: $yesterday

### EXAMPLES:

Input: "Lunch 15, Petrol 40, and I got my allowance 900"
Output:
[
  {"description":"Lunch","amount":15.00,"type":"expense","category":"Food","icon":"utensils","date":"$today","recurring":"none"},
  {"description":"Petrol","amount":40.00,"type":"expense","category":"Transport","icon":"fuel","date":"$today","recurring":"none"},
  {"description":"Allowance","amount":900.00,"type":"income","category":"Income","icon":"wallet","date":"$today","recurring":"none"}
]

Input: "netflix rm45 monthly, gym membership rm80/month, salary 4500"
Output:
[
  {"description":"Netflix","amount":45.00,"type":"expense","category":"Subscriptions","icon":"tv","date":"$today","recurring":"monthly"},
  {"description":"Gym Membership","amount":80.00,"type":"expense","category":"Health","icon":"heart","date":"$today","recurring":"monthly"},
  {"description":"Salary","amount":4500.00,"type":"income","category":"Income","icon":"wallet","date":"$today","recurring":"none"}
]

Input: "coffee 6 yesterday, grab home 12, groceries 85"
Output:
[
  {"description":"Coffee","amount":6.00,"type":"expense","category":"Food","icon":"coffee","date":"$yesterday","recurring":"none"},
  {"description":"Grab Ride","amount":12.00,"type":"expense","category":"Transport","icon":"car","date":"$today","recurring":"none"},
  {"description":"Groceries","amount":85.00,"type":"expense","category":"Food","icon":"shopping_bag","date":"$today","recurring":"none"}
]

### USER INPUT:
"$userInput"

Output:
""".trimIndent()
    }

    suspend fun parseBrainDump(apiKey: String, userInput: String): BrainDumpResult {
        if (apiKey.isBlank()) {
            return BrainDumpResult.Error("No API key configured. Add your Gemini API key in Settings.")
        }
        if (userInput.isBlank()) {
            return BrainDumpResult.Error("Please type something to log.")
        }

        return try {
            val prompt = buildPrompt(userInput)

            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 1024)
                })
            }

            val responseBody = withContext(Dispatchers.IO) {
                val url = java.net.URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                )
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
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "HTTP $statusCode"
                    throw RuntimeException("API error ($statusCode): $err")
                }

                connection.inputStream.bufferedReader().use { it.readText() }
            }

            val responseJson = JSONObject(responseBody)
            val candidates = responseJson.optJSONArray("candidates") ?: JSONArray()
            if (candidates.length() == 0) return BrainDumpResult.Error("No candidates returned.")

            val contentObj = candidates.getJSONObject(0).optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts") ?: JSONArray()
            val rawText = if (parts.length() > 0) parts.getJSONObject(0).optString("text", "") else ""

            if (rawText.isBlank()) return BrainDumpResult.Error("Empty AI response.")

            // Strip potential markdown fencing
            val jsonStr = rawText
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val array = JSONArray(jsonStr)
            val items = mutableListOf<BrainDumpItem>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dateStr = obj.optString("date", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                val parsedDate = try {
                    LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (_: Exception) {
                    LocalDate.now()
                }

                items.add(
                    BrainDumpItem(
                        description = obj.optString("description", "Transaction"),
                        amount      = obj.optDouble("amount", 0.0),
                        type        = obj.optString("type", "expense").lowercase(),
                        category    = obj.optString("category", "Others"),
                        icon        = obj.optString("icon", "dollar_sign"),
                        date        = parsedDate,
                        recurring   = obj.optString("recurring", "none").lowercase()
                    )
                )
            }

            if (items.isEmpty()) BrainDumpResult.Error("No transactions detected. Try: \"Lunch 15, Petrol 40\"")
            else BrainDumpResult.Success(items)

        } catch (e: Exception) {
            android.util.Log.e("BrainDumpService", "Parse error", e)
            BrainDumpResult.Error("AI error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
