package dev.yahaveliyahu.kitchenmate.ui.add

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.googlecode.tesseract.android.TessBaseAPI
import dev.yahaveliyahu.kitchenmate.AddRecipeActivity
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap

// Activity class that handles the file the user shares (PDF or TXT)
class ImportRecipeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Checking if a file arrived through sharing
        if (intent?.action == Intent.ACTION_SEND && intent.type != null) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // If it is a new version (Android 13 and above) – use the new way
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                // Otherwise (old version) – using the old way
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            } ?: intent.data
            if (uri != null) {
                when (intent.type) { // Tells the system what type of file is being shared with the app
                    "text/plain" -> handleTextFile(uri)
                    "application/pdf" -> handlePdfFile(uri)
                    else -> showError("Unsupported file type: ${intent.type}")
                }
            } else {
                showError("No file received")
            }
        } else {
            showError("Invalid sharing intent")
        }
    }

    private fun handleTextFile(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        val text = inputStream?.bufferedReader().use { it?.readText() }
        if (!text.isNullOrBlank()) {
            val recipe = parseRecipeText(text)
            showPreviewDialog(recipe)
        } else {
            showError("Failed to read text file")
        }
    }

    private fun handlePdfFile(uri: Uri) {
        try {
            val descriptor: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r")
            if (descriptor == null) {
                showError("Cannot open PDF file")
                return
            }

            val renderer = PdfRenderer(descriptor)
            if (renderer.pageCount == 0) {
                showError("Empty PDF")
                renderer.close()
                return
            }

            // Draws the first page of the PDF into a 3x enlarged Bitmap image for better OCR quality
            val page = renderer.openPage(0)
            val scale = 4
            val bitmap = createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale.toFloat(), scale.toFloat())
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()

            val text = runTesseractOCR(bitmap)
            if (text.isBlank()) {
                showError("No text found in PDF")
            } else {
                val recipe = parseRecipeText(text)

                if (
                    recipe.title.isBlank() &&
                    recipe.category.isBlank() &&
                    recipe.ingredients.isBlank() &&
                    recipe.essentialIngredients.isBlank() &&
                    recipe.instructions.isBlank()
                ) {
                    // If all fields are empty, meaning Tesseract read something,
                    // but parseRecipeText found no headers,
                    // a message is displayed to the user explaining why the scan didn't work
                    showError(
                        "Text was detected, but it's not in the expected recipe format.\nMake sure to include headers like 'Title:', 'Ingredients:', etc."
                    )
                } else {
                    showPreviewDialog(recipe)
                }
            }
        } catch (e: Exception) {
            showError("Error reading PDF: ${e.message}")
        }
    }

    private fun runTesseractOCR(bitmap: Bitmap): String {
        val tess = TessBaseAPI() // OCR engine creator
        val dataPath = filesDir.absolutePath + "/tesseract/" // Checks if the language file exists in the internal storage
        // Copying the eng.traineddata file from the assets into an internal file folder if it does not exist
        val tessDataDir = File(dataPath + "tessdata/")
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs()
            assets.open("tessdata/eng.traineddata").use { input ->
                FileOutputStream(File(tessDataDir, "eng.traineddata")).use { output ->
                    input.copyTo(output)
                }
            }
        }

        tess.init(dataPath, "eng")
        tess.setImage(bitmap)
        val result = tess.utF8Text
        tess.end()

        return result
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
            .setTitle("Imported Recipe Preview")
            .setView(scrollView)
            .setPositiveButton("Continue to Add") { _, _ ->
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

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
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

            when {
                lower.startsWith("category") -> {
                    category = trimmed.removePrefix("Category:").removePrefix("category:").trim()
                    section = ""
                }
                lower.startsWith("title") -> {
                    title = trimmed.removePrefix("Title:").removePrefix("title:").trim()
                    section = ""
                }
                lower.startsWith("ingredients") -> {
                    section = "ingredients"
                }

                lower.startsWith("essential ingredients") || lower.startsWith("essential:") -> {
                    section = "essential"
                }

                lower.startsWith("instructions") || lower.startsWith("directions") -> {
                    section = "instructions"
                }

                section == "ingredients" && trimmed.isNotBlank() -> {
                    ingredients.appendLine(
                        trimmed.removePrefix("Ingredients:").removePrefix("ingredients:").trim()
                    )
                }

                section == "essential" && trimmed.isNotBlank() -> {
                    essentialIngredients.appendLine(
                        trimmed.removePrefix("Essential Ingredients:").removePrefix("essential ingredients:").removePrefix("Essential:").removePrefix("essential:").trim()
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
