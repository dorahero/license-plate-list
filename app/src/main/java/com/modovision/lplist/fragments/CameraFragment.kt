/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.modovision.lplist.fragments

//import androidx.camera.view.TextureViewMeteringPointFactory

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture.Metadata
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.modovision.lplist.KEY_EVENT_ACTION
import com.modovision.lplist.KEY_EVENT_EXTRA
import com.modovision.lplist.MainActivity
import com.modovision.lplist.R
import com.modovision.lplist.utils.ANIMATION_FAST_MILLIS
import com.modovision.lplist.utils.ANIMATION_SLOW_MILLIS
import com.modovision.lplist.utils.CustomGrid
import com.modovision.lplist.utils.simulateClick
import detection.env.Utils
import detection.tflite.Classifier
import detection.tflite.YoloV5Classifier
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.android.synthetic.main.fragment_mask.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign


/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment() {


    private lateinit var text2: ArrayList<String>
    private lateinit var imageId2 : ArrayList<Bitmap>

    private var  MINIMUM_CONFIDENCE_TF_OD_API = 0.4f

    val TF_OD_API_INPUT_SIZE = 320

    private val TF_OD_API_IS_QUANTIZED = true

    private val TF_OD_API_MODEL_FILE = "lp_flag-fp16_320.tflite"

    private val TF_OD_API_LABELS_FILE = "file:///android_asset/lplabel.txt"


    private var detector: Classifier? = null

    private lateinit var cropBitmap: Bitmap
    private lateinit var copyBitmap: Bitmap
    private lateinit var backBitmap: Bitmap

    private lateinit var lpNum: String



//    private fun pixelate(bitmap: Bitmap, zoneWidth: Int, left: Int, top: Int, right: Int, bottom: Int){
//        val w = bitmap.width
//        val h = bitmap.height
////        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//        val result = bitmap
//        val canvas = Canvas(result)
//        val paint = Paint()
//        var i = left
//        while (i < right) {
//            var j = top
//            while (j < bottom) {
//                val color = bitmap.getPixel(i, j)
//                paint.color = color
//                val gridRight = Math.min(w, i + zoneWidth)
//                val gridBottom = Math.min(h, j + zoneWidth)
//                canvas.drawRect(i.toFloat(), j.toFloat(), gridRight.toFloat(), gridBottom.toFloat(), paint)
//                j += zoneWidth
//            }
//            i += zoneWidth
//        }
//    }

//    fun onTouch(x:Float, y:Float) {
//        val meteringPoint = DisplayOrientedMeteringPointFactory(viewFinder.getDisplay(), camera.cameraInfo, viewFinder.getWidth().toFloat(), viewFinder.getHeight().toFloat()).createPoint(x,y);
//
//        // Prepare focus action to be triggered.
//        val action = FocusMeteringAction.Builder(meteringPoint).build();
//
//        // Execute focus action
//        camera?.cameraControl?.startFocusAndMetering(action);
//    }

    fun handleResult(bitmap: Bitmap, results: List<Classifier.Recognition>) {
        var tmp = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val canvasO = Canvas(bitmap)
        val paint = Paint()
        paint.setColor(Color.BLUE)
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(2.0f)
        val paint2 = Paint()
        paint2.setColor(Color.WHITE)
        paint2.setStyle(Paint.Style.FILL)
        paint2.textSize = 35f
        paint2.strokeWidth = 5f
        val paint3 = Paint()
        paint3.setColor(Color.BLUE)
        paint3.setStyle(Paint.Style.FILL)
        paint3.setStrokeWidth(2.0f)



        val bitmapHeight = bitmap.height.toDouble()
        val bitmapWidth = bitmap.width.toDouble()
        if (bitmapHeight > bitmapWidth) {
            oHeight = TF_OD_API_INPUT_SIZE.toFloat()
            oWidth = ((bitmapWidth / bitmapHeight)*TF_OD_API_INPUT_SIZE).toFloat()
        } else {
            oWidth = TF_OD_API_INPUT_SIZE.toFloat()
            oHeight = ((bitmapHeight / bitmapWidth)*TF_OD_API_INPUT_SIZE).toFloat()
        }
        if (results.isEmpty()) {
            this.requireActivity().runOnUiThread {
//                val fOut = FileOutputStream(createFile(outputDirectory, FILENAME, PHOTO_EXTENSION))
//
//                copyBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut)
//                fOut.flush();
//                fOut.close();
//
//                outputDirectory = MainActivity.getOutputDirectory(requireContext())
//                lifecycleScope.launch(Dispatchers.IO) {
//                    outputDirectory.listFiles { file ->
//                        EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
//                    }?.max()?.let {
//                        setGalleryThumbnail(Uri.fromFile(it))
//                    }
//                }
                hideProgressBar()
                copyBitmap.recycle()
            }
        }
        if (results.isEmpty()) {
            this.requireActivity().runOnUiThread {
//                val fOut = FileOutputStream(createFile(outputDirectory, FILENAME, PHOTO_EXTENSION))
//
//                copyBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut)
//                fOut.flush();
//                fOut.close();
//
//                outputDirectory = MainActivity.getOutputDirectory(requireContext())
//                lifecycleScope.launch(Dispatchers.IO) {
//                    outputDirectory.listFiles { file ->
//                        EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
//                    }?.max()?.let {
//                        setGalleryThumbnail(Uri.fromFile(it))
//                    }
//                }
                hideProgressBar()
                copyBitmap.recycle()
            }
        }
        for ((i, result) in results.withIndex()) {

//            khttp.post(
//                    url = "http://172.16.16.40:8081/WhatTheMask/insert_log",
//                    json = mapOf("label" to result.title.toString(), "latitude" to "23", "longitude" to "33"))
            val location: RectF = result.location
//            System.out.println(result);
            if (result.confidence >= MINIMUM_CONFIDENCE_TF_OD_API) {
                val location2 = RectF((location.left * (bitmapWidth / oWidth)).toFloat(),
                        (location.top * (bitmapHeight / oHeight)).toFloat(),
                        (location.right * (bitmapWidth / oWidth)).toFloat(),
                        (location.bottom * (bitmapHeight / oHeight)).toFloat())
                canvasO.drawRect(location2, paint)

                val srcRect = Rect((location.left * (bitmapWidth / oWidth)).toInt(),
                        (location.top * (bitmapHeight / oHeight)).toInt(),
                        (location.right * (bitmapWidth / oWidth)).toInt(),
                        (location.bottom * (bitmapHeight / oHeight)).toInt())
//                val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
//                val canvas = Canvas(output)
//                canvas.drawBitmap(bitmap, srcRect, srcRect, null)
//                val image = InputImage.fromBitmap(output, 0)

                val recognizer = TextRecognition.getClient()
                var output2 = Bitmap.createBitmap(srcRect.width(), srcRect.height(), Bitmap.Config.ARGB_8888)
                val canvas2 = Canvas(output2)
                canvas2.drawBitmap(tmp, srcRect, Rect(0, 0, srcRect.width(), srcRect.height()), null)
                output2 = resizeBitmap(output2, 250, 100)
                val image = InputImage.fromBitmap(output2, 0)


                recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            // Task completed successfully
                            if (visionText.text.length in 4..10) {
                                lpNum = visionText.text.toUpperCase().replace('O', '0').replace('&', 'Q').replace('|', '1')
                                val re = Regex("[^A-Z0-9 ]")
                                lpNum = re.replace(lpNum, "") // works
//                                if (visionText.text.length > 7) {
//                                    lpNum
//                                }
//                                println(lpNum)
//                                System.out.println(user.lp)
//                                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN).format(System.currentTimeMillis())
//                                System.out.println(user)

//                                textLp.append(lpNum + " || " + date + "\n")
                                val location3 = RectF(location2.left,
                                        location2.top - paint2.textSize,
                                        location2.left + paint2.textSize / 2 * (visionText.text.length + 2),
                                        location2.top)
                                canvasO.drawRect(location3, paint3)
                                canvasO.drawText(lpNum, location2.left, location2.top, paint2)
                                this.requireActivity().runOnUiThread {
                                    if (itnumber.hasNext()) {
                                        imageId2[itnumber.next() as Int] = output2
                                        text2[itnumber.next() as Int] = lpNum
                                    } else {
                                        itnumber = numbers.iterator() //返回一個Iterator介面的子類物件
                                        text2 = ArrayList<String>()
                                        imageId2 = ArrayList<Bitmap>()
                                        val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                        for (i in 0..35) {
                                            imageId2.add(empty)
                                            text2.add("")
                                        }
                                        imageId2[itnumber.next() as Int] = output2
                                        text2[itnumber.next() as Int] = lpNum
                                    }
                                    val adapter = CustomGrid(requireContext(), text2, imageId2)
                                    val grid = container.findViewById(R.id.grid) as GridView
                                    grid.setAdapter(adapter)
//                                    grid.setOnItemClickListener(AdapterView.OnItemClickListener { parent, view, position, id ->
//                                        Toast.makeText(requireContext(), text2[+position], Toast.LENGTH_SHORT).show()
//                                    })

                                    //                img1.setImageBitmap(bitmap)
                                }

                            }
                            if (i+1 == results.size) {
                                this.requireActivity().runOnUiThread {
                                    val fOut = FileOutputStream(createFile(outputDirectory, FILENAME, PHOTO_EXTENSION))

                                    copyBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut)
                                    fOut.flush();
                                    fOut.close();

                                    outputDirectory = MainActivity.getOutputDirectory(requireContext())
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        outputDirectory.listFiles { file ->
                                            EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
                                        }?.max()?.let {
                                            setGalleryThumbnail(Uri.fromFile(it))
                                        }
                                    }
                                    hideProgressBar()
                                    copyBitmap.recycle()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            // Task failed with an exception
                            println("353")
                        }
            }
        }
    }
    // Method to resize a bitmap programmatically
    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int):Bitmap{
        return Bitmap.createScaledBitmap(
                bitmap,
                width,
                height,
                false
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private lateinit var numbers:MutableList<Int>
    private lateinit var itnumber: Iterator<*>

    private lateinit var cameraButton: ImageButton
    private lateinit var textLp: TextView

    private lateinit var bitmap: Bitmap

    private lateinit var container: ConstraintLayout
    private lateinit var progressbar:ProgressBar
    lateinit var adView : AdView

    private lateinit var viewFinder: PreviewView
    private lateinit var outputDirectory: File
    private lateinit var broadcastManager: LocalBroadcastManager

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var oHeight:Float = 0.0f
    private var oWidth:Float = 0.0f

    private var tmpOrientation = arrayListOf<Int>()
    private var initOrientation = 210

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val shutter = container
                            .findViewById<ImageButton>(R.id.camera_capture_button)
                    shutter.simulateClick()
                }
            }
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    CameraFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_camera, container, false)

    private fun setGalleryThumbnail(uri: Uri) {
        // Reference of the view that holds the gallery thumbnail
        val thumbnail = container.findViewById<ImageButton>(R.id.photo_view_button)

        // Run the operations in the view's thread
        thumbnail.post {

            // Remove thumbnail padding
            thumbnail.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

            // Load thumbnail into circular button using Glide
            Glide.with(thumbnail)
                    .load(uri)
                    .apply(RequestOptions.circleCropTransform())
                    .into(thumbnail)
        }
    }

    fun isScreenOriatationPortrait(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private val sensorListener = object:SensorEventListener {
        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

        override fun onSensorChanged(event: SensorEvent?) {
            if (event != null) {
                tmpOrientation = arrayListOf()
                tmpOrientation.add(event.values[0].toInt())
                tmpOrientation.add(event.values[1].toInt())
                tmpOrientation.add(event.values[2].toInt())
//                println(tmpOrientation)
                if (tmpOrientation[1].toInt() < -80 && tmpOrientation[2] in -10..10) {
                    initOrientation = tmpOrientation[0]
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // sensor
        val sensorManager: SensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_ORIENTATION)
        numbers = ArrayList()

        for (i in 0..35) {
            numbers.add(i)
        }

        itnumber = numbers.iterator() //返回一個Iterator介面的子類物件
        text2 = ArrayList<String>()
        imageId2 = ArrayList<Bitmap>()
        val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        for (i in 0..35) {
            imageId2.add(empty)
            text2.add("")
        }


        container = view as ConstraintLayout


        viewFinder = container.findViewById(R.id.view_finder)


        textLp = container.findViewById(R.id.textLpView)



        textLp.movementMethod = ScrollingMovementMethod.getInstance()

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        // Wait for the views to be properly laid out

        viewFinder.post {

            // Keep track of the display in which this view is attached
            displayId = viewFinder.display.displayId

            // Build UI controls
            updateCameraUi()

            // Set up the camera and its use cases
            setUpCamera()
        }

    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)


        // Redraw the camera UI controls
        updateCameraUi()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }
//    inline fun View.afterMeasured(crossinline block: () -> Unit) {
//        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
//            override fun onGlobalLayout() {
//                if (measuredWidth > 0 && measuredHeight > 0) {
//                    viewTreeObserver.removeOnGlobalLayoutListener(this)
//                    block()
//                }
//            }
//        })
//    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    @SuppressLint("ClickableViewAccessibility")
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))

        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = camera?.cameraInfo?.zoomState?.value.toString().toFloat() * detector.scaleFactor
                camera?.cameraControl?.setZoomRatio(scale)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(context, listener)

        (view_finder.getChildAt(0) as? TextureView)?.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

//        view_finder.setOnTouchListener { v, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN -> {
//                }
//                MotionEvent.ACTION_MOVE -> {
//                }
//                MotionEvent.ACTION_UP -> {
//                }
//                else -> {
//                }
//            }
//            true //还回为true,说明事件已经完成了，不会再被其他事件监听器调用
//        }
//        view_finder.setOnTouchListener(object : View.OnTouchListener)
//        if (viewFinder != null) {
//            viewFinder.afterMeasured {
//                viewFinder.setOnTouchListener { _, event ->
//                    return@setOnTouchListener when (event.action) {
//                        MotionEvent.ACTION_DOWN -> {
//                            true
//                        }
//                        MotionEvent.ACTION_UP -> {
//                            val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
//                                    viewFinder.width.toFloat(), viewFinder.height.toFloat()
//                            )
//                            val autoFocusPoint = factory.createPoint(event.x, event.y)
//                            try {
//                                camera?.cameraControl?.startFocusAndMetering(
//                                        FocusMeteringAction.Builder(
//                                                autoFocusPoint,
//                                                FocusMeteringAction.FLAG_AF
//                                        ).apply {
//                                            //focus only when the user tap the preview
//                                            disableAutoCancel()
//                                        }.build()
//                                )
//                            } catch (e: CameraInfoUnavailableException) {
//                                Log.d("ERROR", "cannot access camera", e)
//                            }
//                            true
//                        }
//                        else -> false // Unhandled event.
//                    }
//                }
//            }
//        }
//        val camera = cameraProvider?.bindToLifecycle(
//                this,
//                CameraSelector.DEFAULT_BACK_CAMERA)
//        if (camera != null) {
//            setupTapForFocus(camera.cameraControl)
//        }
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewFinder.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation
                .setTargetRotation(rotation)
                .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                // We request aspect ratio but no resolution to match preview config, but letting
                // CameraX optimize for whatever specific resolution best fits our use cases
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        // Values returned from our analyzer are passed to the attached listener
                        // We log image analysis results here - you should do something useful
                        // instead!
                        Log.d(TAG, "Average luminosity: $luma")
                    })
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)


//            val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
//                override fun onScale(detector: ScaleGestureDetector?): Boolean {
//                    val scaleDrawable = camera!!.cameraInfo.zoomState.observe(viewLifecycleOwner, {})*detector?.scaleFactor
//                    camera!!.cameraControl.setZoomRatio(scaleDrawable)
//                }
//            }
//
//            val scaleDetector = ScaleGestureDetector(context, listener)
//            fun onTouchEvent(event: MotionEvent) : Boolean {
//                scaleDetector.onTouchEvent(event)
//                return true
//            }

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {

        val grid = container.findViewById(R.id.grid) as GridView
        grid.columnWidth = (grid.width/4).toInt()

//        itnumber = numbers.iterator() //返回一個Iterator介面的子類物件
//        text2 = ArrayList<String>()
//        imageId2 = ArrayList<Bitmap>()
//        val empty = Bitmap.createBitmap(1,1, Bitmap.Config.ARGB_8888)
//        for (i in 0..35) {
//            imageId2.add(empty)
//            text2.add("")
//        }
//
//        // Remove previous UI if any
//        container.findViewById<ConstraintLayout>(R.id.camera_ui_container)?.let {
//            container.removeView(it)
//        }

        // Inflate a new view containing all UI for controlling the camera
        val controls = View.inflate(requireContext(), R.layout.camera_ui_container, container)

        adView = controls.findViewById<View>(R.id.adView) as AdView
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        progressbar = controls.findViewById(R.id.progressbar)

        cameraButton = controls.findViewById<ImageButton>(R.id.camera_capture_button)

        progressbar.visibility = View.INVISIBLE


        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
            }?.max()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }

        // Listener for button used to capture photo
        cameraButton.setOnClickListener {
//            // sensor
//            val sensorManager: SensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
//            val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
//            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
//            val orientation = FloatArray(3)
//            println(SensorManager.getOrientation(, orientation))

            showProgressBar()
            // Get a stable reference of the modifiable image capture use case
            imageCapture?.let { imageCapture ->



                // Create output file to hold the image
                val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

                // Setup image capture metadata
                val metadata = Metadata().apply {

                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }

                // Create output options object which contains file + metadata
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                        .setMetadata(metadata)
                        .build()

                //do on bitmap
                imageCapture.takePicture(cameraExecutor, object :
                        ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {


                        //get bitmap from image
                        bitmap = imageProxyToBitmap(image)
                        //safe auto rotation
                        bitmap = view_finder.bitmap!!

                        if (tmpOrientation[2] !in -60..60) {
                            var sign = 0
                            sign = if (tmpOrientation[2] > 60) {
                                -1
                            } else {
                                1
                            }

                            val matrix = Matrix()

                            matrix.postRotate(sign * 90F)

                            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, true)

                            bitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, matrix, true)
                        }
//                        val matrix = Matrix()
//
//                        matrix.postRotate(90F)
//                        val bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        val max = max(bitmap.height, bitmap.width)
                        backBitmap = Bitmap.createBitmap(max, max, bitmap.config)

                        val canvas = Canvas(backBitmap)
                        canvas.drawBitmap(bitmap, Matrix(), null)

                        copyBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                        cropBitmap = Utils.processBitmap(backBitmap, TF_OD_API_INPUT_SIZE)
                        try {
                            detector = YoloV5Classifier.create(
                                    requireActivity().assets,
                                    TF_OD_API_MODEL_FILE,
                                    TF_OD_API_LABELS_FILE,
                                    TF_OD_API_IS_QUANTIZED,
                                    TF_OD_API_INPUT_SIZE)
//                val results: List<Classifier.Recognition> = detector.recognizeImage(cropBitmap)
//                handleResult(copyBitmap, results)
                        } catch (e: IOException) {
                        }

//                        Looper.prepare();
//
//                        Toast.makeText(context, "test", Toast.LENGTH_LONG).show();
//
//                        Looper.loop();
//                        val handler = Handler()
//
//                        Thread(Runnable {
//                            val results: List<Classifier.Recognition> = detector!!.recognizeImage(cropBitmap)
//                            handler.post { handleResult(copyBitmap, results) }
//                        }).start()


//                        val results: List<Classifier.Recognition> = detector!!.recognizeImage(cropBitmap)
                        handleResult(copyBitmap, detector!!.recognizeImage(cropBitmap))


//                        val fOut = FileOutputStream(photoFile)
//
//                        copyBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut)

                        super.onCaptureSuccess(image)
                        image.close()
                        cropBitmap.recycle()
                        backBitmap.recycle()
                        bitmap.recycle()

                    }

                    override fun onError(exception: ImageCaptureException) {
                        super.onError(exception)
                    }

                })

//                 Setup image capture listener which is triggered after photo has been taken
//                imageCapture.takePicture(
//                        outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
//                    override fun onError(exc: ImageCaptureException) {
//                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
//                    }
//
//                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
//
////                        val fOut = FileOutputStream(photoFile)
////
////                        copyBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut)
//
//                        val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
//                        Log.d(TAG, "Photo capture succeeded: $savedUri")
//
//                        // We can only change the foreground Drawable using API level 23+ API
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                            // Update the gallery thumbnail with latest picture taken
//                            setGalleryThumbnail(savedUri)
//                        }
//
//                        // Implicit broadcasts will be ignored for devices running API level >= 24
//                        // so if you only target API level 24+ you can remove this statement
//                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
//                            requireActivity().sendBroadcast(
//                                    Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
//                            )
//                        }
//
//                        // If the folder selected is an external media directory, this is
//                        // unnecessary but otherwise other apps will not be able to access our
//                        // images unless we scan them using [MediaScannerConnection]
//                        val mimeType = MimeTypeMap.getSingleton()
//                                .getMimeTypeFromExtension(savedUri.toFile().extension)
//                        MediaScannerConnection.scanFile(
//                                context,
//                                arrayOf(savedUri.toFile().absolutePath),
//                                arrayOf(mimeType)
//                        ) { _, uri ->
//                            Log.d(TAG, "Image capture scanned into media store: $uri")
//                        }
////                        controls.findViewById<ImageButton>(R.id.camera_capture_button).visibility = View.VISIBLE
//                        if (true == outputDirectory.listFiles()?.isNotEmpty()) {
//                            Navigation.findNavController(
//                                    requireActivity(), R.id.fragment_container
//                            ).navigate(CameraFragmentDirections
//                                    .actionCameraToMask(outputDirectory.absolutePath))
//                        }
//                    }
//                })

//                 We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    container.postDelayed({
                        container.foreground = ColorDrawable(Color.WHITE)
                        container.postDelayed(
                                { container.foreground = null }, ANIMATION_FAST_MILLIS)
                    }, ANIMATION_SLOW_MILLIS)
                }
            }
        }


        // Setup for button used to switch cameras
        controls.findViewById<ImageButton>(R.id.camera_switch_button).visibility = View.INVISIBLE
//        controls.findViewById<ImageButton>(R.id.camera_switch_button).let {
//
//            // Disable the button until the camera is set up
//            it.isEnabled = false
//
//            // Listener for button used to switch cameras. Only called if the button is enabled
//            it.setOnClickListener {
//                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
//                    CameraSelector.LENS_FACING_BACK
//                } else {
//                    CameraSelector.LENS_FACING_FRONT
//                }
//                // Re-bind use cases to update selected camera
//                bindCameraUseCases()
//            }
//        }

        // Listener for button used to view the most recent photo
//        controls.findViewById<ImageButton>(R.id.photo_view_button).visibility = View.GONE
        controls.findViewById<ImageButton>(R.id.photo_view_button).setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                Navigation.findNavController(
                        requireActivity(), R.id.fragment_container
                ).navigate(CameraFragmentDirections
                        .actionCameraToGallery(outputDirectory.absolutePath))
            }
        }
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        val switchCamerasButton = container.findViewById<ImageButton>(R.id.camera_switch_button)
        try {
            switchCamerasButton.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            switchCamerasButton.isEnabled = false
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    /**
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    private class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L
        var framesPerSecond: Double = -1.0
            private set

        /**
         * Used to add listeners that will be called with each luma computed
         */
        fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
         * call image.close() on received images when finished using them. Otherwise, new images
         * may not be received or the camera may stall, depending on back pressure setting.
         *
         */
        override fun analyze(image: ImageProxy) {
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) {
                image.close()
                return
            }

            // Keep track of frames analyzed
            val currentTime = System.currentTimeMillis()
            frameTimestamps.push(currentTime)

            // Compute the FPS using a moving average
            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
            val timestampLast = frameTimestamps.peekLast() ?: currentTime
            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
                    frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0

            // Analysis could take an arbitrarily long amount of time
            // Since we are running in a different thread, it won't stall other use cases

            lastAnalyzedTimestamp = frameTimestamps.first

            // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
            val buffer = image.planes[0].buffer

            // Extract image data from callback object
            val data = buffer.toByteArray()

            // Convert the data into an array of pixel values ranging 0-255
            val pixels = data.map { it.toInt() and 0xFF }

            // Compute average luminance for the image
            val luma = pixels.average()

            // Call all listeners with new value
            listeners.forEach { it(luma) }

            image.close()
        }
    }

    private fun showProgressBar() {
        progressbar.setVisibility(View.VISIBLE)
        cameraButton.setVisibility(View.INVISIBLE)
    }

    private fun hideProgressBar() {
        cameraButton.setVisibility(View.VISIBLE)
        progressbar.setVisibility(View.INVISIBLE)
    }

//    private fun setupTapForFocus(cameraControl: CameraControl) {
//        view_finder.setOnTouchListener { _, event ->
//            if (event.action != MotionEvent.ACTION_UP) {
//                return@setOnTouchListener true
//            }
//
//            val textureView = view_finder.getChildAt(0) as? TextureView
//                    ?: return@setOnTouchListener true
//            val factory = MeteringPointFactory(textureView)
//
//            val point = factory.createPoint(event.x, event.y)
//            val action = FocusMeteringAction.Builder(point).build()
//            cameraControl.startFocusAndMetering(action)
//            return@setOnTouchListener true
//        }
//    }

    companion object {

        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.US)
                        .format(System.currentTimeMillis()) + extension)
    }
}
