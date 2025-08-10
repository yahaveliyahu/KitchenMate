package dev.yahaveliyahu.kitchenmate.ui.recipes

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.RecipeAdapter
import dev.yahaveliyahu.kitchenmate.model.Recipe
import dev.yahaveliyahu.kitchenmate.model.RecipeMatch
import kotlin.collections.iterator

class AllRecipesActivity : AppCompatActivity() {

    private lateinit var allRecipesContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var searchBar: AutoCompleteTextView
    private lateinit var clearButton: ImageView
    private lateinit var userIngredients: List<String>
    private lateinit var noRecipesText: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_recipes)

        allRecipesContainer = findViewById(R.id.all_recipes_RECYCLER)
        progressBar = findViewById(R.id.all_recipes_PROGRESS)
        searchBar = findViewById(R.id.search_bar)
        clearButton = findViewById(R.id.clear_button)
        noRecipesText = findViewById(R.id.no_recipes_TEXT)
        userIngredients = intent.getStringArrayListExtra("userIngredients") ?: emptyList()

        setupAutocompleteSuggestions()
        loadAllRecipesByCategory(userIngredients)

    searchBar.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            val query = s.toString().trim()
            noRecipesText.isVisible = false // Hiding the message
            allRecipesContainer.removeAllViews() // Clearing the display of previous recipes (so that they don't show an error for a moment)

            if (query.isEmpty()) {
                loadAllRecipesByCategory(userIngredients)
            } else {
                searchRecipesByTitle(query)
            }
        }
    })
        clearButton.setOnClickListener {
            searchBar.setText("")
        }
    }
    private fun setupAutocompleteSuggestions() {
        FirebaseFirestore.getInstance().collection("recipes")
            .get()
            .addOnSuccessListener { result ->
                val titles = result.documents.mapNotNull { it.getString("title") }.distinct()
                val adapter =
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, titles)
                searchBar.setAdapter(adapter)
            }
    }

    private fun fetchFavorites(callback: (Set<String>) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return callback(emptySet())

        Firebase.firestore.collection("users")
            .document(userId)
            .collection("favorites")
            .get()
            .addOnSuccessListener { result ->
                val ids = result.documents.mapNotNull { it.getString("id") }.toSet()
                callback(ids)
            }
            .addOnFailureListener {
                callback(emptySet())
            }
    }

    private fun loadAllRecipesByCategory(userIngredients: List<String>) {
        progressBar.isVisible = true

        FirebaseFirestore.getInstance().collection("recipes")
            .get()
            .addOnSuccessListener { result ->

                val recipes = result.documents.mapNotNull { doc ->
                    doc.toObject(Recipe::class.java)?.apply { id = doc.id }
                }.distinctBy { it.title }

                fetchFavorites { favoriteIds ->
                    val recipesWithLikes = recipes.map {
                        it.copy(isLiked = favoriteIds.contains(it.id))
                    }

                    val categorized = recipesWithLikes.groupBy { it.category }
                    allRecipesContainer.removeAllViews()

                    for ((category, recipesInCategory) in categorized) {
                        val recipeViews = mutableListOf<RecyclerView>()

                        val titleView = TextView(this).apply {
                            text = category
                            textSize = 20f
                            setTextColor(ContextCompat.getColor(this@AllRecipesActivity, android.R.color.black))
                            setPadding(24, 40, 24, 24)
                            setBackgroundColor(ContextCompat.getColor(this@AllRecipesActivity, R.color.amber_800))
                        }
                        allRecipesContainer.addView(titleView)
                        titleView.setOnClickListener {
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
                            val intent = Intent(this@AllRecipesActivity, RecipeDetailsActivity::class.java)
                            // Sends a Recipe object from one screen (AllRecipesActivity) to another screen (RecipeDetailsActivity) using Intent
                            intent.putExtra("recipeId", match.recipe.id)
                            intent.putStringArrayListExtra("matchedIngredients", ArrayList())
                            intent.putStringArrayListExtra("userIngredients", ArrayList(userIngredients))
                            startActivity(intent)
                        }
                            val recipeRecycler = RecyclerView(this).apply {
                                layoutManager = LinearLayoutManager(this@AllRecipesActivity)
                                this.adapter = adapter
                                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                                isNestedScrollingEnabled = false
                            }
                            recipeViews.add(recipeRecycler)
                            allRecipesContainer.addView(recipeRecycler)
                        }
                    }
                    progressBar.isVisible = false

                }
            }
            .addOnFailureListener { exception ->
                progressBar.isVisible = false
                Toast.makeText(this, "Failed to load recipes", Toast.LENGTH_SHORT).show()
                Log.e("AllRecipes", "Error: ", exception)
            }
    }
    private fun searchRecipesByTitle(query: String) {
        progressBar.isVisible = true
        noRecipesText.isVisible = false

        FirebaseFirestore.getInstance().collection("recipes")
            .get()
            .addOnSuccessListener { result ->
                val matchingRecipes = result.documents.mapNotNull { doc ->
                    doc.toObject(Recipe::class.java)?.apply {
                        id = doc.id
                    }
                }.filter { it.title.startsWith(query, ignoreCase = true) }

                if (matchingRecipes.isEmpty()) {
                    allRecipesContainer.removeAllViews()
                    noRecipesText.isVisible = true
                    progressBar.isVisible = false
                    return@addOnSuccessListener
                }

                fetchFavorites { favoriteIds ->
                    val recipesWithLikes = matchingRecipes.map {
                        it.copy(isLiked = favoriteIds.contains(it.id))
                    }

                    allRecipesContainer.removeAllViews()
                    for (recipe in recipesWithLikes) {
                        val match = RecipeMatch(
                            recipe = recipe,
                            matchedIngredients = emptyList(),
                            missingIngredients = emptyList(),
                            score = 0.0,
                            userIngredients = emptyList()
                        )

                        val adapter = RecipeAdapter(listOf(match), highlightIngredients = false) {
                            val intent = Intent(this@AllRecipesActivity, RecipeDetailsActivity::class.java)
                            intent.putExtra("recipeId", match.recipe.id)
                            intent.putStringArrayListExtra("userIngredients", ArrayList(userIngredients))
                            startActivity(intent)
                        }

                        val recycler = RecyclerView(this).apply {
                            layoutManager = LinearLayoutManager(this@AllRecipesActivity)
                            this.adapter = adapter
                            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                            isNestedScrollingEnabled = false
                        }

                        allRecipesContainer.addView(recycler)
                    }
                    progressBar.isVisible = false

                }
            }
            .addOnFailureListener {
                progressBar.isVisible = false
                Toast.makeText(this, "Search failed", Toast.LENGTH_SHORT).show()
            }
    }
}