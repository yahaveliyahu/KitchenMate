package dev.yahaveliyahu.kitchenmate

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.yahaveliyahu.kitchenmate.ui.add.AddRecipeWayChoosingActivity
import dev.yahaveliyahu.kitchenmate.ui.ingredients.WayChoosingIngredientsActivity
import dev.yahaveliyahu.kitchenmate.ui.recipes.AllRecipesActivity
import dev.yahaveliyahu.kitchenmate.ui.recipes.FavoriteRecipesActivity
import dev.yahaveliyahu.kitchenmate.ui.recipes.RecentRecipesActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.buttonLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val textGreeting = findViewById<TextView>(R.id.textGreeting)
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "User"
        textGreeting.text = getString(R.string.hi_what_would_you_like_to_do_today, name)

        val btnFavorites = findViewById<Button>(R.id.btnFavorites)
        val btnRecent = findViewById<Button>(R.id.btnRecent)
        val btnShowAllRecipes = findViewById<Button>(R.id.btnShowAllRecipes)
        val btnAddRecipe = findViewById<Button>(R.id.btnAddRecipe)
        val btnFindFromIngredients = findViewById<Button>(R.id.btnFindFromIngredients)
        val btnApproveRecipes = findViewById<Button>(R.id.btnApproveRecipes)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        // Checking if the user is the administrator
        val isAdmin = user?.email == "yahaveliyahu@gmail.com"
        if (isAdmin) {
            btnApproveRecipes.visibility = View.VISIBLE
            Toast.makeText(this, "Welcome admin!", Toast.LENGTH_SHORT).show()
            val db = FirebaseFirestore.getInstance()
            // Checking if there are any pending recipes
            db.collection("pending_recipes").get().addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    Toast.makeText(this, "There is a new recipe waiting for approval", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "No recipes pending approval", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnFavorites.setOnClickListener {
            startActivity(Intent(this, FavoriteRecipesActivity::class.java))
        }

        btnRecent.setOnClickListener {
            startActivity(Intent(this, RecentRecipesActivity::class.java))
        }

        btnShowAllRecipes.setOnClickListener {
            val intent = Intent(this, AllRecipesActivity::class.java)
            startActivity(intent)
        }

        btnAddRecipe.setOnClickListener {
            try {
                startActivity(Intent(this, AddRecipeWayChoosingActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Error running activity: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }


        btnFindFromIngredients.setOnClickListener {
            startActivity(Intent(this, WayChoosingIngredientsActivity::class.java))
        }

        btnApproveRecipes.setOnClickListener {
            startActivity(Intent(this, AdminApprovalActivity::class.java))
        }

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, dev.yahaveliyahu.kitchenmate.ui.auth.LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // This ensures that if someone logs out, they cannot enter MainActivity without logging in again
    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser == null) {
            val intent = Intent(this, dev.yahaveliyahu.kitchenmate.ui.auth.LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}