package com.example.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.DailyGoal
import com.example.data.model.FoodLogEntry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class FoodEstimationResult(
    val foodName: String,
    val servingDescription: String,
    val estimatedGrams: Double,
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double,
    val notes: String
)

data class RecipeAnalysisResult(
    val dishName: String,
    val totalServings: Double,
    val servingSizeDescription: String = "1 serving",
    val servingWeightGrams: Double = 100.0,
    val detectedIngredients: List<String>,
    val perServingCalories: Double,
    val perServingProteinGrams: Double,
    val perServingCarbsGrams: Double,
    val perServingFatGrams: Double,
    val perServingFiberGrams: Double,
    val perServingSugarGrams: Double = 0.0,
    val perServingSodiumMg: Double = 0.0,
    val isNutritionLabel: Boolean = false,
    val macroSummary: String,
    val healthRatingNotes: String
)

data class AiNutritionalInsight(
    val dailyGrade: String,
    val summary: String,
    val macroAnalysis: String,
    val smartRecommendations: List<String>,
    val tipForTheDay: String
)

class GeminiNutritionService {
    companion object {
        private const val TAG = "GeminiNutritionService"
        private const val TIMEOUT_MS = 60_000L
    }

    // Schema for food description search
    private val foodSearchSchema = Schema.obj(
        properties = mapOf(
            "foodName" to Schema.string("Clear, concise food name"),
            "servingDescription" to Schema.string("Description of portion or serving size, e.g., 1 bowl (350g)"),
            "estimatedGrams" to Schema.double("Total estimated weight in grams"),
            "calories" to Schema.double("Total estimated calories in kcal"),
            "proteinGrams" to Schema.double("Protein in grams"),
            "carbsGrams" to Schema.double("Total carbohydrates in grams"),
            "fatGrams" to Schema.double("Total fat in grams"),
            "fiberGrams" to Schema.double("Dietary fiber in grams"),
            "notes" to Schema.string("Brief nutritional takeaway or ingredient assumptions")
        )
    )

    // Schema for recipe, meal photo, or nutrition label analysis
    private val recipeAnalysisSchema = Schema.obj(
        properties = mapOf(
            "dishName" to Schema.string("Name of the product, food item, recipe, or meal identified"),
            "totalServings" to Schema.double("Number of servings per container or in the recipe/image"),
            "servingSizeDescription" to Schema.string("Household serving size description, e.g., '1 cup (228g)', '2 cookies (30g)', or '1 bowl'"),
            "servingWeightGrams" to Schema.double("Weight of 1 single serving in grams (default 100 if unknown)"),
            "detectedIngredients" to Schema.array(
                items = Schema.string("Ingredient or key nutrient item with quantity"),
                description = "Identified ingredients or nutrition panel highlights"
            ),
            "perServingCalories" to Schema.double("Calories per 1 serving (kcal)"),
            "perServingProteinGrams" to Schema.double("Protein per 1 serving in grams"),
            "perServingCarbsGrams" to Schema.double("Total Carbohydrates per 1 serving in grams"),
            "perServingFatGrams" to Schema.double("Total Fat per 1 serving in grams"),
            "perServingFiberGrams" to Schema.double("Dietary Fiber per 1 serving in grams"),
            "perServingSugarGrams" to Schema.double("Total Sugars per 1 serving in grams"),
            "perServingSodiumMg" to Schema.double("Sodium per 1 serving in milligrams (mg)"),
            "isNutritionLabel" to Schema.boolean("True if the analyzed image is a Nutrition Facts label/table on a food package"),
            "macroSummary" to Schema.string("Key macro highlights (e.g. High Protein, Low Sugar, 240 kcal per 55g serving)"),
            "healthRatingNotes" to Schema.string("Helpful calculation note or dietary observation")
        )
    )

    // Schema for personalized insights
    private val insightsSchema = Schema.obj(
        properties = mapOf(
            "dailyGrade" to Schema.string("Status, e.g., Excellent Balance, High Protein, Fiber Deficit, High Sodium"),
            "summary" to Schema.string("2-3 sentence personalized summary of nutritional progress"),
            "macroAnalysis" to Schema.string("Detailed breakdown of calories and macronutrients vs targets"),
            "smartRecommendations" to Schema.array(
                items = Schema.string("Practical actionable snack or food suggestion"),
                description = "2-3 targeted food ideas to balance today's intake"
            ),
            "tipForTheDay" to Schema.string("Actionable, science-backed nutrition or hydration tip")
        )
    )

    private val searchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = foodSearchSchema
            }
        )
    }

    private val recipeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recipeAnalysisSchema
            }
        )
    }

    private val insightsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = insightsSchema
            }
        )
    }

    suspend fun estimateFoodFromDescription(description: String): Result<FoodEstimationResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are an expert clinical dietitian and nutritional database.
                Accurately estimate the nutritional content, macronutrients (Calories, Protein, Carbs, Fat, Fiber), and gram weight for the following food or meal description:
                "$description"
                Calculate realistic values based on USDA / standard nutritional reference data.
            """.trimIndent()

            val response = withTimeout(TIMEOUT_MS) {
                searchModel.generateContent(prompt)
            }
            val text = response.text ?: throw IllegalStateException("Model returned empty text.")
            val json = JSONObject(text)

            val result = FoodEstimationResult(
                foodName = json.getString("foodName"),
                servingDescription = json.optString("servingDescription", "1 serving"),
                estimatedGrams = json.optDouble("estimatedGrams", 100.0),
                calories = json.optDouble("calories", 0.0),
                proteinGrams = json.optDouble("proteinGrams", 0.0),
                carbsGrams = json.optDouble("carbsGrams", 0.0),
                fatGrams = json.optDouble("fatGrams", 0.0),
                fiberGrams = json.optDouble("fiberGrams", 0.0),
                notes = json.optString("notes", "")
            )
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Search timed out", e)
            Result.failure(Exception("Food search timed out. Please check network connection."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeRecipeOrIngredients(
        recipeText: String?,
        imageBitmap: Bitmap?,
        isLabelModeHint: Boolean = false
    ): Result<RecipeAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            if (recipeText.isNullOrBlank() && imageBitmap == null) {
                return@withContext Result.failure(IllegalArgumentException("Please provide a photo or recipe text."))
            }

            val response = withTimeout(TIMEOUT_MS) {
                if (imageBitmap != null) {
                    recipeModel.generateContent(
                        content {
                            image(imageBitmap)
                            val userText = when {
                                isLabelModeHint -> {
                                    val extra = if (!recipeText.isNullOrBlank()) " User notes / product name / portion eaten: $recipeText." else ""
                                    """
                                    Analyze this image of a Nutrition Facts label (or food packaging).$extra
                                    1. Extract the exact Serving Size description (e.g. '2/3 cup (55g)') and numeric gram weight per serving (servingWeightGrams).
                                    2. Extract Servings Per Container (totalServings).
                                    3. Read the exact Per-Serving Calories, Total Fat, Total Carbohydrates, Dietary Fiber, Total Sugars, Protein, and Sodium (mg).
                                    4. If the user notes specify a custom amount eaten, still return the standard 1-serving values in perServing* fields and note the calculation in healthRatingNotes.
                                    5. Set isNutritionLabel = true if this is a nutrition label or package panel.
                                    """.trimIndent()
                                }
                                !recipeText.isNullOrBlank() -> {
                                    """
                                    Analyze this image (which may be a Nutrition Facts label, raw ingredients, or a prepared meal) along with user notes: "$recipeText".
                                    If it is a Nutrition Facts label, read the exact serving size, gram weight, calories, protein, carbs, fat, fiber, sugar, and sodium from the label and set isNutritionLabel = true.
                                    If it is a meal or ingredients photo, identify the dish, detect all visible ingredients, estimate total servings and gram weight per serving, and calculate per-serving macros.
                                    """.trimIndent()
                                }
                                else -> {
                                    """
                                    Analyze this image. First determine if it is a Nutrition Facts label on a food item OR a photo of ingredients / prepared meal:
                                    - If it is a Nutrition Facts label: extract the exact Serving Size, gram weight per serving, servings per container, and exact per-serving Calories, Protein, Total Carbs, Total Fat, Fiber, Sugars, and Sodium, and set isNutritionLabel = true.
                                    - If it is a meal or ingredients photo: identify the dish, detect visible ingredients, estimate total servings and gram weight per serving, and calculate the per-serving macro breakdown, setting isNutritionLabel = false.
                                    """.trimIndent()
                                }
                            }
                            text(userText)
                        }
                    )
                } else {
                    recipeModel.generateContent(
                        """
                        Analyze the following recipe, nutrition label text, or ingredients list:
                        "$recipeText"
                        Estimate total servings, serving weight in grams, and provide exact per-serving macronutrient breakdown (calories, protein, carbs, fat, fiber, sugar, sodium).
                        """.trimIndent()
                    )
                }
            }

            val text = response.text ?: throw IllegalStateException("Model returned empty text.")
            val json = JSONObject(text)

            val ingredientsArray = json.optJSONArray("detectedIngredients")
            val ingredients = mutableListOf<String>()
            if (ingredientsArray != null) {
                for (i in 0 until ingredientsArray.length()) {
                    ingredients.add(ingredientsArray.getString(i))
                }
            }

            val result = RecipeAnalysisResult(
                dishName = json.optString("dishName", "Scanned Food Item"),
                totalServings = json.optDouble("totalServings", 1.0).coerceAtLeast(0.1),
                servingSizeDescription = json.optString("servingSizeDescription", "1 serving (100g)"),
                servingWeightGrams = json.optDouble("servingWeightGrams", 100.0).let { if (it <= 0.0) 100.0 else it },
                detectedIngredients = ingredients,
                perServingCalories = json.optDouble("perServingCalories", 0.0),
                perServingProteinGrams = json.optDouble("perServingProteinGrams", 0.0),
                perServingCarbsGrams = json.optDouble("perServingCarbsGrams", 0.0),
                perServingFatGrams = json.optDouble("perServingFatGrams", 0.0),
                perServingFiberGrams = json.optDouble("perServingFiberGrams", 0.0),
                perServingSugarGrams = json.optDouble("perServingSugarGrams", 0.0),
                perServingSodiumMg = json.optDouble("perServingSodiumMg", 0.0),
                isNutritionLabel = json.optBoolean("isNutritionLabel", isLabelModeHint),
                macroSummary = json.optString("macroSummary", "Nutrient analysis complete"),
                healthRatingNotes = json.optString("healthRatingNotes", "")
            )
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Analysis timed out", e)
            Result.failure(Exception("Image/recipe analysis timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateNutritionalInsights(
        goal: DailyGoal,
        loggedFoods: List<FoodLogEntry>,
        waterIntakeMl: Int
    ): Result<AiNutritionalInsight> = withContext(Dispatchers.IO) {
        try {
            val totalCalories = loggedFoods.sumOf { it.calories }
            val totalProtein = loggedFoods.sumOf { it.proteinGrams }
            val totalCarbs = loggedFoods.sumOf { it.carbsGrams }
            val totalFat = loggedFoods.sumOf { it.fatGrams }
            val totalFiber = loggedFoods.sumOf { it.fiberGrams }

            val foodListSummary = if (loggedFoods.isEmpty()) {
                "No foods logged yet today."
            } else {
                loggedFoods.joinToString("\n") {
                    "- [${it.mealType.displayName}] ${it.foodName} (${it.numberOfServings} x ${it.servingSize}${it.servingUnit}): ${it.calories.toInt()} kcal, P:${it.proteinGrams.toInt()}g, C:${it.carbsGrams.toInt()}g, F:${it.fatGrams.toInt()}g, Fiber:${it.fiberGrams.toInt()}g"
                }
            }

            val prompt = """
                You are a supportive, insightful sports nutritionist. Analyze the user's daily food intake against their target goals:

                Daily Targets:
                - Calories: ${goal.calorieTarget} kcal
                - Protein: ${goal.proteinTargetGrams} g
                - Carbs: ${goal.carbsTargetGrams} g
                - Fat: ${goal.fatTargetGrams} g
                - Fiber: ${goal.fiberTargetGrams} g
                - Water: ${goal.waterTargetMl} ml

                Today's Actual Totals So Far:
                - Calories: ${totalCalories.toInt()} kcal (${goal.calorieTarget - totalCalories.toInt()} kcal remaining)
                - Protein: ${totalProtein.toInt()} g (${goal.proteinTargetGrams - totalProtein.toInt()} g remaining)
                - Carbs: ${totalCarbs.toInt()} g (${goal.carbsTargetGrams - totalCarbs.toInt()} g remaining)
                - Fat: ${totalFat.toInt()} g (${goal.fatTargetGrams - totalFat.toInt()} g remaining)
                - Fiber: ${totalFiber.toInt()} g (${goal.fiberTargetGrams - totalFiber.toInt()} g remaining)
                - Water Logged: $waterIntakeMl ml

                Meals Logged Today:
                $foodListSummary

                Provide an objective, motivating analysis, highlight macro balance, and recommend 2-3 specific snack/meal ideas tailored to hit any remaining protein, fiber, or calorie goals without overshooting.
            """.trimIndent()

            val response = withTimeout(TIMEOUT_MS) {
                insightsModel.generateContent(prompt)
            }
            val text = response.text ?: throw IllegalStateException("Model returned empty text.")
            val json = JSONObject(text)

            val recsArray = json.optJSONArray("smartRecommendations")
            val recs = mutableListOf<String>()
            if (recsArray != null) {
                for (i in 0 until recsArray.length()) {
                    recs.add(recsArray.getString(i))
                }
            }

            val insight = AiNutritionalInsight(
                dailyGrade = json.optString("dailyGrade", "Balanced Progress"),
                summary = json.optString("summary", "You are making steady progress today!"),
                macroAnalysis = json.optString("macroAnalysis", "Macros are progressing towards your daily goals."),
                smartRecommendations = recs,
                tipForTheDay = json.optString("tipForTheDay", "Stay hydrated and prioritize whole nutrient-dense foods.")
            )
            Result.success(insight)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Insights timed out", e)
            Result.failure(Exception("Insights generation timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Insights generation failed", e)
            Result.failure(e)
        }
    }
}
