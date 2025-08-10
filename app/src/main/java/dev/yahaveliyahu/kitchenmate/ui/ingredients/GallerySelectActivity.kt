package dev.yahaveliyahu.kitchenmate.ui.ingredients

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// This activity allows users to select an image from the gallery
// and recognize ingredients using a TensorFlow Lite model
class GallerySelectActivity : AppCompatActivity() {

    private lateinit var labels: List<String>  // List of labels for the model (labels.txt)
    private lateinit var tflite: Interpreter

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val imageUri: Uri? = data?.data
            if (imageUri != null) {
                try {
                    val imageStream: InputStream? = contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(imageStream)

                    if (bitmap == null) {
                        Toast.makeText(this, "❌ Failed to decode image", Toast.LENGTH_SHORT).show()
                        finish()
                        return@registerForActivityResult
                    }

                    val resized = bitmap.scale(224, 224)
                    val (topLabel, confidence) = recognizeIngredient(resized)

                    if (topLabel.isNotBlank() && confidence > 0.5f) {
                        showConfirmationDialog(topLabel)
                    } else {
                        Toast.makeText(this, "❌ Could not recognize ingredient", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "❌ ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                Toast.makeText(this, "❌ No image selected", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the label list
        labels = assets.open("labels.txt").bufferedReader().readLines()
        // Load the model
        tflite = loadModel()
        // Open the gallery
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun recognizeIngredient(bitmap: Bitmap): Pair<String, Float> {
        val inputBuffer = convertBitmapToByteBuffer(bitmap)
        val outputShape = tflite.getOutputTensor(0).shape()
        val outputSize = outputShape.reduce { acc, dim -> acc * dim }
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        tflite.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val probabilities = FloatArray(outputSize)
        outputBuffer.asFloatBuffer().get(probabilities)
        val topIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

        if (topIndex == -1 || topIndex >= labels.size) return "" to 0.0f
        return labels[topIndex] to probabilities[topIndex]
    }

    private fun loadModel(): Interpreter {
        val modelInputStream = assets.open("model.tflite")
        val modelBytes = modelInputStream.readBytes()
        val byteBuffer = ByteBuffer.allocateDirect(modelBytes.size)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.put(modelBytes)
        byteBuffer.rewind()
        return Interpreter(byteBuffer)
    }

    // Converts a Bitmap to a ByteBuffer for model input
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Extract RGB values and normalize them
        for (pixelValue in intValues) {
            val r = ((pixelValue shr 16) and 0xFF) / 255.0f
            val g = ((pixelValue shr 8) and 0xFF) / 255.0f
            val b = (pixelValue and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        return byteBuffer
    }

    private fun showConfirmationDialog(detectedName: String) {
        val editText = EditText(this)
        editText.setText(detectedName)
        AlertDialog.Builder(this)
            .setTitle("Edit or confirm detected ingredient")
            .setView(editText)
            .setPositiveButton("Confirm") { _, _ ->
                val confirmedName = editText.text.toString().trim()
                val resultIntent = Intent()
                resultIntent.putExtra("scannedIngredient", confirmedName)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }
}
