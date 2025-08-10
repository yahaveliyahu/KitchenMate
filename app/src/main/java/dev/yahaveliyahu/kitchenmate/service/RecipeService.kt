package dev.yahaveliyahu.kitchenmate.service

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import dev.yahaveliyahu.kitchenmate.model.RecipeMatch
import dev.yahaveliyahu.kitchenmate.model.Recipe


class RecipeService {

    /*
     * Loads recipes from Firebase and adapts them according to the user's list of ingredients.
     * The result will be returned via the onComplete callback
     */

    private val recipeMatcher = RecipeMatcher() // Instance of RecipeMatcher to find matching recipes

        // Boolean indicates if there is a perfect match
    fun findMatchingRecipesFromFirebase( userIngredients: List<String>, onComplete: (List<RecipeMatch>, Boolean) -> Unit ) {
        FirebaseFirestore.getInstance().collection("recipes")
            .get()
            .addOnSuccessListener { snapshot ->
                val allRecipes = snapshot.documents.mapNotNull { doc ->
                    try {
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val ingredients = (doc["ingredients"] as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
                        val instructions = doc.getString("instructions") ?: ""
                        val imageUrl = doc.getString("imageUrl") ?: ""
                        val category = doc.getString("category") ?: ""
                        val essentialIngredients = (doc["essentialIngredients"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                        Recipe(
                            id = doc.id,
                            title = title,
                            ingredients = ingredients,
                            instructions = instructions,
                            imageUrl = imageUrl,
                            category = category,
                            essentialIngredients = essentialIngredients
                        )
                    } catch (e: Exception) {
                        Log.e("RecipeService", "❌ Error loading recipe: ${e.message}")
                        null
                    }
                }

                val matches = recipeMatcher.findMatchingRecipes(
                    userIngredients = userIngredients,
                    allRecipes = allRecipes
                )

                // Filter: Only recipes with 3 or fewer missing ingredients
                val filteredMatches = matches.filter { it.missingIngredients.size <= 3 }
                    .distinctBy { it.recipe.id }

                val fullMatches = matches.filter { it.missingIngredients.isEmpty() }

                // A recipe will be displayed if:
                // 1. There is at least one match, or
                // 2. At most 3 ingredients are missing and the score is greater than 0
                val limitedMissing = filteredMatches.filter { it.matchedIngredients.isNotEmpty() || it.score > 0.0 }

                val result = (fullMatches + limitedMissing)
                    .distinctBy { it.recipe.id }
                    .sortedByDescending { it.score }

                val hasPerfectMatch = fullMatches.isNotEmpty()
                // If the result is too poor, we will also search on Spoonacular
                if (result.size < 3) {
                    SpoonacularService.findMatchingRecipesFromSpoonacular(userIngredients) { spoonacularResults ->
                        val finalResult = (result + spoonacularResults)
                            .filter { it.missingIngredients.size <= 3 }
                            .distinctBy { it.recipe.title }
                            .sortedByDescending { it.score }
                        onComplete(finalResult, hasPerfectMatch)
                    }
                } else {
                    onComplete(result, hasPerfectMatch)
                }
            }
            .addOnFailureListener {
                Log.e("RecipeService", "Failed to load recipes: ${it.message}")
                onComplete(emptyList(), false)
            }
    }
}