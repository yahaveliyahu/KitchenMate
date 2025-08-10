package dev.yahaveliyahu.kitchenmate.ui.recipes

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.RecipeAdapter
import dev.yahaveliyahu.kitchenmate.model.Recipe
import dev.yahaveliyahu.kitchenmate.model.RecipeMatch
import kotlin.collections.iterator

class FavoriteRecipesActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var progressBar: ProgressBar
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var titleText: TextView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("FavoritesDebug", "onCreate started")
        setContentView(R.layout.activity_favorite_recipes)

        container = findViewById(R.id.recyclerViewFavorites)
        progressBar = findViewById(R.id.favorite_recipes_PROGRESS)
        titleText = findViewById(R.id.favorite_recipes_LBL_title)
        emptyText = findViewById(R.id.favorite_recipes_LBL_empty)

        loadFavorites()
    }

    private fun loadFavorites() {

        Log.d("FavoritesDebug", "Start loadFavorites")

        progressBar.isVisible = true
        val userId = auth.currentUser?.uid ?: return.also {
            Log.e("FavoritesDebug", "User not logged in")
        }

        db.collection("users")
            .document(userId)
            .collection("favorites")
            .get()
            .addOnSuccessListener { result ->
                progressBar.isVisible = false

                val validRecipes = mutableListOf<Recipe>()
                var pending = result.size()

                if (pending == 0) {
                    emptyText.isVisible = true
                    container.removeAllViews()
                    return@addOnSuccessListener
                }

                for (doc in result.documents) {
                    val recipeId = doc.id

                    db.collection("recipes").document(recipeId).get()
                        .addOnSuccessListener { recipeSnap ->
                            if (recipeSnap.exists()) {
                                val recipe = recipeSnap.toObject(Recipe::class.java)
                                recipe?.let {
                                    it.id = recipeId
                                    it.isLiked = true
                                    validRecipes.add(it)
                                }
                            } else {
                                // If the recipe is deleted – we will remove it from favorites
                                db.collection("users")
                                    .document(userId)
                                    .collection("favorites")
                                    .document(recipeId)
                                    .delete()
                                    .addOnSuccessListener {
                                        Log.d("FavoritesDebug", "Removed deleted recipe from favorites: $recipeId")
                                    }
                            }

                            pending--
                            if (pending == 0) displayFavorites(validRecipes)
                        }
                        .addOnFailureListener {
                            Log.e("FavoritesDebug", "Failed to fetch recipe $recipeId")
                            pending--
                            if (pending == 0) displayFavorites(validRecipes)
                        }
                }
            }

            .addOnFailureListener { e ->
                progressBar.isVisible = false
                Log.e("FavoritesDebug", "Error loading favorites", e)
                Toast.makeText(this, "Failed to load favorites", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayFavorites(recipes: List<Recipe>) {
        if (recipes.isEmpty()) {
            emptyText.isVisible = true
            container.removeAllViews()
            return
        }

        emptyText.isVisible = false
        titleText.isVisible = true
        container.removeAllViews()

        val categorized = recipes.groupBy { it.category }

        for ((category, recipesInCategory) in categorized) {
            if (recipesInCategory.isEmpty()) continue

            val recipeViews = mutableListOf<RecyclerView>()

            val categoryTitle = TextView(this).apply {
                text = category
                textSize = 20f
                setTextColor(ContextCompat.getColor(this@FavoriteRecipesActivity, android.R.color.black))
                setPadding(24, 40, 24, 24)
                setBackgroundColor(ContextCompat.getColor(this@FavoriteRecipesActivity, R.color.amber_800))
            }

            container.addView(categoryTitle)
            categoryTitle.setOnClickListener {
                for (view in recipeViews) {
                    view.isVisible = !view.isVisible
                }
            }

            for (match in recipesInCategory.map {
                RecipeMatch(
                    recipe = it,
                    matchedIngredients = emptyList(),
                    missingIngredients = emptyList(),
                    score = 0.0,
                    userIngredients = emptyList()
                )
            }) {
                val adapter = RecipeAdapter(listOf(match), highlightIngredients = false) {
                    val intent = Intent(this@FavoriteRecipesActivity, RecipeDetailsActivity::class.java)
                    intent.putExtra("recipeId", match.recipe.id)
                    intent.putStringArrayListExtra("userIngredients", ArrayList(emptyList<String>()))
                    startActivity(intent)
                }

                val recyclerView = RecyclerView(this).apply {
                    layoutManager = LinearLayoutManager(this@FavoriteRecipesActivity)
                    this.adapter = adapter
                    overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                    isNestedScrollingEnabled = false
                }

                recipeViews.add(recyclerView)
                container.addView(recyclerView)
            }
        }
    }
}