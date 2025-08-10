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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.RecipeAdapter
import dev.yahaveliyahu.kitchenmate.model.Recipe
import dev.yahaveliyahu.kitchenmate.model.RecipeMatch
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.view.isVisible


class RecentRecipesActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var progressBar: ProgressBar
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var titleText: TextView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent_recipes)

        container = findViewById(R.id.recyclerViewRecent)
        progressBar = findViewById(R.id.recent_recipes_PROGRESS)
        titleText = findViewById(R.id.recent_recipes_LBL_title)
        emptyText = findViewById(R.id.recent_recipes_LBL_empty)

        fetchRecentRecipes()
    }

    private fun fetchRecentRecipes() {
        progressBar.isVisible = true

        val userId = auth.currentUser?.uid ?: return.also {
            Log.e("RecentDebug", "User not logged in")
        }

        db.collection("users")
            .document(userId)
            .collection("recentRecipes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(15)
            .get()
            .addOnSuccessListener { result ->
                val recentDocs = result.documents

                if (recentDocs.isEmpty()) {
                    Log.d("RecentDebug", "No recipes found")
                    progressBar.isVisible = false
                    emptyText.isVisible = true
                    container.removeAllViews()
                    return@addOnSuccessListener
                }
                // Prepares an empty list of valid recipes + counts how many more recipes need to be reviewed
                val validRecipes = mutableListOf<Recipe>()
                var pending = recentDocs.size

                for (doc in recentDocs) {
                    val recipeId = doc.id

                    db.collection("recipes").document(recipeId).get()
                        .addOnSuccessListener { recipeSnap ->
                            if (recipeSnap.exists()) {
                                val recipe = recipeSnap.toObject(Recipe::class.java)
                                recipe?.let {
                                    it.id = recipeId
                                    it.timestamp = doc.getTimestamp("timestamp")
                                    validRecipes.add(it)
                                }
                            } else {
                                // If the recipe does not exist – delete it from the user's recentRecipes as well.
                                db.collection("users")
                                    .document(userId)
                                    .collection("recentRecipes")
                                    .document(recipeId)
                                    .delete()
                                    .addOnSuccessListener {
                                        Log.d("RecentDebug", "Removed deleted recipe: $recipeId")
                                    }
                            }
                            // Decrements the remaining process counter, and if it is the last one, calls the displayRecipes function
                            pending--
                            if (pending == 0) displayRecipes(validRecipes)
                        }
                        .addOnFailureListener {
                            Log.e("RecentDebug", "Failed to fetch recipe $recipeId")
                            pending--
                            if (pending == 0) displayRecipes(validRecipes)
                        }
                }
            }
            .addOnFailureListener {
                progressBar.isVisible = false
                Toast.makeText(this, "Failed to fetch recent recipes", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayRecipes(recipes: List<Recipe>) {
        progressBar.isVisible = false

        if (recipes.isEmpty()) {
            Log.d("RecentDebug", "No recipes found")
            emptyText.isVisible = true
            container.removeAllViews()
            return
        }

        emptyText.isVisible = false
        titleText.isVisible = true
        container.removeAllViews()

        // Grouping by category
        val grouped = recipes.groupBy { it.category }
        // Sort categories by the most recent recipe in each (descending)
        val sortedCategories = grouped.entries.sortedByDescending { entry ->
            entry.value.maxOfOrNull { it.timestamp?.toDate()?.time ?: 0L } ?: 0L
        }

        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        for ((category, recipesInCategory) in sortedCategories) {
            Log.d("RecentDebug", "Category: $category has ${recipesInCategory.size} recipes")

            if (recipesInCategory.isEmpty()) continue
            val recipeViews = mutableListOf<RecyclerView>()

            val categoryTitle = TextView(this).apply {
                text = category
                textSize = 20f
                setTextColor(ContextCompat.getColor(this@RecentRecipesActivity, android.R.color.black))
                setPadding(24, 40, 24, 24)
                setBackgroundColor(ContextCompat.getColor(this@RecentRecipesActivity, R.color.amber_800))
            }
            // Adds the title to the screen. If clicked, opens/closes the recipes in the category
            container.addView(categoryTitle)
            categoryTitle.setOnClickListener {
                for (view in recipeViews) {
                    view.isVisible = !view.isVisible
                }
            }

            for (match in recipesInCategory.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
                .map {
                RecipeMatch(
                    recipe = it,
                    matchedIngredients = emptyList(),
                    missingIngredients = emptyList(),
                    score = 0.0,
                    userIngredients = emptyList()
                )
            }) {
                val adapter = RecipeAdapter(
                    listOf(match),
                    highlightIngredients = false,
                    source = "recent",
                    onRecipeClick = { clicked ->
                        val intent = Intent(this@RecentRecipesActivity, RecipeDetailsActivity::class.java)
                        intent.putExtra("recipeId", clicked.recipe.id)
                        intent.putExtra("source", "recent")
                        // Not sending full recipe
                        intent.putStringArrayListExtra("userIngredients", ArrayList(emptyList<String>()))
                        startActivity(intent)
                    }
                )

                val recyclerView = RecyclerView(this).apply {
                    layoutManager = LinearLayoutManager(this@RecentRecipesActivity)
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
















//
//val categorized = recipes.groupBy { it.category ?: "Other" }
//        Log.d("RecentDebug", "Grouped into ${categorized.size} categories")
//
//
//        for ((category, recipesInCategory) in categorized) {
//            Log.d("RecentDebug", "Category: $category has ${recipesInCategory.size} recipes")
//
//            if (recipesInCategory.isEmpty()) continue
//
//            val recipeViews = mutableListOf<RecyclerView>()
//
//            val categoryTitle = TextView(this).apply {
//                text = category
//                textSize = 20f
//                setTextColor(
//                    ContextCompat.getColor(
//                        this@RecentRecipesActivity,
//                        android.R.color.black
//                    )
//                )
//                setPadding(24, 40, 24, 24)
//                setBackgroundColor(
//                    ContextCompat.getColor(
//                        this@RecentRecipesActivity,
//                        R.color.amber_800
//                    )
//                )
//            }
//
//            container.addView(categoryTitle)
//            categoryTitle.setOnClickListener {
//                for (view in recipeViews) {
//                    view.visibility =
//                        if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
//                }
//            }
//
//            for (match in recipesInCategory.map {
//                RecipeMatch(
//                    recipe = it,
//                    matchedIngredients = emptyList(),
//                    missingIngredients = emptyList(),
//                    score = 0.0,
//                    userIngredients = emptyList()
//                )
//            }) {
//                Log.d("RecentDebug", "Creating adapter for recipe: ${match.recipe.title}")
//
//                val ts = match.recipe.timestamp?.toDate()
//                val timestampString =
//                    if (ts != null) "Viewed on: ${dateFormat.format(ts)}" else "View time unknown"
//
//                val adapter = RecipeAdapter(
//                    listOf(match),
//                    highlightIngredients = false,
//                    onRecipeClick = {
//                        val intent =
//                            Intent(this@RecentRecipesActivity, RecipeDetailsActivity::class.java)
//                        intent.putExtra("recipe", match.recipe)
//                        intent.putStringArrayListExtra(
//                            "userIngredients",
//                            ArrayList(emptyList<String>())
//                        )
//                        startActivity(intent)
//                    }
//                )
//
//                val recyclerView = RecyclerView(this).apply {
//                    layoutManager = LinearLayoutManager(this@RecentRecipesActivity)
//                    this.adapter = adapter
//                    overScrollMode = RecyclerView.OVER_SCROLL_NEVER
//                    isNestedScrollingEnabled = false
//                }
//
//                recipeViews.add(recyclerView)
//                container.addView(recyclerView)
//            }
//        }
//    }
//}












//
//
//
//    emptyText.visibility = View.GONE
//                    titleText.visibility = View.VISIBLE
//                    container.removeAllViews()
//
//                    val categorized = recipes.groupBy { it.category ?: "Other" }
//                    Log.d("RecentDebug", "Grouped into ${categorized.size} categories")
//
//                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
//
//                    for ((category, recipesInCategory) in categorized) {
//                        Log.d(
//                            "RecentDebug",
//                            "Category: $category has ${recipesInCategory.size} recipes"
//                        )
//
//                        if (recipesInCategory.isEmpty()) continue
//
//                        val recipeViews = mutableListOf<RecyclerView>()
//
//                        val categoryTitle = TextView(this).apply {
//                            text = category
//                            textSize = 20f
//                            setTextColor(ContextCompat.getColor(this@RecentRecipesActivity, android.R.color.black))
//                            setPadding(24, 40, 24, 24)
//                            setBackgroundColor(ContextCompat.getColor(this@RecentRecipesActivity, R.color.amber_800))
//                        }
//
//                        container.addView(categoryTitle)
//                        categoryTitle.setOnClickListener {
//                            for (view in recipeViews) {
//                                view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
//                            }
//                        }
//
//                        for (match in recipesInCategory.map {
//                            RecipeMatch(
//                                recipe = it,
//                                matchedIngredients = emptyList(),
//                                missingIngredients = emptyList(),
//                                score = 0.0,
//                                userIngredients = emptyList()
//                            )
//                        }) {
//                            Log.d(
//                                "RecentDebug",
//                                "Creating adapter for recipe: ${match.recipe.title}"
//                            )
//
//                            val ts = match.recipe.timestamp?.toDate()
//                            val timestampString = if (ts != null) "Viewed on: ${dateFormat.format(ts)}" else "View time unknown"
//
////                            val timestampText = TextView(this).apply {
////                                text = timestampString
////                                setPadding(32, 8, 32, 8)
////                                setTextColor(ContextCompat.getColor(this@RecentRecipesActivity, android.R.color.black))
////                            }
//
//                            val adapter = RecipeAdapter(
//                                listOf(match),
//                                highlightIngredients = false,
//                                onRecipeClick = {
//                                    val intent = Intent(
//                                        this@RecentRecipesActivity,
//                                        RecipeDetailsActivity::class.java
//                                    )
//                                    intent.putExtra("recipe", match.recipe)
//                                    intent.putStringArrayListExtra(
//                                        "userIngredients",
//                                        ArrayList(emptyList<String>())
//                                    )
//                                    startActivity(intent)
//                                }
//                            )
//
////                            val timestampText = TextView(this).apply {
////                                val ts = match.recipe.timestamp?.toDate()
////                                text = if (ts != null) "Viewed on: ${dateFormat.format(ts)}" else "View time unknown"
////                                setPadding(32, 8, 32, 8)
////                                setTextColor(ContextCompat.getColor(this@RecentRecipesActivity, android.R.color.black))
////                            }
//
//                            val recyclerView = RecyclerView(this).apply {
//                                layoutManager = LinearLayoutManager(this@RecentRecipesActivity)
//                                this.adapter = adapter
//                                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
//                                isNestedScrollingEnabled = false
//                            }
//
//                            recipeViews.add(recyclerView)
//                            container.addView(recyclerView)
//                        }
//                    }
//                }
//            .addOnFailureListener {
//                progressBar.visibility = View.GONE
//                Toast.makeText(this, "Failed to fetch recent recipes", Toast.LENGTH_SHORT).show()
//            }
//    }
//}




//                val recipes = result.documents.mapNotNull { doc ->
//                    doc.toObject(Recipe::class.java)?.apply {
//                        id = doc.id
//                        timestamp = doc.getTimestamp("timestamp")
//                    }
//                }