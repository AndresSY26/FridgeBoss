package com.example.gemini

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Gemini API Request Models ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiSchema(
    @Json(name = "type") val type: String,
    @Json(name = "properties") val properties: Map<String, GeminiSchema>? = null,
    @Json(name = "required") val required: List<String>? = null,
    @Json(name = "items") val items: GeminiSchema? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float = 0.7f,
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "responseSchema") val responseSchema: GeminiSchema? = null
)

// --- Gemini API Response Models ---

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

// --- OCR Schema and Results Decoders ---

@JsonClass(generateAdapter = true)
data class OcrReceiptResult(
    @Json(name = "establecimiento") val establecimiento: String,
    @Json(name = "fecha_compra") val fechaCompra: String,
    @Json(name = "total_pagado") val totalPagado: Double,
    @Json(name = "productos") val productos: List<OcrProduct>
)

@JsonClass(generateAdapter = true)
data class OcrProduct(
    @Json(name = "nombre") val nombre: String,
    @Json(name = "cantidad") val cantidad: Double,
    @Json(name = "precio_unitario") val precioUnitario: Double,
    @Json(name = "unidad") val unidad: String,
    @Json(name = "categoria") val categoria: String,
    @Json(name = "dias_vida_estimados") val diasVidaEstimados: Int
)

// --- Retrofit Service ---

interface GeminiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// --- Retrofit Client Singleton ---

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // Configure 60-second timeouts as mandated in SKILL.md for AI content generation
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val moshi: Moshi = Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    val service: GeminiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiService::class.java)
    }

    private fun getReceiptResponseSchema(): GeminiSchema {
        return GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "establecimiento" to GeminiSchema(type = "STRING"),
                "fecha_compra" to GeminiSchema(type = "STRING"),
                "total_pagado" to GeminiSchema(type = "NUMBER"),
                "productos" to GeminiSchema(
                    type = "ARRAY",
                    items = GeminiSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "nombre" to GeminiSchema(type = "STRING"),
                            "cantidad" to GeminiSchema(type = "NUMBER"),
                            "unidad" to GeminiSchema(type = "STRING"),
                            "precio_unitario" to GeminiSchema(type = "NUMBER"),
                            "categoria" to GeminiSchema(type = "STRING"),
                            "dias_vida_estimados" to GeminiSchema(type = "INTEGER")
                        ),
                        required = listOf("nombre", "cantidad", "unidad", "precio_unitario", "categoria", "dias_vida_estimados")
                    )
                )
            ),
            required = listOf("establecimiento", "fecha_compra", "total_pagado", "productos")
        )
    }

    private fun scaleBitmapDown(bitmap: android.graphics.Bitmap, maxDimension: Int): android.graphics.Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / aspectRatio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * aspectRatio).toInt()
        }
        return android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Process receipt using Multimodal prompt and Structured Output JSON Schema.
     */
    suspend fun processReceiptWithIa(imageBitmap: android.graphics.Bitmap): OcrReceiptResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key missing. Por favor configure GEMINI_API_KEY en el panel de secretos.")
        }

        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = "Analiza este ticket de compra. Extrae los productos, limpia sus nombres a formato legible, clasifícalos en sus categorías correctas y calcula cuántos días de vida útil les quedan estimados (ej: tomates = 4 días, papas = 10 días) antes de caducar."
                )
            )
        )

        // Rescale bitmap safely to prevent OOM
        val scaledBitmap = try {
            scaleBitmapDown(imageBitmap, 1024)
        } catch (t: Throwable) {
            android.util.Log.e("GeminiOcr", "Error resizing image, using original", t)
            imageBitmap
        }

        // Convert bitmap to base64 safely catching any Error or Exception
        val base64Image = try {
            val outputStream = ByteArrayOutputStream()
            // Compress the image to a lower quality if it's too big to avoid hitting payload limits
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            android.util.Log.e("GeminiOcr", "Error compressing or encoding image bitmap", t)
            throw IllegalArgumentException("Fallo al convertir la imagen a Base64: ${t.localizedMessage}", t)
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = "Por favor, analiza este ticket de compra y extrae los productos en formato JSON."),
                        GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.2f,
                responseMimeType = "application/json",
                responseSchema = getReceiptResponseSchema()
            ),
            systemInstruction = systemInstruction
        )

        val responseText = try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IllegalStateException("La respuesta devuelta por la API de Gemini no contiene texto.")
        } catch (t: Throwable) {
            android.util.Log.e("GeminiOcr", "Error calling Gemini generateContent API", t)
            throw IllegalStateException("Error al invocar el motor de IA de Gemini: ${t.localizedMessage}", t)
        }

        // Parse using Moshi safely with detailed fallback / error parsing
        try {
            val adapter = moshi.adapter(OcrReceiptResult::class.java)
            adapter.fromJson(responseText) ?: throw IllegalStateException("Moshi devolvió un objeto deserializado nulo")
        } catch (t: Throwable) {
            android.util.Log.e("GeminiOcr", "Error parsing Gemini structured JSON response: $responseText", t)
            throw IllegalStateException("Error al decodificar la información devuelta por la IA: ${t.localizedMessage}", t)
        }
    }

    /**
     * Helper function to generate content. Extracts key safely and calls the API.
     */
    suspend fun generateRecipe(ingredientsText: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "ERROR_API_KEY_MISSING"
        }

        val prompt = """
            Eres FridgeBoss, un chef profesional con gran sazón hogareña, especializado en desperdicio cero y cocina rescatadora de alimentos.
            
            Tengo estos ingredientes por vencer:
            $ingredientsText
            
            Sugiere una receta creativa, rápida y con sabor casero (estilo colombiano si aplica). Dime qué otros ingredientes básicos de despensa podría necesitar (sal, aceite, etc.).
            
            El output debe ser en ESPAÑOL, amigable, claro e inspirador. Sigue estrictamente este formato de salida estructurado en Markdown:

            **Título de la Receta**
            *Nombre de la receta aquí*

            **Tiempo de Preparación y Dificultad**
            *Tiempo: XX min | Dificultad: fácil/medio*

            **Ingredientes Utilizados de la Nevera**
            - Listado de ingredientes de nevera aprovechados...

            **Ingredientes Básicos de Despensa Adicionales**
            - Listado de básicos necesarios...

            **Instrucciones Paso a Paso**
            1. Primer paso...
            2. Segundo paso...

            **Tip Antibasura (Zero Waste Tip)**
            *Un consejo sumamente práctico sobre cómo conservar mejor estos ingredientes o el plato final para evitar el desperdicio.*
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
        )

        val response = service.generateContent(apiKey, request)
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: "No se pudo generar la receta. Intenta de nuevo más tarde."
    }
}
