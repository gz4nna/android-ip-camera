package com.github.digitallyrefined.androidipcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.digitallyrefined.androidipcamera.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import android.view.WindowManager
import android.net.wifi.WifiManager
import android.content.Context
import android.content.Intent
import java.net.Inet4Address
import java.net.NetworkInterface
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import android.util.Base64
import android.util.Size
import android.net.Uri
import androidx.preference.PreferenceManager
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import android.content.SharedPreferences
import android.media.Image
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var serverSocket: ServerSocket? = null
    private var clients = mutableListOf<Client>()
    private var hasRequestedPermissions = false
    // 正在监听自己的电脑ip
    private var pairedComputerIP: String? = null


    data class Client(
        val socket: Socket,
        val outputStream: OutputStream,
        val writer: PrintWriter
    )

    private var lastFrameTime = 0L

    private fun processImage(image: ImageProxy) {
        Log.d("debug","processImage")
        // Get delay from preferences
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val delay = prefs.getString("stream_delay", "33")?.toLongOrNull() ?: 33L

        // Check if enough time has passed since last frame
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameTime < delay) {
            image.close()
            return
        }
        lastFrameTime = currentTime

        // Convert YUV_420_888 to NV21
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        // Convert NV21 to JPEG
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val jpegStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, jpegStream)
        val jpegBytes = jpegStream.toByteArray()

        synchronized(clients) {
            clients.removeAll { client ->
                try {
                    // Send MJPEG frame
                    client.writer.print("--frame\r\n")
                    client.writer.print("Content-Type: image/jpeg\r\n")
                    client.writer.print("Content-Length: ${jpegBytes.size}\r\n\r\n")
                    Log.d("debug", "Sending JPEG frame: size=${jpegBytes.size}")


                    client.writer.flush()
                    client.outputStream.write(jpegBytes)

                    client.socket.getOutputStream().write("\r\n\r\n".toByteArray())

                    client.outputStream.flush()
                    false
                } catch (e: IOException) {
                    Log.e("debug", "Error sending frame: ${e.message}")
                    try {
                        client.socket.close()
                    } catch (e: IOException) {
                        Log.e("debug", "Error closing client: ${e.message}")
                    }
                    true
                }
            }
        }
    }

    private fun handleMaxClients(socket: Socket): Boolean {
        synchronized(clients) {
            if (clients.size >= MAX_CLIENTS) {
                socket.getOutputStream().writer().use { writer ->
                    writer.write("HTTP/1.1 503 Service Unavailable\r\n\r\n")
                    writer.flush()
                }
                socket.close()
                return true
            }
        }
        return false
    }

    private fun startStreamingServer() {
        Log.d("debug","startStreamingServer")
        try {
            Log.d("debug","startTry")
            // Get certificate path from preferences
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            /*val useCertificate = prefs.getBoolean("use_certificate", false)
            val certificatePath = if (useCertificate) prefs.getString("certificate_path", null) else null*/
            /*val certificatePassword = if (useCertificate) {
                prefs.getString("certificate_password", "")?.let {
                    if (it.isEmpty()) null else it.toCharArray()
                }
            } else null*/

            // Create server socket with specific bind address
            /*serverSocket = if (certificatePath != null) {
                // SSL server socket creation code...
                try {
                    val uri = Uri.parse(certificatePath)
                    // Copy the certificate to app's private storage
                    val privateFile = File(filesDir, "certificate.p12")
                    try {
                        // Delete existing certificate if it exists
                        if (privateFile.exists()) {
                            privateFile.delete()
                        }

                        // Copy new certificate
                        contentResolver.openInputStream(uri)?.use { input ->
                            privateFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw IOException("Failed to open certificate file")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy certificate: ${e.message}")
                        throw e
                    }

                    // Use the private copy of the certificate
                    privateFile.inputStream().use { inputStream ->
                        val keyStore = KeyStore.getInstance("PKCS12")
                        keyStore.load(inputStream, certificatePassword)

                        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                        keyManagerFactory.init(keyStore, certificatePassword)

                        val sslContext = SSLContext.getInstance("TLSv1.2")  // Specify TLS version
                        sslContext.init(keyManagerFactory.keyManagers, null, null)

                        val sslServerSocketFactory = sslContext.serverSocketFactory
                        (sslServerSocketFactory.createServerSocket(STREAM_PORT, 50, null) as SSLServerSocket).apply {
                            enabledProtocols = arrayOf("TLSv1.2")
                            enabledCipherSuites = supportedCipherSuites
                            reuseAddress = true
                            soTimeout = 30000  // 30 seconds timeout
                        }
                    } ?: ServerSocket(STREAM_PORT)  // Fallback if inputStream is null
                } catch (e: Exception) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        Log.e(TAG, "Failed to create SSL server socket: ${e.message}")
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to create SSL server socket: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    ServerSocket(STREAM_PORT) // Fallback to regular socket
                }
            } else {
                ServerSocket(STREAM_PORT, 50, null).apply {
                    reuseAddress = true
                    soTimeout = 30000
                }
            }*/
            Log.d("debug","serverSocket")
            serverSocket = ServerSocket(STREAM_PORT, 50, null).apply {
                reuseAddress = true
                soTimeout = 30000
            }

            //startListening4Pairing()

            //Log.i("debug", "Server started on port $STREAM_PORT (${if (certificatePath != null) "HTTPS" else "HTTP"})")
            Log.d("debug","while")
            while (!Thread.currentThread().isInterrupted) {
                Log.d("debug","isInterrupted")
                try {
                    Log.d("debug","socket")
                    val socket = serverSocket?.accept() ?: continue

                    Log.d("debug","serverSocketAccept")
                    // 这里是最大连接数限制
                    if (handleMaxClients(socket)) {
                        continue
                    }

                    val outputStream = socket.getOutputStream()
                    val writer = PrintWriter(outputStream, true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    // 身份验证功能,先注释了
                    // Get auth credentials from preferences using androidx.preference
                    /*val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    val username = prefs.getString("username", "") ?: ""
                    val password = prefs.getString("password", "") ?: ""*/

                    // Check authentication if credentials are set
                    /*if (username.isNotEmpty() && password.isNotEmpty()) {
                        // Read all headers
                        val headers = mutableListOf<String>()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line.isNullOrEmpty()) break
                            headers.add(line!!)
                        }

                        // Look for auth header
                        val authHeader = headers.find { it.startsWith("Authorization: Basic ") }

                        if (authHeader == null) {
                            // No auth provided, send 401
                            writer.print("HTTP/1.1 401 Unauthorized\r\n")
                            writer.print("WWW-Authenticate: Basic realm=\"Android IP Camera\"\r\n")
                            writer.print("Connection: close\r\n\r\n")
                            writer.flush()
                            socket.close()
                            continue
                        }

                        val providedAuth = String(Base64.decode(
                            authHeader.substring(21), Base64.DEFAULT))
                        if (providedAuth != "$username:$password") {
                            // Wrong credentials
                            writer.print("HTTP/1.1 401 Unauthorized\r\n\r\n")
                            writer.flush()
                            socket.close()
                            continue
                        }
                    }*/

                    // 将视频流推到 ip:4747/video 下,保持和 droidcam 一致
                    val requestLine = reader.readLine() ?: continue
                    val requestParts = requestLine.split(" ")
                    if (requestParts.size < 2) continue
                    val requestPath = requestParts[1]
                    Log.d("debug","requestPath")

                    if (requestPath != "/video") {
                        writer.print("HTTP/1.1 404 Not Found\r\n")
                        writer.print("Content-Length: 0\r\n\r\n")
                        writer.flush()
                        socket.close()
                        continue
                    }
                    Log.d("debug","requestPath=video")

                    // Send HTTP headers for video stream
                    writer.print("HTTP/1.0 200 OK\r\n")
                    writer.print("Connection: close\r\n")
                    writer.print("Cache-Control: no-cache\r\n")
                    writer.print("Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n")
                    writer.flush()
                    Log.d("debug","HTTP/1.0 200 OK")

                    synchronized(clients) {
                        clients.add(Client(socket, outputStream, writer))
                    }
                    Log.i("debug", "Client connected")

                    // Get delay from preferences
                    val delay = prefs.getString("stream_delay", "33")?.toLongOrNull() ?: 33L
                    Thread.sleep(delay)
                } catch (e: IOException) {
                    Log.e("debug", "IOException")
                  // Ignore
                } catch (e: InterruptedException) {
                    Log.e("debug", "InterruptedException")
                    Thread.currentThread().interrupt()
                    break
                }
            }
        } catch (e: IOException) {
            Log.e("debug", "Could not start server: ${e.message}")
        }
    }

    // 开始监听6060口等待连接电脑自己发送ip过来
    private fun startListening4Pairing(){
        Thread {
            try {
                val serverSocket = ServerSocket(6060)
                while (true) {
                    val clientSocket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                    val message = reader.readLine()

                    if (message.startsWith("PAIR:")) {
                        pairedComputerIP = message.substringAfter("PAIR:")
                        Log.d("PairingReceiver", "Paired with computer at $pairedComputerIP")
                        findViewById<Button>(R.id.globalDebugText).text = "接受到连接: $pairedComputerIP"
                    }

                    clientSocket.close()
                }
            } catch (e: Exception) {
                Log.e("PairingReceiver", "Error receiving pairing request: ${e.message}")
            }
        }.start()
    }

    private fun closeClientConnection() {
        synchronized(clients) {
            clients.forEach { client ->
                try {
                    client.socket.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing client connection: ${e.message}")
                }
            }
            clients.clear()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize view binding first
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Hide the action bar
        supportActionBar?.hide()

        // Set full screen flags
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request permissions before starting camera
        if (!allPermissionsGranted() && !hasRequestedPermissions) {
            hasRequestedPermissions = true
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        } else if (allPermissionsGranted()) {
            startCamera()
        } else {
            finish()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Start streaming server
        lifecycleScope.launch(Dispatchers.IO) {
            startStreamingServer()
        }

        // Find the TextView
        val ipAddressText = findViewById<TextView>(R.id.ipAddressText)

        // Get and display the IP address with correct protocol
        val ipAddress = getLocalIpAddress()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useCertificate = prefs.getBoolean("use_certificate", false)
        val protocol = if (useCertificate) "https" else "http"
        ipAddressText.text = "$protocol://$ipAddress:$STREAM_PORT"

        // 息屏按钮的事件绑定,王健说不要这个功能,先注释了
        // Add toggle preview button
        /*findViewById<Button>(R.id.hidePreviewButton).setOnClickListener {
            hidePreview()
        }*/

        // Add switch camera button handler
        findViewById<Button>(R.id.switchCameraButton).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            startCamera()
        }

        // Add settings button
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // Add this method to handle permission results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                // Show which permissions are missing
                val missingPermissions = REQUIRED_PERMISSIONS.filter {
                    ContextCompat.checkSelfPermission(baseContext, it) != PackageManager.PERMISSION_GRANTED
                }
                Toast.makeText(this,
                    "Please allow camera permissions",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            // 这里和GS里的识别保持一致
            val preferredPrefixes = listOf("192.168.", "10.", "172.16.")

            val allIps = mutableListOf<String>()

            NetworkInterface.getNetworkInterfaces().toList().forEach { networkInterface ->
                networkInterface.inetAddresses.toList().forEach { address ->
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress ?: return@forEach
                        allIps.add(ip)

                        // 优先返回符合要求的 IP
                        if (preferredPrefixes.any { ip.startsWith(it) }) {
                            return ip
                        }
                    }
                }
            }
            // 如果找不到符合条件的 IP，则返回第一个找到的 IPv4 地址
            return allIps.firstOrNull() ?: "unknown"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "unknown"
    }

    // 息屏,注释掉自己不可见避免找不到恢复按钮
    /*private fun hidePreview() {
        val viewFinder = viewBinding.viewFinder
        val rootView = viewBinding.root
        val ipAddressText = findViewById<TextView>(R.id.ipAddressText)
        val settingsButton = findViewById<Button>(R.id.settingsButton)
        val switchCameraButton = findViewById<TextView>(R.id.switchCameraButton)
        // val hidePreviewButton = findViewById<Button>(R.id.hidePreviewButton)

        if (viewFinder.visibility == View.VISIBLE) {
            viewFinder.visibility = View.GONE
            ipAddressText.visibility = View.GONE
            settingsButton.visibility = View.GONE
            switchCameraButton.visibility = View.GONE
            // hidePreviewButton.visibility = View.GONE
            rootView.setBackgroundColor(android.graphics.Color.BLACK)
        } else {
            viewFinder.visibility = View.VISIBLE
            ipAddressText.visibility = View.VISIBLE
            settingsButton.visibility = View.VISIBLE
            switchCameraButton.visibility = View.VISIBLE
            // hidePreviewButton.visibility = View.VISIBLE
            rootView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }*/

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                // 一直使用CameraX的默认配置不要修改,高分辨率会导致处理出错
                /*.apply {
                    // Get resolution from preferences
                    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                    when (prefs.getString("camera_resolution", "low")) {
                        "high" -> setTargetResolution(android.util.Size(1280, 720))
                        "medium" -> setTargetResolution(android.util.Size(640, 480))
                        // "low" -> don't set resolution, use CameraX default
                    }
                }*/
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { image ->
                        if (clients.isNotEmpty()) {  // Only process if there are clients
                            processImage(image)
                        }
                        image.close()
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    lensFacing,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        serverSocket?.close()
        closeClientConnection()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val STREAM_PORT = 4747
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val MAX_CLIENTS = 1  // Limit concurrent connections
        private val REQUIRED_PERMISSIONS = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    // 点击拍照的时候发送指令到运行GS的电脑的5050口
    fun captureFrameButton_OnClick(view: View) {
        pairedComputerIP?.let {
            Thread {
                try {
                    val socket = Socket(it, 5050)
                    val outputStream = socket.getOutputStream()
                    outputStream.write("CAPTURE".toByteArray())
                    outputStream.flush()
                    socket.close()
                    Log.d("debug", "Capture command sent to $it")
                    findViewById<Button>(R.id.globalDebugText).text = "拍照指令已发送到: $it"
                } catch (e: Exception) {
                    Log.e("debug", "Error sending capture command: ${e.message}")
                    findViewById<Button>(R.id.globalDebugText).text = "发送指令时出错: ${e.message}"
                }
            }.start()
        } ?: Log.e("debug", "No paired computer found")
    }
}
