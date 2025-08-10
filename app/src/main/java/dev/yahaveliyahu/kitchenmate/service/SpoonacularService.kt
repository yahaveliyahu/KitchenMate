package dev.yahaveliyahu.kitchenmate.service

import android.util.Log
import dev.yahaveliyahu.kitchenmate.model.Recipe
import dev.yahaveliyahu.kitchenmate.model.RecipeMatch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import dev.yahaveliyahu.kitchenmate.BuildConfig
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

// A service that communicates with the Spoonacular API to find recipes
// that match the ingredients the user entered,
// in case there is no match in the recipe database in the firestore
object SpoonacularService : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    fun findMatchingRecipesFromSpoonacular(userIngredients: List<String>, onComplete: (List<RecipeMatch>) -> Unit) {
        launch {
            val matchedRecipes = mutableListOf<RecipeMatch>()
            try {
                val apiKey = BuildConfig.SPOONACULAR_API_KEY
                val ingredientsParam = URLEncoder.encode(userIngredients.joinToString(","), "UTF-8")
                val url = URL("https://api.spoonacular.com/recipes/findByIngredients?ingredients=$ingredientsParam&number=10&ranking=1&ignorePantry=true&apiKey=$apiKey")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)

                val deferredRecipes = (0 until jsonArray.length()).map { i ->
                    async {
                        val item = jsonArray.getJSONObject(i)
                        val id = item.getInt("id").toString()
                        val title = item.getString("title")
                        val image = item.optString("image", "")
                        val usedIngredients = item.optJSONArray("usedIngredients") ?: JSONArray()
                        val missedIngredients = item.optJSONArray("missedIngredients") ?: JSONArray()

                        val allIngredients = mutableListOf<String>()
                        for (j in 0 until usedIngredients.length()) {
                            allIngredients.add(usedIngredients.getJSONObject(j).getString("name"))
                        }
                        for (j in 0 until missedIngredients.length()) {
                            allIngredients.add(missedIngredients.getJSONObject(j).getString("name"))
                        }

                        // Parallel reading for more information
                        val fullInfoUrl = URL("https://api.spoonacular.com/recipes/$id/information?includeNutrition=false&apiKey=$apiKey")
                        val fullConnection = fullInfoUrl.openConnection() as HttpURLConnection
                        val fullResponse = fullConnection.inputStream.bufferedReader().use { it.readText() }

                        val instructions = JSONObject(fullResponse).optString("instructions", "")

                        if (instructions.isNullOrBlank()) {
                            null  // A recipe without preparation instructions will be skipped
                        } else {
                            val matcher = RecipeMatcher()
                            val recipe = Recipe(
                                id = id,
                                title = title,
                                ingredients = allIngredients,
                                instructions = instructions,
                                imageUrl = image,
                                category = "",
                                isLiked = false,
                                timestamp = null,
                                essentialIngredients = emptyList(),
                                isSpoonacular = true
                            )
                            matcher.calculateRecipeMatch(userIngredients, recipe)?.takeIf { it.missingIngredients.size <= 3 }

                        }
                    }
                }

                matchedRecipes += deferredRecipes.awaitAll().filterNotNull()

                withContext(Dispatchers.Main) {
                    val filtered = removeDuplicateSpoonacularRecipes(matchedRecipes)
                    onComplete(filtered.sortedByDescending { it.score })
                }

            } catch (e: Exception) {
                Log.e("Spoonacular", "❌ Error reading recipes from API", e)
                withContext(Dispatchers.Main) {
                    onComplete(emptyList())
                }
            }
        }
    }

    // Common suffixes that are removed from the name to identify duplicates
    private val suffixesToIgnore = listOf(
        "sandwich", "stew", "bowl", "wrap", "salad", "dish", "recipe", "plate", "serving", "sandwiches"
    )

    // Normalizes recipe name – removes common suffixes
    private fun normalizeTitle(title: String): String {
        var result = title.lowercase().trim()

        // Consolidation of common expressions
        result = result.replace("&", "and")
            .replace("/", " ")
            .replace("-", " ")
            .replace(Regex("[^a-z0-9 ]"), "") // Removes special characters
            .replace(Regex("\\s+"), " ") // Removes double spaces

        // Removing common suffixes (also in plural form)
        for (suffix in suffixesToIgnore) {
            if (result.endsWith(" $suffix")) {
                result = result.removeSuffix(" $suffix").trim()
            }
            if (result.endsWith(" ${suffix}s")) {
                result = result.removeSuffix(" ${suffix}s").trim()
            }
        }

        return result
    }

    // Calculates Levenshtein distance between two names
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[m][n]
    }

    // Checks whether two recipes are similar in image, ingredients and normalized name
    private fun areRecipesSimilar(r1: Recipe, r2: Recipe): Boolean {
        val image1 = r1.imageUrl.substringAfterLast("/")
        val image2 = r2.imageUrl.substringAfterLast("/")
        val sameImage = image1 == image2

        val ingredients1 = r1.ingredients.map { it.lowercase().trim() }.toSet()
        val ingredients2 = r2.ingredients.map { it.lowercase().trim() }.toSet()
        val sameIngredients = ingredients1 == ingredients2

        val title1 = normalizeTitle(r1.title)
        val title2 = normalizeTitle(r2.title)
        val titleDistance = levenshtein(title1, title2)
        val titlesSimilar = titleDistance <= 3

        return sameImage && sameIngredients && titlesSimilar
    }

    // Filter duplicates by content – only keeps unique recipes
    private fun removeDuplicateSpoonacularRecipes(recipes: List<RecipeMatch>): List<RecipeMatch> {
        val unique = mutableListOf<RecipeMatch>()

        for (candidate in recipes) {
            val isDuplicate = unique.any { existing ->
                areRecipesSimilar(existing.recipe, candidate.recipe)
            }

            if (!isDuplicate) {
                unique.add(candidate)
            }
        }

        return unique
    }
}






// Functions to upload recipes from Spoonacular API to firestore.
// I did this for one-time use, to enrich my recipe database so that I would have 100 recipes in firestore


//    private const val API_KEY = BuildConfig.SPOONACULAR_API_KEY
//    private val client = OkHttpClient()
//    private val db = FirebaseFirestore.getInstance()
//
//    fun importRecipes(context: Context, targetCount: Int = 100) {
//        val allRecipes = mutableListOf<Recipe>()
//        val allIngredients = mutableSetOf<String>()
//
//        fun fetchNextBatch(batchSize: Int = 10, onDone: () -> Unit) {
//            val url = "https://api.spoonacular.com/recipes/random?number=$batchSize&apiKey=$API_KEY&instructionsRequired=true&addRecipeInformation=true"
//            val request = Request.Builder().url(url).build()
//
//            client.newCall(request).enqueue(object : Callback {
//                override fun onFailure(call: Call, e: IOException) {
//                    Log.e("SpoonacularIntegration", "API call failed: ${e.message}")
//                    onDone()
//                }
//
//                override fun onResponse(call: Call, response: Response) {
//                    response.body?.string()?.let { json ->
//                        try {
//                            val obj = JSONObject(json)
//                            val recipesArray = obj.getJSONArray("recipes")
//                            for (i in 0 until recipesArray.length()) {
//                                val r = recipesArray.getJSONObject(i)
//                                val id = r.getInt("id").toString()
//                                val title = r.getString("title")
//                                val image = r.optString("image", "")
//                                val instructions = r.optString("instructions", "")
//                                val ingredientsArray = r.getJSONArray("extendedIngredients")
//                                val ingredients = mutableListOf<String>()
//                                for (j in 0 until ingredientsArray.length()) {
//                                    ingredients.add(ingredientsArray.getJSONObject(j).getString("name"))
//                                }
//                                allIngredients.addAll(ingredients)
//                                allRecipes.add(
//                                    Recipe(
//                                        id = id,
//                                        title = title,
//                                        imageUrl = image,
//                                        ingredients = ingredients,
//                                        instructions = instructions,
//                                        category = ""
//                                    )
//                                )
//                            }
//
//                            if (allRecipes.size < targetCount) {
//                                fetchNextBatch(batchSize, onDone)
//                            } else {
//                                onDone()
//                            }
//                        } catch (e: Exception) {
//                            Log.e("SpoonacularIntegration", "Parse error: ${e.message}")
//                            onDone()
//                        }
//                    }
//                }
//            })
//        }
//
//        fetchNextBatch {
//            allRecipes.forEach { recipe ->
//                db.collection("recipes").document(recipe.id).set(recipe)
//            }
//
// // Checking which new ingredients have been added relative to the existing commonIngredients list from the ManuallyEnteringIngredientsActivity class
//            val existingIngredients = ManuallyEnteringIngredientsActivity.Companion.commonIngredients.toSet()
//            val newIngredients = allIngredients.subtract(existingIngredients)
//
//            if (newIngredients.isNotEmpty()) {
//                val formatted = newIngredients.sorted().joinToString(",\n") { "\"$it\"" }
                // Log.d("Spoonacular", "\n\uD83D\uDC49 New ingredients to add to commonIngredients:\n$formatted")
//            } else {
//            Log.d("Spoonacular", "\uD83C\uDF89 No new ingredients – all ingredients already exist in commonIngredients")
//            }
//        }
//    }
//
//    fun fetchFromSpoonacularWithMissingSupport(
//        userIngredients: List<String>,
//        onComplete: (List<Recipe>) -> Unit
//    ) {
//        Thread {
//            try {
//                val ingredientsParam = URLEncoder.encode(userIngredients.joinToString(","), "UTF-8")
//                val url =
//                    URL("https://api.spoonacular.com/recipes/findByIngredients?ingredients=$ingredientsParam&number=10&ranking=1&ignorePantry=true&apiKey=$API_KEY")
//                val connection = url.openConnection() as HttpURLConnection
//                connection.requestMethod = "GET"
//
//                val response = connection.inputStream.bufferedReader().use { it.readText() }
//                val recipesJson = JSONArray(response)
//
//                val matchedRecipes = mutableListOf<Recipe>()
//                for (i in 0 until recipesJson.length()) {
//                    val obj = recipesJson.getJSONObject(i)
//                    val missedCount = obj.optJSONArray("missedIngredients")?.length() ?: 0
//                    if (missedCount <= 3) {
//                        val id = obj.getInt("id").toString()
//                        val title = obj.getString("title")
//                        val image = obj.optString("image", "")
//                        val usedIngredients = obj.optJSONArray("usedIngredients") ?: JSONArray()
//                        val missedIngredients = obj.optJSONArray("missedIngredients") ?: JSONArray()
//                        val allIngredients = mutableListOf<String>()
//
//                        for (j in 0 until usedIngredients.length()) {
//                            allIngredients.add(usedIngredients.getJSONObject(j).getString("name"))
//                        }
//                        for (j in 0 until missedIngredients.length()) {
//                            allIngredients.add(missedIngredients.getJSONObject(j).getString("name"))
//                        }
//
//                          // Additional fetch for instructions
//                         val fullUrl = URL("https://api.spoonacular.com/recipes/$id/information?includeNutrition=false&apiKey=$API_KEY")
//                        val fullConn = fullUrl.openConnection() as HttpURLConnection
//                        val fullResponse = fullConn.inputStream.bufferedReader().use { it.readText() }
//                        val fullJson = JSONObject(fullResponse)
//                        val instructions = fullJson.optString("instructions", "No instructions available.")
//
//                        matchedRecipes.add(
//                            Recipe(
//                                id = id,
//                                title = title,
//                                imageUrl = image,
//                                ingredients = allIngredients,
//                                instructions = instructions,
//                                category = ""
//                            )
//                        )
//                    }
//                }
//                onComplete(matchedRecipes)
//            } catch (e: Exception) {
//                Log.e("SpoonacularService", "API error: ${e.message}")
//                onComplete(emptyList())
//            }
//        }.start()
//    }
//
