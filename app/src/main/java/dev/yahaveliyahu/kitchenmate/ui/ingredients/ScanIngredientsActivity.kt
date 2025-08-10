package dev.yahaveliyahu.kitchenmate.ui.ingredients

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.service.RecipeMatcher

class ScanIngredientsActivity : AppCompatActivity() {

    private val selectedIngredients = mutableListOf<String>()

    private val barcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleScanResult(result.data)
    }

    private val scanWithoutBarcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleScanResult(result.data)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleScanResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_ingredients)

        // Get the existing ingredient list from the ManuallyEnteringIngredientsActivity screen
        val previousList = intent.getStringArrayListExtra("userIngredients")
        if (previousList != null) {
            selectedIngredients.addAll(previousList)
        }

        // Barcode scanning
        findViewById<CardView>(R.id.btnBarcodeScan).setOnClickListener {
            val intent = Intent(this, LiveBarcodeScannerActivity::class.java)
            barcodeLauncher.launch(intent)
        }

        // Product scanning without barcode
        findViewById<CardView>(R.id.btnScanWithoutBarcode).setOnClickListener {
            val intent = Intent(this, ScanWithoutBarcodeActivity::class.java)
            scanWithoutBarcodeLauncher.launch(intent)
        }

        // Select a photo from the gallery
        findViewById<CardView>(R.id.btnSelectFromGallery).setOnClickListener {
            val intent = Intent(this, GallerySelectActivity::class.java)
            galleryLauncher.launch(intent)
        }
    }

    // One function that handles the result of each action
    private fun handleScanResult(data: Intent?) {
        val scannedIngredient = data?.getStringExtra("scannedIngredient")?.trim()
        scannedIngredient?.let {
            val normalized = RecipeMatcher().normalizeIngredient(it)
            if (!selectedIngredients.contains(normalized)) {
                selectedIngredients.add(normalized)
                Toast.makeText(this, "Scanned: \"$it\"\n Normalized: \"$normalized\"", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Already added: $normalized", Toast.LENGTH_SHORT).show()
            }

            // Go to the ManuallyEnteringIngredientsActivity with the updated list
            val intent = Intent(this, ManuallyEnteringIngredientsActivity::class.java)
            intent.putStringArrayListExtra("userIngredients", ArrayList(selectedIngredients))
            startActivity(intent)
        }
    }
}