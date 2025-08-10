package dev.yahaveliyahu.kitchenmate

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*
import androidx.core.os.BundleCompat
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AlertDialog


typealias UploadCallback = (String?) -> Unit //  Callback type for image upload

class AddRecipeActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView // Will display the selected image
    private var imageUri: Uri? = null // Saves the location of the selected image
    // Two request codes to identify which action was performed: selection from gallery or photo
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Intent>
    private val requestImagePick = 1
    private val requestImageCapture = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_recipe)

        val titleEditText = findViewById<EditText>(R.id.editTextRecipeTitle)
        val categoryEditText = findViewById<EditText>(R.id.editTextCategory)
        val ingredientsEditText = findViewById<EditText>(R.id.editTextIngredients)
        val essentialIngredientsEditText = findViewById<EditText>(R.id.editTextEssentialIngredients)
        val instructionsEditText = findViewById<EditText>(R.id.editTextInstructions)
        val submitButton = findViewById<Button>(R.id.buttonSubmitRecipe)
        val btnSelectImage = findViewById<Button>(R.id.buttonSelectImage)
        val btnTakePhoto = findViewById<Button>(R.id.buttonTakePhoto)
        imageView = findViewById(R.id.imageViewRecipe)
        val tooltipIcon = findViewById<ImageView>(R.id.imgEssentialTooltip)


        // Captures data transferred from OCR and automatically fills in fields
        categoryEditText.setText(intent.getStringExtra("category") ?: "")
        titleEditText.setText(intent.getStringExtra("title") ?: "")
        ingredientsEditText.setText(intent.getStringExtra("ingredients") ?: "")
        essentialIngredientsEditText.setText(intent.getStringExtra("essentialIngredients") ?: "")
        instructionsEditText.setText(intent.getStringExtra("instructions") ?: "")

        // Displays a toast that the fields have been autofilled
        if (!intent.getStringExtra("title").isNullOrEmpty() ||
            !intent.getStringExtra("ingredients").isNullOrEmpty() ||
            !intent.getStringExtra("essentialIngredients").isNullOrEmpty() ||
            !intent.getStringExtra("instructions").isNullOrEmpty()) {
            Toast.makeText(this, "Fields were filled based on scan. You can edit them before submitting.", Toast.LENGTH_LONG).show()
        }

        // Select a photo from the gallery
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                imageUri = result.data?.data
                imageView.setImageURI(imageUri)
            }
        }

        // Taking a picture
        takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val bitmap = result.data?.extras?.let { BundleCompat.getParcelable(it, "data", Bitmap::class.java) }
                bitmap?.let {
                    val uri = getImageUriFromBitmap(it)
                    imageUri = uri
                    imageView.setImageBitmap(it)
                }
            }
        }

        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        btnTakePhoto.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePhotoLauncher.launch(intent)
        }

        submitButton.setOnClickListener {
            val title = titleEditText.text.toString().trim()
            val category = categoryEditText.text.toString().trim()
            val ingredients = ingredientsEditText.text.toString().trim()
            val essentialIngredients = essentialIngredientsEditText.text.toString().trim()
            val instructions = instructionsEditText.text.toString().trim()


            if (title.isEmpty() || category.isEmpty() || ingredients.isEmpty() || essentialIngredients.isEmpty() || instructions.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (imageUri == null) {
                Toast.makeText(this, "Please select an image for the recipe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Identifies the user and decides whether he is an admin based on his email
            val currentUser = FirebaseAuth.getInstance().currentUser
            val isAdmin = currentUser?.email == "yahaveliyahu@gmail.com"
            val folder = if (isAdmin) "recipe_images" else "pending_images"

            // Check for existing recipe with same name (case-insensitive)
            val db = FirebaseFirestore.getInstance()
            val recipesRef = db.collection("recipes")
            val pendingRef = db.collection("pending_recipes")

            recipesRef.whereEqualTo("title", title).get()
                .addOnSuccessListener { recipeDocs ->
                    if (!recipeDocs.isEmpty) {
                        Toast.makeText(this, "A recipe with this name already exists.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    pendingRef.whereEqualTo("title", title).get()
                        .addOnSuccessListener { pendingDocs ->
                            if (!pendingDocs.isEmpty) {
                                Toast.makeText(this, "A recipe with this name is already pending approval.", Toast.LENGTH_LONG).show()
                                return@addOnSuccessListener
                            }
                            // The name is available – continue uploading
                            uploadImageToFirebase(folder) { imageUrl ->
                                if (imageUrl == null) {
                                    Toast.makeText(this, "Image upload failed. Please try again.", Toast.LENGTH_LONG).show()
                                    return@uploadImageToFirebase
                                }

                                saveRecipeToFirestore(
                                    title = title,
                                    category = category,
                                    ingredients = ingredients,
                                    essentialIngredients = essentialIngredients,
                                    instructions = instructions,
                                    imageUrl = imageUrl,
                                    isAdmin = isAdmin
                                )
                            }
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to check for duplicate names", Toast.LENGTH_SHORT).show()
                }
        }

        tooltipIcon.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Essential Ingredients")
                .setMessage("These are the key ingredients required to make the recipe. If any are missing, the recipe might not appear in search results.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun saveRecipeToFirestore(
        title: String,
        category: String,
        ingredients: String,
        essentialIngredients: String,
        instructions: String,
        imageUrl: String?,
        isAdmin: Boolean
    ) {

        val recipe = hashMapOf(
            "title" to title,
            "category" to category,
            "ingredients" to ingredients.split("\n").map { it.trim() }.filter { it.isNotBlank() },
            "essentialIngredients" to essentialIngredients.split("\n").map { it.trim() }.filter { it.isNotBlank() },
            "instructions" to instructions,
            "imageUrl" to imageUrl,
        )

        val collection = if (isAdmin) "recipes" else "pending_recipes"

        FirebaseFirestore.getInstance().collection(collection)
            .add(recipe)
            .addOnSuccessListener {
                val message =
                    if (isAdmin) "The recipe has been added!" else "The recipe has been submitted for administrator approval."
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error uploading recipe", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImageToFirebase(folder: String, callback: UploadCallback) {
        val uri = imageUri ?: return callback(null)
        val ref = FirebaseStorage.getInstance().reference.child("$folder/${UUID.randomUUID()}.jpg")
        ref.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { uri -> callback(uri.toString()) }
            .addOnFailureListener { callback(null) }
    }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == RESULT_OK) {
                when (requestCode) {
                    requestImagePick -> {
                        imageUri = data?.data
                        imageView.setImageURI(imageUri)
                    }

                    requestImageCapture -> {
                        val bitmap = data?.extras?.let { BundleCompat.getParcelable(it, "data", Bitmap::class.java) }
                        bitmap?.let {
                            val uri = getImageUriFromBitmap(it)
                            imageUri = uri
                            imageView.setImageBitmap(it)
                        }
                    }
                }
            }
        }

    // Converts the bitmap to a URI for uploading
    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri {
        val file = File(cacheDir, "temp_recipe.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return FileProvider.getUriForFile(this, "$packageName.provider", file)
    }
}
