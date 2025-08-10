package dev.yahaveliyahu.kitchenmate.ui.add

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.yahaveliyahu.kitchenmate.AddRecipeActivity
import androidx.core.net.toUri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// This activity scans an image for recipe text using OCR
// and allows the user to preview and edit the recipe before adding it
class OCRScanRecipeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Converts URI to Bitmap
        val imageUriString = intent.getStringExtra("image_uri")
        val imageUri = imageUriString?.toUri() // Converts the string back to URI
        // If there is a URI, we will run a coroutine (using lifecycleScope)
        // to perform background loading (IO thread), which is a heavy operation
        if (imageUri != null) {
            lifecycleScope.launch {
                val bitmap = loadBitmap(imageUri)
                // If we were able to convert the URI to a Bitmap, we will run OCR on it
                if (bitmap != null) {
                    runTextRecognition(bitmap)
                } else {
                    Toast.makeText(this@OCRScanRecipeActivity, "⚠️ Failed to load image", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } else {
            Toast.makeText(this, "⚠️ No image provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // A function that is loaded inside a coroutine in a separate thread to load the image safely
    private suspend fun loadBitmap(imageUri: android.net.Uri): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, imageUri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

        private fun runTextRecognition(bitmap: Bitmap) { // Convert the bitmap to an InputImage
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val recipe = parseRecipeText(fullText)
                    showPreviewDialog(recipe)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Text recognition failed", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }

    private fun showPreviewDialog(recipe: RecipeData) {
        val message = "Category: ${recipe.category}\n\n" +
                "Title: ${recipe.title}\n\n" +
                "Ingredients:\n${recipe.ingredients}\n\n" +
                "Essential Ingredients:\n${recipe.essentialIngredients}\n\n" +
                "Instructions:\n${recipe.instructions}"

        val textView = TextView(this).apply {
            text = message
            setPadding(32, 32, 32, 32)
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }


        AlertDialog.Builder(this)
            .setTitle("Scan Result Preview")
            .setView(scrollView)
            .setPositiveButton("Continue / Edit") { _, _ ->
                // Open AddRecipeActivity and pass it the data we managed to extract from the image
                val intent = Intent(this, AddRecipeActivity::class.java).apply {
                    putExtra("title", recipe.title)
                    putExtra("category", recipe.category)
                    putExtra("ingredients", recipe.ingredients)
                    putExtra("essentialIngredients", recipe.essentialIngredients)
                    putExtra("instructions", recipe.instructions)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    private fun parseRecipeText(fullText: String): RecipeData {
        val lines = fullText.split("\n")
        var title = ""
        var category = ""
        val ingredients = StringBuilder()
        val essentialIngredients = StringBuilder()
        val instructions = StringBuilder()
        var section = ""

        for (line in lines) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()

            when { // Check for specific keywords to identify sections
                lower.startsWith("category") -> {
                    category = trimmed
                        .removePrefix("Category:")
                        .removePrefix("category:")
                        .trim()
                    section = ""
                }


                lower.startsWith("title:") -> {
                    title = trimmed
                        .removePrefix("Title:")
                        .removePrefix("title:")
                        .trim()
                    section = ""
                }

                lower.startsWith("ingredients") -> {
                    section = "ingredients"
                }

                lower.startsWith("essential ingredients") || lower.startsWith("essential:") -> {
                    section = "essential"
                }

                lower.startsWith("instructions:") || lower.startsWith("directions:") -> {
                    section = "instructions"
                }

                section == "essential" && trimmed.isNotBlank() -> {
                    essentialIngredients.appendLine(
                        trimmed.removePrefix("Essential Ingredients:").removePrefix("essential ingredients:").removePrefix("Essential:").removePrefix("essential:").trim()
                    )
                }

                section == "ingredients" && trimmed.isNotBlank() -> {
                    ingredients.appendLine(
                        trimmed.removePrefix("Ingredients:").removePrefix("ingredients:").trim()
                    )
                }

                section == "instructions" && trimmed.isNotBlank() -> {
                    instructions.appendLine(
                        trimmed.removePrefix("Instructions:").removePrefix("instructions:").trim()
                    )
                }
            }
        }

        return RecipeData(
            title = title,
            category = category,
            ingredients = ingredients.toString().trim(),
            essentialIngredients = essentialIngredients.toString().trim(),
            instructions = instructions.toString().trim()
        )
    }

    data class RecipeData(
        val title: String,
        val category: String,
        val ingredients: String,
        val essentialIngredients: String,
        val instructions: String
    )
}