package dev.yahaveliyahu.kitchenmate.ui.ingredients

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import dev.yahaveliyahu.kitchenmate.R

class WayChoosingIngredientsActivity : AppCompatActivity() {

    private lateinit var enterIngredientsCard: CardView
    private lateinit var scanIngredientsCard: CardView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_way_choosing_ingredients)

        enterIngredientsCard = findViewById<CardView>(R.id.cardEnterIngredients)
        scanIngredientsCard = findViewById<CardView>(R.id.cardScanIngredients)

        // Go to the scan screen by pressing the button
        scanIngredientsCard.setOnClickListener {
            val intent = Intent(this, ScanIngredientsActivity::class.java)
            startActivity(intent)
        }

        enterIngredientsCard.setOnClickListener {
            val intent = Intent(this, ManuallyEnteringIngredientsActivity::class.java)
            startActivity(intent)
        }
    }
}