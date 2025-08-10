package dev.yahaveliyahu.kitchenmate.model

data class RecipeMatch(
    val recipe: Recipe,
    val matchedIngredients: List<String>,
    val missingIngredients: List<String>,
    val score: Double,
    val userIngredients: List<String>
)