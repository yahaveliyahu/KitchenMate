package dev.yahaveliyahu.kitchenmate

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class AdminApprovalActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_approval)

        loadNextPendingRecipe()
    }

    private fun loadNextPendingRecipe() {
        db.collection("pending_recipes").limit(1).get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "No recipes pending", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                val doc = result.documents[0]
                val data = doc.data ?: return@addOnSuccessListener

                try {
                    val category = data["category"] as? String ?: ""
                    Log.d("AdminApproval", "Category loaded: $category")
                    val title = data["title"] as? String ?: ""
                    Log.d("AdminApproval", "Title loaded: $title")

                    val ingredients = (data["ingredients"] as? List<*>)?.joinToString("\n") ?: ""
                    Log.d("AdminApproval", "Ingredients loaded: $ingredients")

                    val essentialIngredients = (data["essentialIngredients"] as? List<*>)?.joinToString("\n") ?: ""

                    Log.d("AdminApproval", "Essential Ingredients loaded: $essentialIngredients")

                    val instructions = data["instructions"] as? String ?: ""
                    Log.d("AdminApproval", "Instructions loaded: $instructions")

                    val imageUrl = data["imageUrl"] as? String

                    Log.d("AdminApproval", "Fields loaded: $title, $category, ${ingredients.length} chars, etc.")

                    findViewById<TextView>(R.id.textCategory).text = category
                    findViewById<TextView>(R.id.textTitle).text = title
                    findViewById<TextView>(R.id.textIngredients).text = ingredients
                    findViewById<TextView>(R.id.textEssentialIngredientsTitle).text = essentialIngredients
                    findViewById<TextView>(R.id.textInstructions).text = instructions

                    val imageView = findViewById<ImageView>(R.id.imageViewPreview)
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(imageUrl).into(imageView)
                    }

                    findViewById<Button>(R.id.buttonApprove).setOnClickListener {
                        approveRecipe(doc.id, data)
                    }
                    findViewById<Button>(R.id.buttonReject).setOnClickListener {
                        rejectRecipe(doc.id, imageUrl)
                    }
                } catch (e: Exception) {
                    Log.e("AdminApproval", "⚠️ Exception during recipe parsing: ${e.message}", e)
                    Toast.makeText(this, "Error loading recipe", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("AdminApproval", "Error fetching pending recipe: ${e.message}", e)
                Toast.makeText(this, "Error fetching recipe", Toast.LENGTH_SHORT).show()
            }
    }

    private fun approveRecipe(docId: String, data: Map<String, Any>) {
        val imageUrl = data["imageUrl"] as? String
        if (imageUrl != null && imageUrl.contains("pending_images")) {
            val newRef = storage.reference.child("recipe_images/${imageUrl.substringAfterLast('/')}")
            val oldRef = storage.getReferenceFromUrl(imageUrl)
            oldRef.getBytes(5 * 1024 * 1024).addOnSuccessListener { bytes ->
                val uploadTask = newRef.putBytes(bytes)
                uploadTask.continueWithTask { newRef.downloadUrl }.addOnSuccessListener { finalUrl ->
                    saveToRecipes(docId, data, finalUrl.toString())
                }.addOnFailureListener {
                    Toast.makeText(this, "Error transferring image", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to download original image", Toast.LENGTH_SHORT).show()
            }

        } else {
            saveToRecipes(docId, data, imageUrl)
        }
    }

    private fun saveToRecipes(docId: String, data: Map<String, Any>, imageUrl: String?) {
        val recipe = hashMapOf(
            "title" to data["title"],
            "category" to data["category"],
            "ingredients" to data["ingredients"],
            "essentialIngredients" to data["essentialIngredients"],
            "instructions" to data["instructions"],
            "imageUrl" to imageUrl,
            "approved" to true
        )
        db.collection("recipes").add(recipe).addOnSuccessListener {
            db.collection("pending_recipes").document(docId).delete()
            Toast.makeText(this, "Recipe approved!", Toast.LENGTH_SHORT).show()
            loadNextPendingRecipe()
        }
    }

    private fun rejectRecipe(docId: String, imageUrl: String?) {
        db.collection("pending_recipes").document(docId).delete()
        if (!imageUrl.isNullOrEmpty() && imageUrl.contains("pending_images")) {
            val ref = storage.getReferenceFromUrl(imageUrl)
            ref.delete().addOnSuccessListener {
                Toast.makeText(this, "Recipe rejected and image deleted", Toast.LENGTH_SHORT).show()
                loadNextPendingRecipe()
            }.addOnFailureListener {
                Toast.makeText(this, "The recipe was rejected but the image could not be deleted", Toast.LENGTH_SHORT).show()
                loadNextPendingRecipe()
            }
        } else {
            Toast.makeText(this, "Recipe rejected", Toast.LENGTH_SHORT).show()
            loadNextPendingRecipe()
        }
    }
}
