package com.example.cadada

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.cadada.databinding.ActivityLiveSnapshotBinding
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.*
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*

class LiveSnapshotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveSnapshotBinding
    private lateinit var player: ExoPlayer
    private lateinit var handler: Handler
    private lateinit var interpreter: Interpreter

    private var detected = false
    private var currentId = -1
    private var currentSnapshotPath = ""
    private var screenshotRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveSnapshotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handler = Handler(Looper.getMainLooper())
        loadModel()

        binding.btnSaveAddress.setOnClickListener {
            val address = binding.etAddress.text.toString()
            if (address.isNotBlank()) {
                getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("rtsp_address", address)
                    .apply()
                Toast.makeText(this, "주소 저장 완료", Toast.LENGTH_SHORT).show()
                setupStream(address)
            }
        }
        val backButton: Button = findViewById(R.id.btn_back)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        val savedAddress = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("rtsp_address", null)

        savedAddress?.let {
            binding.etAddress.setText(it)
            setupStream(it)
        }
    }

    @OptIn(UnstableApi::class)
    private fun setupStream(address: String) {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        val mediaItem = MediaItem.fromUri(address.toUri())
        val mediaSource = RtspMediaSource.Factory().createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.progressbar.visibility = if (isPlaying) View.GONE else View.VISIBLE
                if (isPlaying && screenshotRunnable == null) {
                    screenshotRunnable = object : Runnable {
                        override fun run() {
                            takeScreenshotFromPlayerView { bitmap ->
                                if (bitmap != null && !detected) {
                                    runObjectDetection(bitmap)
                                }
                            }
                            handler.postDelayed(this, 2000)
                        }
                    }
                    handler.postDelayed(screenshotRunnable!!, 2000)
                }
            }
        })
    }

    private fun loadModel() {
        try {
            val modelFile = assets.openFd("model.tflite")
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, modelFile.startOffset, modelFile.declaredLength)
            interpreter = Interpreter(byteBuffer)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "모델 로딩 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runObjectDetection(bitmap: Bitmap, isVerification: Boolean = false) {
        val image = TensorImage.fromBitmap(bitmap)

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.5f)
            .build()

        val detector = ObjectDetector.createFromFileAndOptions(this, "model.tflite", options)
        val results = detector.detect(image)

        var catDetected = false
        for (obj in results) {
            for (category in obj.categories) {
                if (category.label.equals("cat", ignoreCase = true) && category.score > 0.5f) {
                    catDetected = true
                    break
                }
            }
        }

        if (catDetected && !detected) {
            detected = true
            Toast.makeText(this, "고양이 인식됨", Toast.LENGTH_SHORT).show()
            saveAndSendResult(bitmap)
        } else if (!catDetected && isVerification) {
            Toast.makeText(this, "재확인 결과: 고양이 없음", Toast.LENGTH_SHORT).show()
            deleteDetectionFromServer(currentId)
            detected = false
        } else if (catDetected && isVerification) {
            Toast.makeText(this, "고양이 유지, 20초 후 재감지", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.Main).launch {
                delay(20_000)
                detected = false
            }
        }
    }

    private fun saveAndSendResult(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            val fileName = "snapshot_${System.currentTimeMillis()}.png"
            val file = File(cacheDir, fileName)
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()

            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("date", currentDate)
                .addFormDataPart("time", currentTime)
                .addFormDataPart("snapshot", file.name, file.asRequestBody("image/png".toMediaTypeOrNull()))
                .build()

            val request = Request.Builder()
                .url("http://10.0.2.2:3000/api/detect")
                .post(requestBody)
                .build()

            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                try {
                    val json = JSONObject(responseBody)
                    val id = json.getInt("id")
                    currentId = id
                    currentSnapshotPath = file.absolutePath

                    delay(15_000)
                    takeScreenshotFromPlayerView { bitmap2 ->
                        if (bitmap2 != null) {
                            runObjectDetection(bitmap2, isVerification = true)
                        } else {
                            detected = false
                        }
                    }
                } catch (e: Exception) {
                    detected = false
                }
            } else {
                detected = false
            }
        }
    }

    private fun deleteDetectionFromServer(id: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("id", id.toString())
                .build()

            val request = Request.Builder()
                .url("http://10.0.2.2:3000/api/verify-detection")
                .post(requestBody)
                .build()

            val client = OkHttpClient()
            try {
                client.newCall(request).execute()
            } catch (_: Exception) {}
        }
    }

    private fun takeScreenshotFromPlayerView(callback: (Bitmap?) -> Unit) {
        val surfaceView = findSurfaceView(binding.playerView)
        if (surfaceView == null) {
            callback(null)
            return
        }

        val bitmap = createBitmap(surfaceView.width, surfaceView.height)

        PixelCopy.request(surfaceView, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                callback(bitmap)
            } else {
                callback(null)
            }
        }, handler)
    }

    private fun findSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) {
            val result = findSurfaceView(view.getChildAt(i))
            if (result != null) return result
        }
        return null
    }
}