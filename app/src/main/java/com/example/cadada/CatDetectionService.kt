package com.example.cadada

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.Player
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


class CatDetectionService : Service() {

    private lateinit var player: ExoPlayer
    private lateinit var handler: Handler
    private lateinit var interpreter: Interpreter
    private lateinit var surfaceView: SurfaceView

    private var detected = false
    private var currentId = -1

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        loadModel()
        setupForegroundNotification()

        val address = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("rtsp_address", null)

        address?.let {
            setupStream(it)
        } ?: Log.e("CatService", "RTSP 주소 없음")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupForegroundNotification() {
        val channelId = "CatDetectionChannel"
        val channelName = "고양이 감지 서비스"

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("고양이 감지 중")
            .setContentText("스트리밍에서 고양이를 감지하고 있습니다.")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        startForeground(1, notification)
    }


    private fun loadModel() {
        try {
            val modelFile = assets.openFd("model.tflite")
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, modelFile.startOffset, modelFile.declaredLength)
            interpreter = Interpreter(byteBuffer)
        } catch (e: IOException) {
            Log.e("CatService", "모델 로딩 실패: ${e.message}")
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun setupStream(address: String) {
        player = ExoPlayer.Builder(this).build()

        val playerView = android.view.SurfaceView(this)
        surfaceView = playerView

        val mediaItem = MediaItem.fromUri(address.toUri())
        val mediaSource = RtspMediaSource.Factory().createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    handler.postDelayed(object : Runnable {
                        override fun run() {
                            takeScreenshot { bitmap ->
                                if (bitmap != null && !detected) {
                                    runObjectDetection(bitmap)
                                }
                            }
                            handler.postDelayed(this, 2000)
                        }
                    }, 2000)
                }
            }
        })
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
            saveAndSendResult(bitmap)
        } else if (!catDetected && isVerification) {
            deleteDetectionFromServer(currentId)
            detected = false
        } else if (catDetected && isVerification) {
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
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

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
                val json = JSONObject(responseBody)
                currentId = json.getInt("id")

                delay(15_000)
                takeScreenshot { bitmap2 ->
                    if (bitmap2 != null) {
                        runObjectDetection(bitmap2, isVerification = true)
                    } else {
                        detected = false
                    }
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
            } catch (_: Exception) { }
        }
    }

    private fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        val bitmap = createBitmap(surfaceView.width, surfaceView.height)

        PixelCopy.request(surfaceView, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                callback(bitmap)
            } else {
                callback(null)
            }
        }, handler)
    }
}
