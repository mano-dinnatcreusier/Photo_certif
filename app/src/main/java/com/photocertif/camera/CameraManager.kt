package com.photocertif.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraManager — Responsabilité unique : gestion du cycle de vie CameraX.
 *
 * Design rationale :
 * - On capture les bytes en mémoire AVANT de les sauvegarder sur disque
 *   (OnImageCapturedCallback vs OnImageSavedCallback). Cela permet de signer
 *   les bytes EXACTS qui seront écrits — garantie d'intégrité bout-en-bout.
 * - Le cameraExecutor est isolé pour ne pas bloquer le thread principal.
 */
class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
    }

    private var imageCapture: ImageCapture? = null

    // Thread dédié aux callbacks de capture (hors main thread)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    /**
     * Lie les UseCases CameraX (Preview + ImageCapture) au [lifecycleOwner].
     * La preview est rendue dans le [previewView] fourni.
     *
     * Suspend function : retourne quand la caméra est réellement prête.
     */
    suspend fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        suspendCancellableCoroutine { cont ->
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    imageCapture = capture

                    // Libère les liaisons précédentes avant de rebinder
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    Log.i(TAG, "Caméra liée avec succès (Preview + ImageCapture)")
                    cont.resume(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors de la liaison de la caméra: ${e.message}")
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))

            cont.invokeOnCancellation { cameraProviderFuture.cancel(true) }
        }
    }

    /**
     * Capture une photo et retourne les bytes JPEG bruts.
     *
     * Utilise [ImageCapture.OnImageCapturedCallback] (capture en mémoire) plutôt que
     * [ImageCapture.OnImageSavedCallback] (sauvegarde directe sur disque) :
     *  → On obtient les bytes avant toute écriture disque
     *  → On peut signer ces bytes EXACTS avant persistance
     *  → Garantie que la signature porte sur ce qui sera sauvegardé
     *
     * @throws IllegalStateException si bindCamera() n'a pas été appelé
     */
    suspend fun captureImageBytes(): ByteArray {
        val capture = imageCapture
            ?: throw IllegalStateException("Caméra non initialisée. Appelez d'abord bindCamera().")

        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val bytes = imageProxyToJpegBytes(image)
                            Log.i(TAG, "✓ Image capturée : ${bytes.size} bytes JPEG")
                            cont.resume(bytes)
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        } finally {
                            image.close() // Toujours libérer l'ImageProxy pour éviter les fuites
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Erreur de capture: ${exception.message} (code=${exception.imageCaptureError})")
                        cont.resumeWithException(exception)
                    }
                }
            )
        }
    }

    /**
     * Extrait les bytes JPEG depuis un [ImageProxy].
     * CameraX garantit le format JPEG pour [OnImageCapturedCallback].
     */
    private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    /** Libère le thread executor (appeler depuis ViewModel.onCleared()). */
    fun shutdown() {
        cameraExecutor.shutdown()
        Log.i(TAG, "CameraExecutor arrêté")
    }
}
