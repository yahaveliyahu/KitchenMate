package dev.yahaveliyahu.kitchenmate.ui.recipes

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import dev.yahaveliyahu.kitchenmate.ui.ingredients.ManuallyEnteringIngredientsActivity
import dev.yahaveliyahu.kitchenmate.R

class NoRecipeFoundActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.item_no_recipe_found)

        val backButton: Button = findViewById(R.id.button_back_to_ingredients)

        val userIngredients = intent.getStringArrayListExtra("userIngredients") ?: arrayListOf()

        backButton.setOnClickListener {
            val intent = Intent(this, ManuallyEnteringIngredientsActivity::class.java)
            intent.putStringArrayListExtra("userIngredients", ArrayList(userIngredients))
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }
}