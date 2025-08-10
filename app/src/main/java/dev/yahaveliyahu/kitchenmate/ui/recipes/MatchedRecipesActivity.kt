package dev.yahaveliyahu.kitchenmate.ui.recipes

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.yahaveliyahu.kitchenmate.ui.ingredients.ManuallyEnteringIngredientsActivity
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.RecipeAdapter
import dev.yahaveliyahu.kitchenmate.service.RecipeService
import dev.yahaveliyahu.kitchenmate.service.SpoonacularService
import dev.yahaveliyahu.kitchenmate.model.RecipeMatch


class MatchedRecipesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noPerfectMatchTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var addMoreButton: Button
    private val recipeService = RecipeService()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_matched_recipes)

        recyclerView = findViewById(R.id.recycler_matched_recipes)
        recyclerView.layoutManager = LinearLayoutManager(this)

        noPerfectMatchTextView = findViewById(R.id.txt_no_perfect_match)
        progressBar = findViewById(R.id.matched_recipes_PROGRESS)
        addMoreButton = findViewById(R.id.button_add_more)

        noPerfectMatchTextView.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        // Get the list of ingredients from the previous screen
        val userIngredients = intent.getStringArrayListExtra("userIngredients") ?: arrayListOf()
        // If no ingredients were passed, use an empty list

        // Step 1 – Search the Local Database
        recipeService.findMatchingRecipesFromFirebase(userIngredients)
        { firebaseMatches, hasPerfectMatch ->
            if (firebaseMatches.isNotEmpty()) {
                progressBar.visibility = View.GONE

                if (!hasPerfectMatch) {
                    noPerfectMatchTextView.visibility = View.VISIBLE
                    noPerfectMatchTextView.text =
                        getString(R.string.there_are_no_recipes_that_you_can_make_with_just_what_you_have_at_home_but_if_you_buy_a_few_more_ingredients_you_can_make_the_following)
                }
                recyclerView.adapter = RecipeAdapter(firebaseMatches, highlightIngredients = false, source = "matched"
                ) { item ->
                    Log.d("MatchedRecipes", "fb: adapter set (source=matched, with callback)")
                    openRecipeDetails(item, userIngredients)
                }
            } else {
                // Step 2 – If there is no result in Firestore, turn to the Spoonacular API
                SpoonacularService.findMatchingRecipesFromSpoonacular(userIngredients) { apiMatches ->
                    progressBar.visibility = View.GONE

                    if (apiMatches.isNotEmpty()) {
                        noPerfectMatchTextView.visibility = View.VISIBLE
                        noPerfectMatchTextView.text =
                            getString(R.string.we_found_no_matches_in_our_database_but_here_are_some_recipes_from_the_web_that_are_close_to_what_you_can_cook)

                        recyclerView.adapter = RecipeAdapter(
                            apiMatches,
                            highlightIngredients = false,
                            source = "spoonacular"
                        )
                    } else {
                        // Step 3 – Nothing found on Spoonacular either
                        val intent = Intent(this, NoRecipeFoundActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }

        addMoreButton.setOnClickListener {
            val intent = Intent(this, ManuallyEnteringIngredientsActivity::class.java)
            intent.putStringArrayListExtra("userIngredients", ArrayList(userIngredients))
            startActivity(intent)
            finish()
        }
    }

    private fun openRecipeDetails(item: RecipeMatch, userIngredients: List<String>) {
        val intent = Intent(this, RecipeDetailsActivity::class.java)
        // Saving everything needed inside the Intent so the next screen knows what to display
        intent.putExtra("recipeId", item.recipe.id)
        intent.putStringArrayListExtra("matchedIngredients", ArrayList(item.matchedIngredients))
        intent.putStringArrayListExtra("userIngredients", ArrayList(userIngredients))
        intent.putExtra("source", "matched")
        startActivity(intent) // Go to RecipeDetailsActivity screen
    }
}