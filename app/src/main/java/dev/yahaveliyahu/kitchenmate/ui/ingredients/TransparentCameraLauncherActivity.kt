package dev.yahaveliyahu.kitchenmate.ui.ingredients

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import dev.yahaveliyahu.kitchenmate.R
import dev.yahaveliyahu.kitchenmate.ui.add.OCRScanRecipeActivity
import java.io.File

class TransparentCameraLauncherActivity : AppCompatActivity() {

    lateinit var imageUri: Uri
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparent)

        val rootView = findViewById<View>(android.R.id.content)

        Snackbar.make( rootView,
            "Please take a picture of printed English text only. Handwriting is not supported.",
            Snackbar.LENGTH_LONG ).show()

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = Intent(this, OCRScanRecipeActivity::class.java)
                intent.putExtra("image_uri", imageUri.toString())
                startActivity(intent)
            }
            finish()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val imageFile = File.createTempFile("recipe_", ".jpg", cacheDir)
            imageUri = FileProvider.getUriForFile(this, "${packageName}.provider", imageFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            cameraLauncher.launch(intent)
        }, 2000)
    }
}