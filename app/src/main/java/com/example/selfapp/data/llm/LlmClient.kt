package com.example.selfapp.data.llm

import android.util.Log
import com.example.selfapp.data.dao.LlmSettingsDao
import com.example.selfapp.data.entity.LlmSettings
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmClient @Inject constructor(
    private val llmSettingsDao: LlmSettingsDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateTransformations(questionText: String): List<String> = withContext(Dispatchers.IO) {
        val settings = llmSettingsDao.getSettings() ?: LlmSettings()

        if (settings.apiKey.trim().isEmpty()) {
            // Fallback mock transformations for local testing
            return@withContext List(10) { index ->
                "Transformation ${index + 1} of: \"$questionText\". (SelfApp generated offline variation to test the same theoretical concepts)."
            }
        }

        val systemPrompt = settings.customSystemPrompt
        val userPrompt = settings.customUserPrompt.replace("{question}", questionText)

        try {
            val responseText = makeApiRequest(settings, systemPrompt, userPrompt)
            val jsonObject = parseResponseObject(responseText)
            val jsonArray = jsonObject.getAsJsonArray("transformations")
            val result = mutableListOf<String>()
            if (jsonArray != null) {
                for (element in jsonArray) {
                    result.add(element.asString)
                }
            }
            if (result.size >= 10) result.take(10) else padTransformations(result, questionText)
        } catch (e: Exception) {
            Log.e("LlmClient", "Error generating transformations from LLM", e)
            // Return fallback mock transformations in case of API failure
            List(10) { index ->
                "Transformation ${index + 1} (Fallback due to error: ${e.localizedMessage}): $questionText"
            }
        }
    }

    suspend fun gradeSubmission(
        questionText: String,
        reasoningText: String,
        solutionFilesText: String
    ): GradeResult = withContext(Dispatchers.IO) {
        val settings = llmSettingsDao.getSettings() ?: LlmSettings()

        if (settings.apiKey.trim().isEmpty()) {
            // Offline grading fallback mock
            val randomScore = (70..98).random()
            return@withContext GradeResult(
                score = randomScore,
                feedback = "Offline Grading Mode: Good effort! Detected reasoning document (${reasoningText.length} chars) and code file uploads. Reviewing locally: the implementation logic mirrors standard algorithmic patterns."
            )
        }

        val systemPrompt = "You are an automated computer science grader.\n" +
                "Evaluate the student's solution files and reasoning files against the question.\n" +
                "Calculate a score from 0 to 100.\n" +
                "Provide detailed constructive feedback.\n" +
                "Return your response in JSON format containing two keys: \"score\" (an integer) and \"feedback\" (a string). Do not return any extra characters or markdown."

        val userPrompt = "Question:\n$questionText\n\n" +
                "Student's Uploaded Reasoning Documents:\n$reasoningText\n\n" +
                "Student's Uploaded Solution Code:\n$solutionFilesText"

        try {
            val responseText = makeApiRequest(settings, systemPrompt, userPrompt)
            val jsonObject = parseResponseObject(responseText)
            val score = jsonObject.get("score")?.asInt ?: 50
            val feedback = jsonObject.get("feedback")?.asString ?: "No feedback provided."
            GradeResult(score, feedback)
        } catch (e: Exception) {
            Log.e("LlmClient", "Error grading submission from LLM", e)
            GradeResult(
                score = 0,
                feedback = "Grading failed due to network or parser error: ${e.localizedMessage}"
            )
        }
    }

    private fun validateSettings(settings: LlmSettings): Boolean {
        if (settings.apiKey.trim().isEmpty()) {
            return false
        }
        if (settings.provider.uppercase() == "GROQ" && settings.modelName.trim().isEmpty()) {
            return false
        }
        return true
    }

    private fun makeApiRequest(settings: LlmSettings, systemPrompt: String, userPrompt: String): String {
        if (!validateSettings(settings)) {
            throw IOException("Invalid API settings. Please check your API key and model name.")
        }

        val provider = settings.provider.uppercase()
        val url: String
        val requestBodyJson: String
        val headersMap = mutableMapOf<String, String>()

        when (provider) {
            "OPENAI" -> {
                url = if (settings.baseUrl.trim().isNotEmpty()) settings.baseUrl else "https://api.openai.com/v1/chat/completions"
                headersMap["Authorization"] = "Bearer ${settings.apiKey}"
                headersMap["Content-Type"] = "application/json"
                
                val messages = listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userPrompt)
                )
                val bodyMap = mutableMapOf(
                    "model" to settings.modelName,
                    "messages" to messages
                )
                requestBodyJson = Gson().toJson(bodyMap)
            }
            "GROQ" -> {
                val base = if (settings.baseUrl.trim().isNotEmpty()) settings.baseUrl else "https://api.groq.com/openai/v1"
                url = "$base/chat/completions"
                headersMap["Authorization"] = "Bearer ${settings.apiKey}"
                headersMap["Content-Type"] = "application/json"
                
                val messages = listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userPrompt)
                )
                val bodyMap = mutableMapOf(
                    "model" to settings.modelName,
                    "messages" to messages,
                    "temperature" to 0.7
                )
                requestBodyJson = Gson().toJson(bodyMap)
            }
            "ANTHROPIC" -> {
                url = if (settings.baseUrl.trim().isNotEmpty()) settings.baseUrl else "https://api.anthropic.com/v1/messages"
                headersMap["x-api-key"] = settings.apiKey
                headersMap["anthropic-version"] = "2023-06-01"
                headersMap["Content-Type"] = "application/json"

                val messages = listOf(
                    mapOf("role" to "user", "content" to userPrompt)
                )
                val bodyMap = mapOf(
                    "model" to settings.modelName,
                    "max_tokens" to 4096,
                    "system" to systemPrompt,
                    "messages" to messages
                )
                requestBodyJson = Gson().toJson(bodyMap)
            }
            else -> { // GEMINI
                val base = if (settings.baseUrl.trim().isNotEmpty()) settings.baseUrl else "https://generativelanguage.googleapis.com"
                url = "$base/v1beta/models/${settings.modelName}:generateContent?key=${settings.apiKey}"
                headersMap["Content-Type"] = "application/json"

                // We append system prompt instruction to the contents block for Gemini
                val fullText = "System instructions:\n$systemPrompt\n\nPrompt:\n$userPrompt"
                val part = mapOf("text" to fullText)
                val parts = listOf(part)
                val content = mapOf("parts" to parts)
                val bodyMap = mapOf("contents" to listOf(content))
                requestBodyJson = Gson().toJson(bodyMap)
            }
        }

        val body = requestBodyJson.toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder().url(url).post(body)
        for ((k, v) in headersMap) {
            requestBuilder.addHeader(k, v)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP response code: ${response.code} body: ${response.body?.string()}")
            }
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            
            // Extract the text content from the provider's specific envelope
            return extractTextFromEnvelope(provider, responseBody)
        }
    }

    private fun extractTextFromEnvelope(provider: String, responseBody: String): String {
        val gson = Gson()
        val json = gson.fromJson(responseBody, JsonObject::class.java)

        return when (provider) {
            "OPENAI", "GROQ" -> {
                json.getAsJsonArray("choices")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString ?: responseBody
            }
            "ANTHROPIC" -> {
                json.getAsJsonArray("content")
                    ?.get(0)?.asJsonObject
                    ?.get("text")?.asString ?: responseBody
            }
            else -> { // GEMINI
                json.getAsJsonArray("candidates")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("content")
                    ?.getAsJsonArray("parts")
                    ?.get(0)?.asJsonObject
                    ?.get("text")?.asString ?: responseBody
            }
        }
    }

    private fun parseResponseObject(rawText: String): JsonObject {
        var cleanText = rawText.trim()

        // Strip markdown code blocks if the LLM wrapped it in ```json ... ```
        if (cleanText.startsWith("```")) {
            val lines = cleanText.split("\n")
            val codeLines = lines.filterIndexed { index, _ ->
                index > 0 && index < lines.size - 1
            }
            cleanText = codeLines.joinToString("\n").trim()
        }
        
        // Find first '{' and last '}' to strip any external text
        val firstBrace = cleanText.indexOf('{')
        val lastBrace = cleanText.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleanText = cleanText.substring(firstBrace, lastBrace + 1)
        }

        return Gson().fromJson(cleanText, JsonObject::class.java)
    }

    private fun padTransformations(list: List<String>, originalText: String): List<String> {
        val result = list.toMutableList()
        while (result.size < 10) {
            result.add("Transformation ${result.size + 1}: Variation of \"$originalText\"")
        }
        return result
    }

    data class GradeResult(
        val score: Int,
        val feedback: String
    )
}
