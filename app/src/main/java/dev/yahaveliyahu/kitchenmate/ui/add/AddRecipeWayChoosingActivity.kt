package dev.yahaveliyahu.kitchenmate.ui.add

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import dev.yahaveliyahu.kitchenmate.AddRecipeActivity
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.ui.ingredients.TransparentCameraLauncherActivity

class AddRecipeWayChoosingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_recipe_way_choosing)

        val cardManualEntry = findViewById<CardView>(R.id.cardManualEntry)
        val cardScanRecipe = findViewById<CardView>(R.id.cardScanRecipe)

        cardManualEntry.setOnClickListener {
            val intent = Intent(this, AddRecipeActivity::class.java)
            startActivity(intent)
        }

        cardScanRecipe.setOnClickListener {
            val intent = Intent(this, TransparentCameraLauncherActivity::class.java)
            startActivity(intent)
        }
    }
}