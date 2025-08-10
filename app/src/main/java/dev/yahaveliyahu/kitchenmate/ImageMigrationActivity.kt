package dev.yahaveliyahu.kitchenmate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ImageMigrationActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var progressText: TextView

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var totalRecipes = 0
    private var processedRecipes = 0
    private var successCount = 0
    private var failureCount = 0
    private var skippedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_migration)

        initViews()
        checkUserPermission()
    }

    private fun initViews() {
        startButton = findViewById(R.id.btn_start_migration)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.tv_status)
        progressText = findViewById(R.id.tv_progress)

        startButton.setOnClickListener {
            startImageMigration()
        }
    }

    private fun checkUserPermission() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "❌ You must be logged in", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Checking if the user is an admin
        if (user.email != "yahaveliyahu@gmail.com") {
            Toast.makeText(this, "❌ Access denied - Admin only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        statusText.text = getString(R.string.ready_to_migrate_images_to_firebase_storage)
    }

    private fun startImageMigration() {
        startButton.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE
        statusText.text = getString(R.string.starting_image_migration)

        // Reset counters
        totalRecipes = 0
        processedRecipes = 0
        successCount = 0
        failureCount = 0
        skippedCount = 0

        // Get all recipes from Firestore
        db.collection("recipes")
            .get()
            .addOnSuccessListener { documents ->
                totalRecipes = documents.size()
                statusText.text = getString(R.string.found_recipes, totalRecipes)

                if (totalRecipes == 0) {
                    finishMigration()
                    return@addOnSuccessListener
                }

                // Processing any recipe
                for (document in documents) {
                    processRecipe(document.id, document.data)
                }
            }
            .addOnFailureListener { e ->
                statusText.text = getString(R.string.failed_to_fetch_recipes, e.message)
                startButton.isEnabled = true
                progressBar.visibility = ProgressBar.GONE
            }
    }

    private fun processRecipe(recipeId: String, data: Map<String, Any>) {
        val title = data["title"] as? String ?: "Unknown Recipe"
        val imageUrl = data["imageUrl"] as? String ?: ""

        Log.d("ImageMigration", "Processing recipe: $title")

        // Check if the image is already in Firebase Storage
        if (imageUrl.contains("firebasestorage.googleapis.com")) {
            skippedCount++
            Log.d("ImageMigration", "⏭️ Skipped '$title' (already in Storage)")
            updateProgress()
            return
        }

        // Checking if there is a valid URL
        if (imageUrl.isEmpty() || !imageUrl.startsWith("http")) {
            skippedCount++
            Log.d("ImageMigration", "⏭️ Skipped '$title' (no valid URL)")
            updateProgress()
            return
        }

        // Downloading and uploading the image
        CoroutineScope(Dispatchers.IO).launch {
            try {
                downloadAndUploadImage(recipeId, title, imageUrl)
            } catch (e: Exception) {
                failureCount++
                Log.e("ImageMigration", "❌ Error processing '$title': ${e.message}")
                updateProgress()
            }
        }
    }

    private fun downloadAndUploadImage(recipeId: String, title: String, imageUrl: String) {
        try {
            // Downloading the image
            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                failureCount++
                Log.e("ImageMigration", "❌ Failed to download image for '$title'")
                updateProgress()
                return
            }

            // Convert to Bitmap
            val inputStream = response.body!!.byteStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                failureCount++
                Log.e("ImageMigration", "❌ Failed to decode image for '$title'")
                updateProgress()
                return
            }

            // JPEG compression
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val imageBytes = baos.toByteArray()

            // Create a safe file name
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9\\s]"), "")
                .replace("\\s+".toRegex(), "_")
                .take(50)

            val fileName = "${safeTitle}_${recipeId}.jpg"
            val imageRef = storage.reference.child("recipe_images/$fileName")

            // Upload to Firebase Storage
            imageRef.putBytes(imageBytes)
                .addOnSuccessListener {
                    // Getting the new URL
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        // Update Firestore with the new URL
                        db.collection("recipes").document(recipeId)
                            .update("imageUrl", uri.toString())
                            .addOnSuccessListener {
                                successCount++
                                Log.d("ImageMigration", "✅ Successfully migrated '$title'")
                                updateProgress()
                            }
                            .addOnFailureListener { e ->
                                failureCount++
                                Log.e("ImageMigration", "❌ Failed to update Firestore for '$title': ${e.message}")
                                updateProgress()
                            }
                    }.addOnFailureListener { e ->
                        failureCount++
                        Log.e("ImageMigration", "❌ Failed to get download URL for '$title': ${e.message}")
                        updateProgress()
                    }
                }
                .addOnFailureListener { e ->
                    failureCount++
                    Log.e("ImageMigration", "❌ Failed to upload image for '$title': ${e.message}")
                    updateProgress()
                }

        } catch (e: Exception) {
            failureCount++
            Log.e("ImageMigration", "❌ Exception processing '$title': ${e.message}")
            updateProgress()
        }
    }

    private fun updateProgress() {
        processedRecipes++

        runOnUiThread {
            val progressPercent = if (totalRecipes > 0) {
                (processedRecipes * 100) / totalRecipes
            } else 0

            progressText.text = getString(
                R.string.progress_template,
                processedRecipes,
                totalRecipes,
                progressPercent,
                successCount,
                failureCount,
                skippedCount
            )

            if (processedRecipes >= totalRecipes) {
                finishMigration()
            }
        }
    }

    private fun finishMigration() {
        runOnUiThread {
            progressBar.visibility = ProgressBar.GONE
            startButton.isEnabled = true

            val message = "🎉 Migration completed!\n" +
                    "✅ Successfully migrated: $successCount\n" +
                    "❌ Failed: $failureCount\n" +
                    "⏭️ Skipped: $skippedCount\n" +
                    "📊 Total processed: $processedRecipes/$totalRecipes"

            statusText.text = message
            Toast.makeText(this@ImageMigrationActivity, "Migration completed!", Toast.LENGTH_LONG).show()

            Log.d("ImageMigration", message)
        }
    }
}