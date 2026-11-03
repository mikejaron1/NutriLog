package com.example.data.remote

import android.util.Log
import com.example.data.model.FoodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenFoodFactsService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "OpenFoodFactsService"
    }

    suspend fun getProductByBarcode(barcode: String): FoodItem? = withContext(Dispatchers.IO) {
        val sanitizedBarcode = barcode.trim()
        if (sanitizedBarcode.isEmpty()) return@withContext null

        val url = "https://world.openfoodfacts.org/api/v2/product/$sanitizedBarcode.json"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NutriLog - Android - Version 1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "OpenFoodFacts API error: ${response.code}")
                    return@withContext null
                }

                val bodyString = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyString)
                val status = json.optInt("status", 0)
                if (status != 1) {
                    Log.d(TAG, "Product not found in OpenFoodFacts for barcode: $sanitizedBarcode")
                    return@withContext null
                }

                val product = json.getJSONObject("product")
                val productName = product.optString("product_name").ifBlank {
                    product.optString("generic_name").ifBlank { "Scanned Food Item" }
                }
                val brand = product.optString("brands").ifBlank { "Brand" }

                // Nutriments object
                val nutriments = product.optJSONObject("nutriments") ?: JSONObject()

                // Check serving or 100g
                val servingQuantity = product.optDouble("serving_quantity", 100.0)
                val servingUnit = product.optString("serving_size").takeIf { it.isNotBlank() } ?: "g"

                // Try serving first, fallback to 100g
                val calories = nutriments.optDouble("energy-kcal_serving",
                    nutriments.optDouble("energy-kcal_100g",
                        nutriments.optDouble("energy-kcal", 0.0)))

                val protein = nutriments.optDouble("proteins_serving",
                    nutriments.optDouble("proteins_100g",
                        nutriments.optDouble("proteins", 0.0)))

                val carbs = nutriments.optDouble("carbohydrates_serving",
                    nutriments.optDouble("carbohydrates_100g",
                        nutriments.optDouble("carbohydrates", 0.0)))

                val fat = nutriments.optDouble("fat_serving",
                    nutriments.optDouble("fat_100g",
                        nutriments.optDouble("fat", 0.0)))

                val fiber = nutriments.optDouble("fiber_serving",
                    nutriments.optDouble("fiber_100g",
                        nutriments.optDouble("fiber", 0.0)))

                return@withContext FoodItem(
                    name = productName,
                    brand = brand,
                    servingSize = if (servingQuantity > 0) servingQuantity else 100.0,
                    servingUnit = "g",
                    calories = if (calories >= 0) calories else 0.0,
                    proteinGrams = if (protein >= 0) protein else 0.0,
                    carbsGrams = if (carbs >= 0) carbs else 0.0,
                    fatGrams = if (fat >= 0) fat else 0.0,
                    fiberGrams = if (fiber >= 0) fiber else 0.0,
                    barcode = sanitizedBarcode,
                    source = "BARCODE"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch barcode info from OpenFoodFacts", e)
            null
        }
    }
}
