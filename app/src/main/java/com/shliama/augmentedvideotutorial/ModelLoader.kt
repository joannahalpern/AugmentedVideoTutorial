///*
// * Copyright 2018 Google LLC
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
//import android.content.Context
//import com.google.ar.sceneform.rendering.ModelRenderable
//
//import java.lang.ref.WeakReference
//import java.util.concurrent.CompletableFuture
//
///**
// * Model loader class to avoid memory leaks from the activity. Activity and Fragment controller
// * classes have a lifecycle that is controlled by the UI thread. When a reference to one of these
// * objects is accessed by a background thread it is "leaked". Using that reference to a
// * lifecycle-bound object after Android thinks it has "destroyed" it can produce bugs. It also
// * prevents the Activity or Fragment from being garbage collected, which can leak the memory
// * permanently if the reference is held in the singleton scope.
// *
// *
// * To avoid this, use a non-nested class which is not an activity nor fragment. Hold a weak
// * reference to the activity or fragment and use that when making calls affecting the UI.
// */
//class ModelLoader internal constructor(owner: ModelLoaderCallbacks) {
//    private val owner: WeakReference<ModelLoaderCallbacks>
//    private var future: CompletableFuture<ModelRenderable>? = null
//
//    init {
//        this.owner = WeakReference(owner)
//    }
//
//    /**
//     * Starts loading the model specified. The result of the loading is returned asynchrounously via
//     * [ModelLoaderCallbacks.setRenderable] or [ ][ModelLoaderCallbacks.onLoadException] (Throwable)}.
//     *
//     * @param resourceId the resource id of the .sfb to load.
//     * @return true if loading was initiated.
//     */
//    internal fun loadModel(context: Context, resourceId: Int): Boolean {
//
//        future = ModelRenderable.builder()
//            .setSource(context, resourceId)
//            .build()
//            .thenApply(this::setRenderable)
//            .exceptionally(this::onException)
//        return future != null
//    }
//
//    internal fun onException(throwable: Throwable): ModelRenderable? {
//        val listener = owner.get()
//        listener?.onLoadException(throwable)
//        return null
//    }
//
//    internal fun setRenderable(modelRenderable: ModelRenderable): ModelRenderable {
//        val listener = owner.get()
//        listener?.setRenderable(modelRenderable)
//        return modelRenderable
//    }
//
//    /** Callbacks for handling the loading results.  */
//    interface ModelLoaderCallbacks {
//        fun setRenderable(modelRenderable: ModelRenderable)
//
//        fun onLoadException(throwable: Throwable)
//    }
//
//    companion object {
//        private val TAG = "ModelLoader"
//    }
//}
