package dev.yahaveliyahu.kitchenmate.model

import com.google.firebase.Timestamp
import java.io.Serializable

data class Recipe(
    var id: String = "",
    val title: String = "",
    val ingredients: List<String> = emptyList(),
    val instructions: String = "",
    val imageUrl: String = "",
    val category: String = "",
    var isLiked: Boolean = false,
    var timestamp: Timestamp? = null, // for RecentRecipesActivity
    val essentialIngredients: List<String> = emptyList(),
    val isSpoonacular: Boolean = false
) : Serializable // used for passing objects between activities or saving state