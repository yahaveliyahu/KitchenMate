package dev.yahaveliyahu.kitchenmate

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dev.yahaveliyahu.kitchenmate.model.RecipeMatch
import dev.yahaveliyahu.kitchenmate.ui.recipes.RecipeDetailsActivity
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import android.view.animation.AnimationUtils


// Responsible for displaying recipe details in the list
class RecipeAdapter(
    private val recipeMatches: List<RecipeMatch>,
    private val highlightIngredients: Boolean = true,
    private val extraLine: String? = null,
    private val source: String? = null,
    private val onRecipeClick: ((RecipeMatch) -> Unit)? = null
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    inner class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.recipe_LBL_title)
        val ingredientsTextView: TextView = itemView.findViewById(R.id.recipe_LBL_ingredients)
        val instructionsTextView: TextView = itemView.findViewById(R.id.recipe_LBL_instructions)
        val imageView: ImageView = itemView.findViewById(R.id.recipe_IMG_image)
        val heartImageView: ImageView = itemView.findViewById(R.id.recipe_IMG_heart)
        val matchTextView: TextView = itemView.findViewById(R.id.recipe_LBL_match)
        val addMoreButton: Button? = itemView.findViewById(R.id.button_add_more)
        val timestampTextView: TextView? = itemView.findViewById(R.id.recipe_LBL_timestamp)
    }

    override fun getItemCount(): Int {
        return if (recipeMatches.isEmpty()) 1 else recipeMatches.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (recipeMatches.isEmpty()) -1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            if (viewType == -1) R.layout.item_no_recipe_found else R.layout.activity_recipe_item,
            parent,
            false
        )
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        if (recipeMatches.isEmpty()) {
            holder.titleTextView.text = holder.itemView.context.getString(R.string.no_recipe_found)
            holder.ingredientsTextView.visibility = View.GONE
            holder.instructionsTextView.visibility = View.GONE
            holder.imageView.visibility = View.GONE
            holder.heartImageView.visibility = View.GONE
            holder.matchTextView.visibility = View.GONE
            holder.addMoreButton?.setOnClickListener {
                (it.context as? Activity)?.finish()
            }
            return
        }

        val recipeMatch = recipeMatches[position]
        val recipe = recipeMatch.recipe
        val scorePercent = (recipeMatch.score * 100).roundToInt()

        holder.titleTextView.text = recipe.title

        // Show viewing date if available
        if (holder.timestampTextView != null) {
            val timestamp = recipe.timestamp
            if (timestamp != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val formatted = "🕒 Viewed on ${sdf.format(timestamp.toDate())}"
                holder.timestampTextView.text = formatted
                holder.timestampTextView.visibility = View.VISIBLE
                holder.timestampTextView.setTextColor(Color.BLACK)
            } else if (extraLine != null) {
                holder.timestampTextView.text = extraLine
                holder.timestampTextView.visibility = View.VISIBLE
            } else {
                holder.timestampTextView.visibility = View.GONE
            }
        }

        val allIngredients = recipe.ingredients
        val ingredientsText = allIngredients.joinToString(", ")
        val spannable = SpannableString(ingredientsText)
        var currentPosition = 0

        for (ingredient in allIngredients) {
            val ingredientStart = currentPosition
            val ingredientEnd = currentPosition + ingredient.length

            // Coloring the entire row of the component
            spannable.setSpan(
                ForegroundColorSpan(Color.BLACK),
                ingredientStart,
                ingredientEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            currentPosition += ingredient.length + 2 // Including ", "
        }

        holder.ingredientsTextView.text =
            SpannableStringBuilder("Ingredients:\n ").append(spannable)
        SpannableString(ingredientsText)
        // Displays the ingredients
        holder.ingredientsTextView.text = SpannableStringBuilder(
            holder.itemView.context.getString(R.string.ingredients_header)
        ).append(spannable)

        holder.instructionsTextView.text = holder.itemView.context.getString(
            R.string.instructions_header, recipe.instructions
        )

        // Match score
        if (scorePercent >= 50) {
            holder.matchTextView.visibility = View.VISIBLE
            holder.matchTextView.text = holder.itemView.context.getString(
                R.string.match_percent, scorePercent
            )
        } else {
            holder.matchTextView.visibility = View.GONE
        }

        holder.imageView.let { imageView ->
            Glide.with(imageView.context)
                .load(recipe.imageUrl.takeIf { it.isNotEmpty() })
                .placeholder(R.drawable.placeholder_image) // Appears when loaded
                .error(R.drawable.error_image) // If the URL is incorrect
                .into(imageView)
        }

        // Heart state (favorites)
        val recipeId = recipe.id
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (!recipeId.isBlank() && userId != null) {
            Firebase.firestore.collection("users")
                .document(userId)
                .collection("favorites")
                .document(recipeId)
                .get()
                .addOnSuccessListener { document ->
                    recipe.isLiked = document.exists()
                    val favoriteIcon =
                        if (recipe.isLiked) R.drawable.heart else R.drawable.empty_heart
                    holder.heartImageView.setImageResource(favoriteIcon)
                }
        }

        holder.heartImageView.setOnClickListener {
            val bounceAnim =
                AnimationUtils.loadAnimation(holder.itemView.context, R.anim.scale_bounce)
            holder.heartImageView.startAnimation(bounceAnim)

            if (userId != null) {
                val db = Firebase.firestore

                if (recipe.id.isBlank()) {
                    Toast.makeText(
                        holder.itemView.context,
                        "Recipe ID is missing",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val favRef = db.collection("users").document(userId).collection("favorites")
                    .document(recipe.id)

                val wasLiked = recipe.isLiked
                recipe.isLiked = !recipe.isLiked
                notifyItemChanged(position)

                if (recipe.isLiked) {
                    val data = hashMapOf(
                        "id" to recipe.id,
                        "title" to recipe.title,
                        "ingredients" to recipe.ingredients,
                        "instructions" to recipe.instructions,
                        "imageUrl" to recipe.imageUrl,
                        "category" to recipe.category,
                        "isLiked" to true
                    )
                    favRef.set(data)
                        .addOnSuccessListener {
                            Toast.makeText(
                                holder.itemView.context,
                                "Saved to favorites ❤️",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                holder.itemView.context,
                                "Failed to save",
                                Toast.LENGTH_SHORT
                            ).show()
                            recipe.isLiked = wasLiked // Cancel local update if failed
                            notifyItemChanged(position)
                        }

                } else {
                    favRef.delete()
                        .addOnSuccessListener {
                            Toast.makeText(
                                holder.itemView.context,
                                "Removed from favorites 💔",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                holder.itemView.context,
                                "Failed to remove",
                                Toast.LENGTH_SHORT
                            ).show()
                            recipe.isLiked = wasLiked // Cancel local update if failed
                            notifyItemChanged(position)
                        }
                }
            } else {
                Toast.makeText(
                    holder.itemView.context,
                    "Please sign in to save favorites",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val isSpoonacular = (source == "spoonacular") || recipe.isSpoonacular
        holder.heartImageView.visibility = if (isSpoonacular) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, RecipeDetailsActivity::class.java)

            if (isSpoonacular) {
                val safeRecipe = recipe.copy(timestamp = null) // Sending a full object with a copy without a Timestamp
                intent.putExtra("source", "spoonacular")
                intent.putExtra("recipe", safeRecipe)
                intent.putStringArrayListExtra(
                    "userIngredients",
                    ArrayList(recipeMatch.userIngredients)
                )
                context.startActivity(intent)
                return@setOnClickListener   // Don't let the callback take action
            }

            onRecipeClick?.let { cb ->
                cb(recipeMatch) // Respect the callback that comes from the screen (Recent/All/Favorites/Matched)
                return@setOnClickListener
            }

            // Otherwise – Open by recipeId (Firestore)
            intent.putExtra("recipeId", recipe.id)
            source?.let { intent.putExtra("source", it) }
            intent.putStringArrayListExtra(
                "userIngredients",
                ArrayList(recipeMatch.userIngredients)
            )
            context.startActivity(intent)
        }
    }
}


