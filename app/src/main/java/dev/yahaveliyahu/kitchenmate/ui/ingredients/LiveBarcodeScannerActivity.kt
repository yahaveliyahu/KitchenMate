package dev.yahaveliyahu.kitchenmate.ui.ingredients

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CompoundBarcodeView
import dev.yahaveliyahu.kitchenmate.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class LiveBarcodeScannerActivity : AppCompatActivity() {

    private lateinit var barcodeView: CompoundBarcodeView // ZXing component for scanning from camera
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_barcode_scanner)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        barcodeView = findViewById(R.id.barcode_scanner)
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.text?.let { barcode ->
                    barcodeView.pause()
                    handleBarcode(barcode)
                }
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    // First checks if the barcode already exists in Firestore
    private fun handleBarcode(barcode: String) {
        firestore.collection("barcode_cache").document(barcode).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val cachedName = document.getString("name")
                    if (!cachedName.isNullOrBlank()) {
                        confirmProductName(cachedName, barcode)
                    } else {
                        fetchFromOpenFoodFacts(barcode)
                    }
                } else {
                    fetchFromOpenFoodFacts(barcode)
                }
            }
            .addOnFailureListener {
                fetchFromOpenFoodFacts(barcode)
            }
    }

    // If not present or fails – sends to OpenFoodFacts
    private fun fetchFromOpenFoodFacts(barcode: String) {
        runOnUiThread {
            Toast.makeText(this, "🔎 Looking up: $barcode", Toast.LENGTH_SHORT).show()
        }
        // Preparing a web call for OpenFoodFacts
        val client = OkHttpClient()
        val url = "https://world.openfoodfacts.org/api/v0/product/$barcode.json"
        val request = Request.Builder().url(url).build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    runOnUiThread {
                        showError("⚠️ Could not fetch product")
                        askUserToEnterName(barcode)
                    }
                    return@Thread
                }

                val json = JSONObject(body)
                if (json.getInt("status") == 1) {
                    val product = json.getJSONObject("product")
                    val name = product.optString("product_name_he")
                        .ifBlank { product.optString("product_name") }
                        .ifBlank { null }

                    if (name != null) {
                        firestore.collection("barcode_cache").document(barcode)
                            .set(mapOf("name" to name))
                        runOnUiThread { confirmProductName(name, barcode) }
                    } else {
                        runOnUiThread {
                            showError("❌ No name found for this product")
                            askUserToEnterName(barcode)
                        }
                    }
                } else {
                    runOnUiThread {
                        showError("❌ Product not found in OpenFoodFacts")
                        askUserToEnterName(barcode)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    showError("❌ Product not found in OpenFoodFacts")
                    askUserToEnterName(barcode)
                }
            }
        }.start()
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Returns the result to the ManuallyEnteringIngredientsActivity
    private fun sendResult(result: String) {
        val resultIntent = Intent()
        resultIntent.putExtra("scannedIngredient", result)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun confirmProductName(productName: String, barcode: String) {
        val editText = EditText(this)
        editText.setText(productName)

        AlertDialog.Builder(this)
            .setTitle("Confirm or Edit Product Name")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val finalName = editText.text.toString().trim()
                if (finalName.isNotBlank()) {
                    firestore.collection("barcode_cache").document(barcode)
                        .set(mapOf("name" to finalName))
                    sendResult(finalName)
                } else {
                    Toast.makeText(this, "Empty name, not added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    private fun askUserToEnterName(barcode: String) {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Product not found. Enter name:")
            .setView(editText)
            .setPositiveButton("Confirm") { _, _ ->
                val enteredName = editText.text.toString().trim()
                if (enteredName.isNotBlank()) {
                    firestore.collection("barcode_cache").document(barcode)
                        .set(mapOf("name" to enteredName))
                    val resultIntent = Intent()
                    resultIntent.putExtra("scannedIngredient", enteredName)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } else {
                    Toast.makeText(this, "Empty name, not added", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

}