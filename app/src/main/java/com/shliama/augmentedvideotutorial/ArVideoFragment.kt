package com.shliama.augmentedvideotutorial

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.media.CamcorderProfile
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.core.graphics.rotationMatrix
import androidx.core.graphics.transform
import com.google.ar.core.*
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.rendering.ExternalTexture
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import java.io.IOException
import android.os.CountDownTimer
import android.provider.MediaStore
import android.provider.Settings
import com.google.android.material.floatingactionbutton.FloatingActionButton


open class ArVideoFragment : ArFragment() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var externalTexture: ExternalTexture
    private lateinit var videoRenderable: ModelRenderable
    private lateinit var videoAnchorNode: VideoAnchorNode
    private lateinit var videoRecorder: VideoRecorder
//    private lateinit var recordButton: FloatingActionButton

    private var activeAugmentedImage: AugmentedImage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPlayer = MediaPlayer()
        videoRecorder = VideoRecorder()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        planeDiscoveryController.hide()
        planeDiscoveryController.setInstructionView(null)
        arSceneView.planeRenderer.isEnabled = false
        arSceneView.isLightEstimationEnabled = false



        initializeSession()
        createArScene()
        setUpVideoRecorder()


//        recordButton = view!!.findViewById(R.id.record)
//        recordButton.setOnClickListener(View.OnClickListener { this.toggleRecording(it) })
//        recordButton.isEnabled = true
//        recordButton.setImageResource(R.drawable.round_videocam)

        return view
    }

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        recordButton = view.findViewById(R.id.record)
//        recordButton.setOnClickListener(View.OnClickListener { this.toggleRecording(it) })
//        recordButton.isEnabled = true
//        recordButton.setImageResource(R.drawable.round_videocam)
//    }

    override fun getSessionConfiguration(session: Session): Config {

        fun loadAugmentedImageBitmap(imageName: String): Bitmap =
            requireContext().assets.open(imageName).use { return BitmapFactory.decodeStream(it) }

        fun setupAugmentedImageDatabase(config: Config, session: Session): Boolean {
            try {
                config.augmentedImageDatabase = AugmentedImageDatabase(session).also { db ->
                    db.addImage(TEST_VIDEO_1, loadAugmentedImageBitmap(TEST_IMAGE_1))
                    db.addImage(TEST_VIDEO_2, loadAugmentedImageBitmap(TEST_IMAGE_2))
                    db.addImage(TEST_VIDEO_3, loadAugmentedImageBitmap(TEST_IMAGE_3))
                }
                return true
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Could not add bitmap to augmented image database", e)
            } catch (e: IOException) {
                Log.e(TAG, "IO exception loading augmented image bitmap.", e)
            }
            return false
        }

        return super.getSessionConfiguration(session).also {
            it.lightEstimationMode = Config.LightEstimationMode.DISABLED
            it.focusMode = Config.FocusMode.AUTO

            if (!setupAugmentedImageDatabase(it, session)) {
                Toast.makeText(requireContext(), "Could not setup augmented image database", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createArScene() {
        // Create an ExternalTexture for displaying the contents of the video.
        externalTexture = ExternalTexture().also {
            mediaPlayer.setSurface(it.surface)
        }

        // Create a renderable with a material that has a parameter of type 'samplerExternal' so that
        // it can display an ExternalTexture.
        ModelRenderable.builder()
            .setSource(requireContext(), R.raw.augmented_video_model)
            .build()
            .thenAccept { renderable ->
                videoRenderable = renderable
                renderable.isShadowCaster = false
                renderable.isShadowReceiver = false
                renderable.material.setExternalTexture("videoTexture", externalTexture)
            }
            .exceptionally { throwable ->
                Log.e(TAG, "Could not create ModelRenderable", throwable)
                return@exceptionally null
            }

        videoAnchorNode = VideoAnchorNode().apply {
            setParent(arSceneView.scene)
        }
    }

    private fun setUpVideoRecorder() {
        videoRecorder.setSceneView(arSceneView)
        val orientation = resources.configuration.orientation
        videoRecorder.setVideoQuality(CamcorderProfile.QUALITY_2160P, orientation)
        videoRecorder.onToggleRecord()
    }

    override fun onUpdate(frameTime: FrameTime) {
        val frame = arSceneView.arFrame
        if (frame == null || frame.camera.trackingState != TrackingState.TRACKING) {
            return
        }

        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
        for (augmentedImage in updatedAugmentedImages) {
            if (activeAugmentedImage != augmentedImage && augmentedImage.trackingState == TrackingState.TRACKING) {
                try {
                    dismissArVideo()
                    playbackArVideo(augmentedImage)
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Could not play video [${augmentedImage.name}]", e)
                }
            }
        }
    }

    private fun dismissArVideo() {
        videoAnchorNode.anchor?.detach()
        videoAnchorNode.renderable = null
        activeAugmentedImage = null
        mediaPlayer.reset()
    }

    private fun playbackArVideo(augmentedImage: AugmentedImage) {
        Log.d(TAG, "playbackVideo = ${augmentedImage.name}")

        requireContext().assets.openFd(augmentedImage.name)
            .use { descriptor ->

                val metadataRetriever = MediaMetadataRetriever()
                metadataRetriever.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )

                val videoWidth = metadataRetriever.extractMetadata(METADATA_KEY_VIDEO_WIDTH).toFloatOrNull() ?: 0f
                val videoHeight = metadataRetriever.extractMetadata(METADATA_KEY_VIDEO_HEIGHT).toFloatOrNull() ?: 0f
                val videoRotation = metadataRetriever.extractMetadata(METADATA_KEY_VIDEO_ROTATION).toFloatOrNull() ?: 0f

                // Account for video rotation, so that scale logic math works properly
                val imageSize = RectF(0f, 0f, augmentedImage.extentX, augmentedImage.extentZ)
                    .transform(rotationMatrix(videoRotation))

                val videoScaleType = VideoScaleType.CenterCrop

                videoAnchorNode.setVideoProperties(
                    videoWidth = videoWidth, videoHeight = videoHeight, videoRotation = videoRotation,
                    imageWidth = imageSize.width(), imageHeight = imageSize.height(),
                    videoScaleType = videoScaleType
                )

                // Update the material parameters
                videoRenderable.material.setFloat2(MATERIAL_IMAGE_SIZE, imageSize.width(), imageSize.height())
                videoRenderable.material.setFloat2(MATERIAL_VIDEO_SIZE, videoWidth, videoHeight)
                videoRenderable.material.setBoolean(MATERIAL_VIDEO_CROP, VIDEO_CROP_ENABLED)

                mediaPlayer.setDataSource(descriptor)
            }.also {
                mediaPlayer.isLooping = true
                mediaPlayer.prepare()
                mediaPlayer.start()
            }


        videoAnchorNode.anchor = augmentedImage.createAnchor(augmentedImage.centerPose)

        activeAugmentedImage = augmentedImage

        externalTexture.surfaceTexture.setOnFrameAvailableListener {
            it.setOnFrameAvailableListener(null)
            videoAnchorNode.renderable = videoRenderable
            fadeInVideo()
        }
    }

    private fun fadeInVideo() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = LinearInterpolator()
            addUpdateListener { v ->
                videoRenderable.material.setFloat(MATERIAL_VIDEO_ALPHA, v.animatedValue as Float)
            }
            start()
        }
    }

    override fun onPause() {
        if (videoRecorder.isRecording) {
            toggleRecording(null)
        }
        super.onPause()
        dismissArVideo()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    companion object {
        private const val TAG = "ArVideoFragment"

        private const val TEST_IMAGE_1 = "test_image_1.jpg"
        private const val TEST_IMAGE_2 = "test_image_2.jpg"
        private const val TEST_IMAGE_3 = "test_image_3.jpg"

        private const val TEST_VIDEO_1 = "test_video_1.mp4"
        private const val TEST_VIDEO_2 = "test_video_2.mp4"
        private const val TEST_VIDEO_3 = "test_video_3.mp4"

        private const val VIDEO_CROP_ENABLED = true

        private const val MATERIAL_IMAGE_SIZE = "imageSize"
        private const val MATERIAL_VIDEO_SIZE = "videoSize"
        private const val MATERIAL_VIDEO_CROP = "videoCropEnabled"
        private const val MATERIAL_VIDEO_ALPHA = "videoAlpha"
    }
    override fun getAdditionalPermissions(): Array<String> {
        val additionalPermissions = super.getAdditionalPermissions()
        val permissionLength = additionalPermissions?.size ?: 0
        val permissions = Array<String>(permissionLength + 1) { i -> Manifest.permission.WRITE_EXTERNAL_STORAGE }
        if (permissionLength > 0) {
            System.arraycopy(additionalPermissions!!, 0, permissions, 1, additionalPermissions.size)
        }
        return permissions
    }

    fun hasWritePermission(): Boolean {
        return true
    }

    /** Launch Application Setting to grant permissions.  */
    fun launchPermissionSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", requireActivity().packageName, null)
        requireActivity().startActivity(intent)
    }

    /*
   * Used as a handler for onClick, so the signature must match onClickListener.
   */
    private fun toggleRecording(unusedView: View?) {
        val recording = videoRecorder.onToggleRecord()
        if (!recording) {
            val videoPath = videoRecorder.videoPath?.absolutePath

            // Send  notification of updated content.
            val values = ContentValues()
            values.put(MediaStore.Video.Media.TITLE, "Sceneform Video")
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            values.put(MediaStore.Video.Media.DATA, videoPath)
            activity?.applicationContext?.contentResolver?.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }
    }
}
