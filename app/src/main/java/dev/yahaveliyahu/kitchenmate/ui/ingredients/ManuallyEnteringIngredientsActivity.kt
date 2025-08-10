package dev.yahaveliyahu.kitchenmate.ui.ingredients

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.service.RecipeMatcher
import dev.yahaveliyahu.kitchenmate.ui.recipes.MatchedRecipesActivity

class ManuallyEnteringIngredientsActivity : AppCompatActivity() {

    private lateinit var ingredientDropdown: AutoCompleteTextView
    private lateinit var addIngredientButton: Button
    private lateinit var selectedIngredientsLayout: LinearLayout
    private lateinit var ingredientAdapter: ArrayAdapter<String>
    private lateinit var scanButton: Button
    private val selectedIngredients = mutableListOf<String>()

    companion object {
        const val SCAN_BARCODE_REQUEST = 1001

        val commonIngredients = listOf(
            "Agave", "Apple", "Avocado", "Aged Pecorino", "Almond Meal", "Almond", "Anchovy Fillet", "Asparagus", "Apricot Preserve", "Anchovy",
            "Butter", "Bread", "Beef", "Beans", "Baking Powder", "Black Pepper", "Brown Sugar", "Basil", "Breadcrumbs", "Bell Pepper", "Blueberry",
            "Biscuit", "Banana", "Brie", "Baking Soda", "Buttermilk", "Baby Beet Greens", "Broccoli", "Bay Leaf", "Blackberry", "Chicken",
            "Cucumber", "Cheese", "Carrot", "Corn", "Cooking Cream", "Cottage", "Chicken Broth", "Chocolate", "Cream", "Coriander", "Clove",
            "Cinnamon", "Coffee", "Cumin", "Chocolate Chips", "Cheddar Cheese", "Cherry Tomatoes", "Chili Powder","Chicken Soup", "Croutons",
            "Caesar Dressing", "Cracker", "Cajun Pepper", "Cayenne Pepper", "Chili Sauce", "Corn Flour", "Cornstarch", "Canned Soup", "Celery",
            "Chive", "Cinnamon Powder", "Cauliflower", "Coconut Oil", "Chocolate Cocoa Powder", "Candy", "Cardamom", "Cannellini Beans",
            "Condensed Milk", "Chocolate Cookie", "Coconut", "Coconut Milk", "Coconut Flake", "Chicken Dip", "Date", "Dashi Stock", "Egg",
            "Eggplant", "Egg Yolk", "Egg Noodles", "Espresso", "Flour", "Feta", "Fish Fillet", "Fennel", "Flaxseed", "Garlic", "Garlic Powder",
            "Ginger Powder", "Grape", "Granola", "Green Chili", "Ginger", "Garam Masala", "Green Onion", "Green Chili", "Honey", "Herb",
            "Hamburger Bun", "Hot Sauce", "Hazelnut", "Heavy Cream", "Ham", "Ice Cube", "Jalapeno Pepper", "Kefir", "Ketchup", "Kidney Bean",
            "Kahlua", "Lettuce", "Lemon", "Lemon Juice", "Lemon Peel", "Lime Juice", "Lime", "Leek", "Milk", "Maple Syrup", "Marinara Sauce",
            "Mozzarella", "Mint", "Mirin", "Mayonnaise", "Mustard", "Monterrey Jack Cheese", "Macaroni", "Mixed Vegetables", "Mushroom", "Muffin",
            "Mushroom Soup", "Muenster Cheese", "Noodles", "Nutella Spread", "Onion", "Oil", "Orange", "Orange Juice", "Onion Powder", "Oregano",
            "Oat", "Olive Oil", "Oat Milk", "Pepper", "Potato", "Peanut Butter", "Peanut", "Pasta", "Poppy", "Parmesan", "Parsley", "Pesto",
            "Pine Nuts", "Pecans", "Pea", "Paprika", "Phyllo", "Pomegranate", "Pumpkin Seed", "Panko", "Pie Crust", "Pistachio", "Porcini Mushroom",
            "Polenta", "Rice", "Rosemary", "Rice Vinegar", "Roux", "Red Chili Sauce", "Raisin", "Ricotta", "Romano", "Rosewater", "Rum", "Salt",
            "Sugar", "Soy", "Saffron", "Sour Cream", "Salsa", "Scallion", "Spring Onion", "Spinach", "Shallot", "Strawberry", "Sesame Oil",
            "Spaghetti", "Spelt", "Soy Sauce", "Sub Rolls", "Sunflower Oil", "Simple Syrup", "Swiss Cheese", "Tomato", "Tuna", "Tahini", "Tofu",
            "Tortilla", "Taco", "Tomato Sauce", "Thyme", "Tomato Paste", "Turmeric", "Vanilla", "Vinegar", "Vegetable Broth", "Water", "Wine",
            "Worcestershire Sauce", "Whey Protein", "Walnut", "White Pepper", "Wonton Wrapper", "Whipping Cream", "White Onion", "Yogurt",
            "Yellow Onion", "Zucchini"
        ).sorted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manually_entering_ingredients)

        ingredientDropdown = findViewById(R.id.autoCompleteIngredient)
        addIngredientButton = findViewById(R.id.btnAddIngredient)
        selectedIngredientsLayout = findViewById(R.id.selectedIngredientsList)
        scanButton = findViewById(R.id.btnScanIngredient)
        val findRecipesButton = findViewById<Button>(R.id.btnFindRecipes)

        // Adding a scanned item
        val scannedIngredient = intent.getStringExtra("scannedIngredient")?.trim()
        if (!scannedIngredient.isNullOrEmpty()) {
            val normalized = RecipeMatcher().normalizeIngredient(scannedIngredient)
            if (!selectedIngredients.contains(normalized)) {
                selectedIngredients.add(normalized)
                addIngredientToList(normalized)
                Toast.makeText(this, " Scanned: \"$scannedIngredient\"\n🧪 Normalized: \"$normalized\"", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Already added: $normalized", Toast.LENGTH_SHORT).show()
            }
        }

        // Restoring previously entered ingredients
        val previousIngredients = intent.getStringArrayListExtra("userIngredients")
        if (previousIngredients != null) {
            selectedIngredients.addAll(previousIngredients)
            for (ingredient in previousIngredients) {
                addIngredientToList(ingredient)
            }
        }

        // Go to the scan screen by pressing the button
        scanButton.setOnClickListener {
            val intent = Intent(this, ScanIngredientsActivity::class.java)
            intent.putStringArrayListExtra("userIngredients", ArrayList(selectedIngredients))
            startActivity(intent)
        }


        // Adapter definition with list of ingredients
        ingredientAdapter = ArrayAdapter(this, R.layout.ingredient_dropdown_item, commonIngredients)
        ingredientDropdown.setAdapter(ingredientAdapter)

        ingredientDropdown.setOnClickListener { ingredientDropdown.showDropDown() }

        ingredientDropdown.threshold = 1  // One character is enough

        // Filter while typing
        ingredientDropdown.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}   // Don't need this in the app, but it's part of the TextWatcher interface
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} // Don't need this in the app, but it's part of the TextWatcher interface
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                val typedText = text.toString().trim()
                val filtered = commonIngredients.filter {
                    it.startsWith(typedText, ignoreCase = true)
                }.map {
                    if (selectedIngredients.contains(it)) "$it ✓" else it
                }
                // Create a new adapter with only the filtered results
                ingredientAdapter = ArrayAdapter(
                    this@ManuallyEnteringIngredientsActivity,
                    R.layout.ingredient_dropdown_item,
                    filtered
                )
                ingredientDropdown.setAdapter(ingredientAdapter)
                ingredientDropdown.showDropDown()

                // Notification if no results found
                if (typedText.isNotEmpty() && filtered.isEmpty()) {
                    Toast.makeText(this@ManuallyEnteringIngredientsActivity, "No matching ingredient found", Toast.LENGTH_SHORT).show()
                }
            }
        })
        ingredientDropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) { ingredientDropdown.showDropDown() }
        }
        // Action when adding an ingredient
        addIngredientButton.setOnClickListener {
            val ingredient = ingredientDropdown.text.toString().trim().removeSuffix(" ✓")
            if (ingredient.isEmpty()) {
                Toast.makeText(this, "Please enter an ingredient", Toast.LENGTH_SHORT).show()
            } else if (!commonIngredients.contains(ingredient)) {
                Toast.makeText(this, "No matching ingredient found", Toast.LENGTH_SHORT).show()
            } else if (isAlreadyAdded(ingredient)) {
                Toast.makeText(this, "You have already added this item to the list", Toast.LENGTH_SHORT).show()
            } else {
                addIngredientToList(ingredient)
                selectedIngredients.add(ingredient)
                ingredientDropdown.setText("")
            }
        }
        findRecipesButton.setOnClickListener {
            if (selectedIngredients.isEmpty()) {
                Toast.makeText(this, "Please add at least one ingredient", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, MatchedRecipesActivity::class.java)
                intent.putStringArrayListExtra("userIngredients", ArrayList(selectedIngredients))
                startActivity(intent)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCAN_BARCODE_REQUEST && resultCode == RESULT_OK && data != null) {
            val scannedIngredient = data.getStringExtra("scannedIngredient")?.trim()
            if (!scannedIngredient.isNullOrEmpty()) {
                val normalized = RecipeMatcher().normalizeIngredient(scannedIngredient)
                if (!selectedIngredients.contains(normalized)) {
                    selectedIngredients.add(normalized)
                    addIngredientToList(normalized)
                    Toast.makeText(
                        this,
                        "Scanned: \"$scannedIngredient\"\n🧪 Normalized: \"$normalized\"",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "Already added: $scannedIngredient", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isAlreadyAdded(ingredient: String): Boolean {
        return selectedIngredients.contains(ingredient)
    }

    private fun dpToPx(dp: Int): Int { return (dp * resources.displayMetrics.density).toInt() }

    private fun addIngredientToList(ingredient: String) {
        val container = ConstraintLayout(this).apply {
            id = View.generateViewId()
            tag = ingredient
            setPadding(8, 8, 8, 8)
            setBackgroundResource(R.drawable.ingredient_item_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }

        // Create text with the name of the product
        val textView = TextView(this).apply {
            id = View.generateViewId()
            text = ingredient
            textSize = 18f
            setPadding(16, 16, 16, 16)
        }

        val removeButton = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.x)
            contentDescription = "Remove ingredient"
            setOnClickListener {
                selectedIngredientsLayout.removeView(container)
                selectedIngredients.remove(ingredient)
            }
        }
        container.addView(textView)
        container.addView(removeButton)

        // Connecting the textView constraints
        textView.layoutParams = ConstraintLayout.LayoutParams( 0, ConstraintLayout.LayoutParams.WRAP_CONTENT ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToStart = removeButton.id
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            marginEnd = dpToPx(8)
        }

        // Connecting the constraints of removeButton
        removeButton.layoutParams = ConstraintLayout.LayoutParams( dpToPx(24), dpToPx(24) ).apply {
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }

        selectedIngredientsLayout.addView(container)
    }

}