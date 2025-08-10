package dev.yahaveliyahu.kitchenmate.ui.recipes

import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.model.Recipe
import dev.yahaveliyahu.kitchenmate.ui.ingredients.ManuallyEnteringIngredientsActivity
import dev.yahaveliyahu.kitchenmate.utils.*



// Responsible for displaying a full screen of a recipe
class RecipeDetailsActivity : AppCompatActivity() {

    private lateinit var titleTextView: TextView
    private lateinit var ingredientsTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var ingredientsTitleTextView: TextView
    private lateinit var instructionsTitleTextView: TextView
    private lateinit var imageView: ImageView
    private lateinit var btnDeleteRecipe: Button
    private lateinit var tooltipIcon: ImageView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_details)

        titleTextView = findViewById<TextView>(R.id.recipe_details_LBL_title)
        ingredientsTextView = findViewById<TextView>(R.id.recipe_details_LBL_ingredients)
        instructionsTextView = findViewById<TextView>(R.id.recipe_details_LBL_instructions)
        ingredientsTitleTextView = findViewById<TextView>(R.id.recipe_details_LBL_ingredients_title)
        instructionsTitleTextView = findViewById<TextView>(R.id.recipe_details_LBL_instructions_title)
        imageView = findViewById<ImageView>(R.id.recipe_details_IMG_image)
        btnDeleteRecipe = findViewById<Button>(R.id.recipe_details_BTN_delete)
        tooltipIcon = findViewById<ImageView>(R.id.recipe_details_IMG_tooltip)

        val source = intent.getStringExtra("source") ?: ""
        val userIngredients = intent.getStringArrayListExtra("userIngredients") ?: emptyList<String>()

        // Serializable read: typed API (Android 13+) or legacy
        val recipe: Recipe? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("recipe", Recipe::class.java).also {
                    Log.d("RecipeDetails", "deserialize: recipeIsNull=${it == null} (typed API)")
                }
            } else {
                @Suppress("DEPRECATION")
                (intent.getSerializableExtra("recipe") as? Recipe).also {
                    Log.d("RecipeDetails", "deserialize: recipeIsNull=${it == null} (legacy API)")
                }
            }

        if (recipe != null) {
            val treatAsMatched = source == "matched" || source == "spoonacular"
            if (treatAsMatched) {
                displayMatchedRecipe(recipe, userIngredients)
            } else {
                displayAllRecipe(recipe)
            }
            return
        }

        // Guard for Spoonacular route – Firestore is not accessed if there is no object
        if (source == "spoonacular") {
            Log.e("RecipeDetails", "spn: missing recipe object; cannot load from Firestore")
            Toast.makeText(this, "Spoonacular Recipe not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // If we didn't get a recipe, we'll load from Firestore by ID
        val recipeId = intent.getStringExtra("recipeId")
        if (recipeId == null) {
            Toast.makeText(this, "Missing recipe ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        FirebaseFirestore.getInstance().collection("recipes")
            .document(recipeId)
            .get()
            .addOnSuccessListener { document ->
                Log.d(
                    "RecipeDetails",
                    "db: toObject -> exists=${document.exists()} mappedNull=${document.toObject(Recipe::class.java) == null}"
                )
                try {
                    val recipeFromDb = document.toObject(Recipe::class.java)
                    if (recipeFromDb != null) {
                        recipeFromDb.id = recipeId
                        if (source == "matched") { // That is, the recipe came from an ingredient matching algorithm
                            displayMatchedRecipe(recipeFromDb, userIngredients)
                        } else {
                            displayAllRecipe(recipeFromDb)
                        }
                    } else {
                        Toast.makeText(this, "Recipe not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e : Exception) {
                    Log.e("RecipeDetails", "❌ Failed to parse recipe: ${e.message}")
                    Toast.makeText(this, "Invalid recipe data", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load recipe", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }

    private fun displayMatchedRecipe(recipe: Recipe, userIngredients: List<String>) {
        titleTextView.text = recipe.title
        ingredientsTitleTextView.text = getString(R.string.ingredients_details)
        instructionsTitleTextView.text = getString(R.string.instructions_details)

        // Color the bullet in amber_800 and ingredient in green or red by match
        ingredientsTextView.text = buildSmartIngredientHighlight(
            context = this,
            ingredients = recipe.ingredients,
            userIngredients = userIngredients,
            commonIngredients = ManuallyEnteringIngredientsActivity.commonIngredients,
            bulletColor = ContextCompat.getColor(this, R.color.amber_800)
        )

        displayInstructions(instructionsTextView, recipe.instructions)
        displayImage(imageView, recipe.imageUrl)

        tooltipIcon.visibility = View.VISIBLE
        tooltipIcon.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Ingredients Legend")
                .setMessage("🟢 Groceries you have\n🔴 Groceries you need to buy")
                .setPositiveButton("OK", null)
                .show()
        }
        saveToRecent(recipe)
        btnDeleteRecipe.visibility = View.GONE
    }

    private fun displayAllRecipe(recipe: Recipe) {
        titleTextView.text = recipe.title
        ingredientsTitleTextView.text = getString(R.string.ingredients_details)
        instructionsTitleTextView.text = getString(R.string.instructions_details)

        // Color the bullet in amber_800
        val spannable = SpannableStringBuilder()
        recipe.ingredients.forEach { ingredient ->
            val bullet = "\u2022 "
            spannable.append(bullet)
            spannable.append("$ingredient\n")
        }
        ingredientsTextView.text = spannable
        displayInstructions(instructionsTextView, recipe.instructions)
        displayImage(imageView, recipe.imageUrl)

        // Show delete button if user is an admin
        val isAdmin = FirebaseAuth.getInstance().currentUser?.email == "yahaveliyahu@gmail.com"
        if (isAdmin) {
            btnDeleteRecipe.visibility = View.VISIBLE
            btnDeleteRecipe.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Deleting a recipe")
                    .setMessage("Are you sure you want to delete the recipe?")
                    .setPositiveButton("Delete") { _, _ ->
                        FirebaseFirestore.getInstance().collection("recipes").document(recipe.id)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Recipe deleted", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error deleting", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            btnDeleteRecipe.visibility = View.GONE
        }

        saveToRecent(recipe)
        tooltipIcon.visibility = View.GONE
    }

    // Coloring the numbering of the preparation instructions in amber_800
    private fun displayInstructions(instructionsTextView: TextView, instructions: String) {
        instructionsTextView.text = buildColoredInstructionsText(
            context = instructionsTextView.context,
            instructions = instructions,
            numberingColor = ContextCompat.getColor(instructionsTextView.context, R.color.amber_800)
        )
    }

    private fun displayImage(imageView: ImageView, imageUrl: String) {
        Glide.with(imageView.context)
            .load(imageUrl.takeIf { it.isNotEmpty() })
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.error_image)
            .fallback(R.drawable.error_image)
            .into(imageView)
    }

    private fun saveToRecent(recipe: Recipe) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (recipe.id.isBlank()) return

        val recentRef = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("recentRecipes").document(recipe.id)

        val data = hashMapOf(
            "id" to recipe.id,
            "title" to recipe.title,
            "ingredients" to recipe.ingredients,
            "instructions" to recipe.instructions,
            "imageUrl" to recipe.imageUrl,
            "category" to recipe.category,
            "timestamp" to FieldValue.serverTimestamp()
        )
        recentRef.set(data)
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .collection("recentRecipes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                result.documents.drop(15).forEach { it.reference.delete() }
            }
    }
}