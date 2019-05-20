///*
// * Copyright 2018 Google LLC. All Rights Reserved.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.shliama.augmentedvideotutorial
//
//import android.app.Activity
//import android.app.ActivityManager
//import android.content.ContentValues
//import android.content.Context
//import android.media.CamcorderProfile
//import android.os.Build
//import android.os.Build.VERSION_CODES
//import android.os.Bundle
//import android.provider.MediaStore
//import android.support.design.widget.FloatingActionButton
//import android.support.v7.app.AppCompatActivity
//import android.util.Log
//import android.view.Gravity
//import android.view.MotionEvent
//import android.view.View
//import android.widget.Toast
//import com.google.ar.core.Anchor
//import com.google.ar.core.HitResult
//import com.google.ar.core.Plane
//import com.google.ar.sceneform.AnchorNode
//import com.google.ar.sceneform.rendering.ModelRenderable
//import com.google.ar.sceneform.ux.TransformableNode
//
///**
// * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
// */
//class VideoRecordingActivity : AppCompatActivity(), ModelLoader.ModelLoaderCallbacks {
//
//    private var arFragment: WritingArFragment? = null
//    private var andyRenderable: ModelRenderable? = null
//    // Model loader class to avoid leaking the activity context.
//    private var modelLoader: ModelLoader? = null
//
//    // VideoRecorder encapsulates all the video recording functionality.
//    private var videoRecorder: VideoRecorder? = null
//
//    // The UI to record.
//    private var recordButton: FloatingActionButton? = null
//
//    protected// CompletableFuture requires api level 24
//    // FutureReturnValueIgnored is not valid
//    fun onCreate(savedInstanceState: Bundle) {
//        super.onCreate(savedInstanceState)
//
//        if (!checkIsSupportedDeviceOrFinish(this)) {
//            return
//        }
//
//        setContentView(R.layout.activity_ux)
//        arFragment = getSupportFragmentManager().findFragmentById(R.id.ux_fragment) as WritingArFragment
//
//        modelLoader = ModelLoader(this)
//        modelLoader!!.loadModel(this, R.raw.andy)
//
//        arFragment!!.setOnTapArPlaneListener(
//            { hitResult: HitResult, plane: Plane, motionEvent: MotionEvent ->
//                if (andyRenderable == null) {
//                    return@arFragment.setOnTapArPlaneListener
//                }
//
//                // Create the Anchor.
//                val anchor = hitResult.createAnchor()
//                val anchorNode = AnchorNode(anchor)
//                anchorNode.setParent(arFragment!!.getArSceneView().getScene())
//
//                // Create the transformable andy and add it to the anchor.
//                val andy = TransformableNode(arFragment!!.getTransformationSystem())
//                andy.setParent(anchorNode)
//                andy.renderable = andyRenderable
//                andy.select()
//            })
//
//        // Initialize the VideoRecorder.
//        videoRecorder = VideoRecorder()
//        val orientation = getResources().getConfiguration().orientation
//        videoRecorder!!.setVideoQuality(CamcorderProfile.QUALITY_2160P, orientation)
//        videoRecorder!!.setSceneView(arFragment!!.getArSceneView())
//
//        recordButton = findViewById(R.id.record)
//        recordButton!!.setOnClickListener(???({ this.toggleRecording(it) }))
//        recordButton!!.setEnabled(true)
//        recordButton!!.setImageResource(R.drawable.round_videocam)
//    }
//
//    protected fun onPause() {
//        if (videoRecorder!!.isRecording()) {
//            toggleRecording(null)
//        }
//        super.onPause()
//    }
//
//    /*
//   * Used as a handler for onClick, so the signature must match onClickListener.
//   */
//    private fun toggleRecording(unusedView: View?) {
//        if (!arFragment!!.hasWritePermission()) {
//            Log.e(TAG, "Video recording requires the WRITE_EXTERNAL_STORAGE permission")
//            Toast.makeText(
//                this,
//                "Video recording requires the WRITE_EXTERNAL_STORAGE permission",
//                Toast.LENGTH_LONG
//            )
//                .show()
//            arFragment!!.launchPermissionSettings()
//            return
//        }
//        val recording = videoRecorder!!.onToggleRecord()
//        if (recording) {
//            recordButton!!.setImageResource(R.drawable.round_stop)
//        } else {
//            recordButton!!.setImageResource(R.drawable.round_videocam)
//            val videoPath = videoRecorder!!.getVideoPath().getAbsolutePath()
//            Toast.makeText(this, "Video saved: $videoPath", Toast.LENGTH_SHORT).show()
//            Log.d(TAG, "Video saved: $videoPath")
//
//            // Send  notification of updated content.
//            val values = ContentValues()
//            values.put(MediaStore.Video.Media.TITLE, "Sceneform Video")
//            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
//            values.put(MediaStore.Video.Media.DATA, videoPath)
//            getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
//        }
//    }
//
//    fun setRenderable(modelRenderable: ModelRenderable) {
//        andyRenderable = modelRenderable
//    }
//
//    fun onLoadException(throwable: Throwable) {
//        val toast = Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG)
//        toast.setGravity(Gravity.CENTER, 0, 0)
//        toast.show()
//        Log.e(TAG, "Unable to load andy renderable", throwable)
//    }
//
//    companion object {
//        private val TAG = VideoRecordingActivity::class.java.simpleName
//        private val MIN_OPENGL_VERSION = 3.0
//
//        /**
//         * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
//         * on this device.
//         *
//         *
//         * Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
//         *
//         *
//         * Finishes the activity if Sceneform can not run
//         */
//        fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
//            if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
//                Log.e(TAG, "Sceneform requires Android N or later")
//                Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show()
//                activity.finish()
//                return false
//            }
//            val openGlVersionString = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
//                .deviceConfigurationInfo
//                .glEsVersion
//            if (java.lang.Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
//                Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later")
//                Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
//                    .show()
//                activity.finish()
//                return false
//            }
//            return true
//        }
//    }
//}
