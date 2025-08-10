package dev.yahaveliyahu.kitchenmate.service

import dev.yahaveliyahu.kitchenmate.model.Recipe
import dev.yahaveliyahu.kitchenmate.model.RecipeMatch
import org.atteo.evo.inflector.English
import kotlin.collections.iterator


    // Responsible for the logic of matching the ingredients entered by the user to the ingredients required in the recipes
class RecipeMatcher {

    // Ingredient similarity mapping for flexible matching
    private val ingredientSimilarities = mapOf(
        "chicken breast" to listOf("chicken"),
        "ground beef" to listOf("beef"),
        "all-purpose flour" to listOf("flour"),
        "olive oil" to listOf("oil"),
        "vegetable oil" to listOf("oil"),
        "canola oil" to listOf("oil"),
        "parmesan cheese" to listOf("cheese", "parmesan"),
        "mozzarella cheese" to listOf("cheese", "mozzarella"),
        "cheddar cheese" to listOf("cheese", "cheddar"),
        "beef broth" to listOf("broth", "beef stock"),
        "chicken broth" to listOf("broth", "chicken stock"),
        "vegetable broth" to listOf("broth", "veggie stock"),
        "diced tomatoes" to listOf("tomato", "tomatoes"),
        "tomato sauce" to listOf("tomato", "tomato paste"),
        "marinara sauce" to listOf("tomato", "pasta sauce"),
        "green onions" to listOf("spring onion", "onion"),
        "yellow onion" to listOf("onion"),
        "white onion" to listOf("onion"),
        "onion" to listOf("green onions", "yellow onion", "white onion", "spring onion"),
        "breadcrumbs" to listOf("crumbs"),
        "bread slices" to listOf("bread"),
        "egg noodles" to listOf("noodles"),
        "sour cream" to listOf("cream"),
        "romaine lettuce" to listOf("lettuce"),
        "taco shells" to listOf("tortillas"),
        "croutons" to listOf("bread"),
        "sub rolls" to listOf("bread", "rolls"),
        "hamburger bun" to listOf("bread", "rolls"),
        "cream of mushroom soup" to listOf("mushroom soup"),
        "cream of chicken soup" to listOf("chicken soup"),
        "pecans" to listOf("nuts"),
        "granulated sugar" to listOf("sugar"),
        "all-purpose flour" to listOf("flour"),
        "chocolate chips" to listOf("chocolate"),
        "vanilla extract" to listOf("vanilla"),
        "lemon juice" to listOf("lemon"),
        "orange juice" to listOf("orange"),
        "whole milk" to listOf("milk"),
        "butter" to listOf("margarine", "oil"),
        "milk" to listOf("almond milk", "soy milk", "Oat milk"),
        "sugar" to listOf("honey", "maple syrup", "agave"),
        "egg" to listOf("egg whites", "egg yolk"),
        "garlic powder" to listOf("garlic"),
        "onion powder" to listOf("onion"),
        "vinegar" to listOf("lemon juice", "lime juice")
        )

        private fun hasEnoughEssential(recipe: Recipe, userIngredients: List<String>): Boolean {
            val normalizedUser = userIngredients.map { it.lowercase().trim() }
            val essentialCount = recipe.essentialIngredients.size
            val presentEssential = recipe.essentialIngredients.count { ess ->
                normalizedUser.any { user -> user.equals(ess, ignoreCase = true) }
            }

            return when {
                essentialCount <= 1 -> presentEssential == essentialCount // If there is only one, it must appear
                essentialCount <= 3 -> presentEssential >= essentialCount - 1 // There may be one missing
                else -> presentEssential >= essentialCount - 2 // If there are many essentials, maybe two are missing
            }
        }


        fun findMatchingRecipes( userIngredients: List<String>, allRecipes: List<Recipe>, minScore: Double = 0.0 ): List<RecipeMatch> {
        val normalizedUserIngredients = userIngredients.map { normalizeIngredient(it.lowercase().trim()) }
        return allRecipes.filter { hasEnoughEssential(it, normalizedUserIngredients) }
            .mapNotNull { recipe -> calculateRecipeMatch(normalizedUserIngredients, recipe) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
    }

    fun calculateRecipeMatch( userIngredientsRaw: List<String>, recipe: Recipe): RecipeMatch? {
        // Full normalization of the user list
        val userIngredients = userIngredientsRaw.map { normalizeIngredient(it.lowercase().trim()) }
        // Normalize each ingredient in the recipe
        val recipeIngredients = recipe.ingredients.map { normalizeIngredient(it.lowercase().trim()) }
        val matchedIngredients = mutableListOf<String>()
        val missingIngredients = mutableListOf<String>()

        for (recipeIngredient in recipeIngredients) {
            val isMatched = isIngredientMatched(recipeIngredient, userIngredients)
            if (isMatched) {
                matchedIngredients.add(recipeIngredient)
            } else {
                missingIngredients.add(recipeIngredient)
            }
        }

        // Early screening stage – if there are almost no matches or too many missing → we will not refund at all
        if (matchedIngredients.size < 2 || missingIngredients.size > 3) {
            return null
        }

        // Calculate base match percentage
        val matchPercentage = matchedIngredients.size.toDouble() / recipeIngredients.size
        // Apply penalties and bonuses
        var finalScore = matchPercentage
        // Penalty for recipes with too many missing ingredients
        if (missingIngredients.size > 3) finalScore -= 0.2
        // Ensure score is between 0 and 1
        finalScore = finalScore.coerceIn(0.0, 1.0)
        return RecipeMatch( // Create a RecipeMatch object with the calculated score and lists
            recipe = recipe,
            score = finalScore,
            matchedIngredients = matchedIngredients,
            missingIngredients = missingIngredients,
            userIngredients = userIngredients
        )
    }

    // Normalize ingredient by removing quantities, units, and common descriptors
    fun normalizeIngredient(ingredient: String): String {
        val result = ingredient
            .lowercase()
            .replace(Regex("\\d+\\s*(cup|cups|tablespoon|tablespoons|tbsp|tsp|teaspoon|teaspoons|oz|ounce|ounces|gram|grams|g|ml|milliliter|milliliters|liter|liters|l|pound|pounds|lb|lbs|kg|kilogram|kilograms)\\b"), "")
            .replace(Regex("\\d+/\\d+"), "")
            .replace(Regex("\\d+\\.\\d+"), "")
            .replace(Regex("\\b\\d+\\b"), "")
            .replace(Regex("\\b(fresh|dried|ground|chopped|diced|sliced|minced|crushed|grated|shredded|optional|to taste|beaten|boiled|baked|steamed|removed|scrambled|cooled|cooked|raw|organic|large|small|medium|extra virgin|peel|flakes|preferred|favorite|your|style|type of)\\b"), "")
            .replace(Regex("\\b(a|an|the|or)\\s+"), "")
            .replace(",", "")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return result
    }

    fun isIngredientMatched(recipeIngredient: String, userIngredients: List<String>): Boolean {

        // Direct match
        if (userIngredients.any { it.equals(recipeIngredient, ignoreCase = true) }) {
            return true
        }

        // Using the atteo-evo-inflector library, which provides tools for inflecting English words (for example, plural inflection here)
        val singularForm = English.plural(recipeIngredient, 1)
        val pluralForm = English.plural(recipeIngredient, 2)

        for (userIng in userIngredients) {
            val userSingular = English.plural(userIng, 1)
            val userPlural = English.plural(userIng, 2)

        if (userIng.equals(recipeIngredient, ignoreCase = true) ||
            userSingular.equals(recipeIngredient, ignoreCase = true) ||
            userPlural.equals(recipeIngredient, ignoreCase = true) ||
            recipeIngredient.equals(userSingular, ignoreCase = true) ||
            recipeIngredient.equals(userPlural, ignoreCase = true) ||
            userIng.equals(singularForm, ignoreCase = true) ||
            userIng.equals(pluralForm, ignoreCase = true)) {
            return true
        }
    }
        // Check similarity mappings
        ingredientSimilarities[recipeIngredient]?.let { similarIngredients ->
            if (similarIngredients.any { similar ->
                    userIngredients.any { it.equals(similar, ignoreCase = true) }
                }) return true
        }

        // Check reverse similarity mappings ONLY if the key has 2+ words (like "chicken broth")
        for ((key, values) in ingredientSimilarities) {
            if (key.trim().split(" ").size >= 2) {
                if (values.any { it.equals(recipeIngredient, ignoreCase = true) } &&
                    userIngredients.any { it.equals(key, ignoreCase = true) }) {
                    return true
                }
            }
        }

        // Check if either ingredient contains the other
        return userIngredients.any { ui ->
            containsWholeWord(recipeIngredient, ui) ||   // e.g., recipeIngredient="olive oil", ui="oil"
                    containsWholeWord(ui, recipeIngredient)      // reverse check: recipeIngredient="oil", ui="olive oil"
        }
    }

        // Whole word check
        private fun containsWholeWord(haystack: String, needle: String): Boolean {
            if (needle.isBlank()) return false
            val re = Regex("""(?i)(^|[^\p{L}\p{N}])${Regex.escape(needle)}($|[^\p{L}\p{N}])""")
            return re.containsMatchIn(haystack)
        }
    }