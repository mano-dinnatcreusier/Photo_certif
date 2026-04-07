package com.photocertif.ui

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photocertif.camera.CameraManager
import com.photocertif.crypto.CryptoManager
import com.photocertif.data.CaptureResult
import com.photocertif.data.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * MainViewModel — Orchestre le pipeline : Capture → Signature → Persistance.
 *
 * Respecte le principe de responsabilité unique :
 *  - CameraManager   : tout ce qui concerne la caméra
 *  - CryptoManager   : tout ce qui concerne les clés et signatures
 *  - MainViewModel   : coordination et gestion de l'état UI
 *  - MainActivity    : uniquement le rendu Compose
 */
class MainViewModel(private val appContext: Context) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private val DATE_FORMAT_FILE = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        private val DATE_FORMAT_ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    val cryptoManager = CryptoManager()
    val cameraManager = CameraManager(appContext)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _securityInfo = MutableStateFlow<CryptoManager.KeyProvisioningResult?>(null)
    val securityInfo: StateFlow<CryptoManager.KeyProvisioningResult?> = _securityInfo.asStateFlow()

    init {
        initCrypto()
    }

    /** Initialise le module crypto au démarrage (génère la clé si absente). */
    private fun initCrypto() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { cryptoManager.getOrCreateKeyPair() }
                .onSuccess { result ->
                    _securityInfo.value = result
                    Log.i(TAG, "Crypto prêt | StrongBox=${result.isStrongBoxBacked} TEE=${result.isHardwareBacked}")
                }
                .onFailure { e ->
                    _uiState.value = UiState.Error("Initialisation crypto échouée: ${e.message}")
                }
        }
    }

    /** Lie la caméra au cycle de vie de l'activité et initialise la preview. */
    suspend fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraManager.bindCamera(lifecycleOwner, previewView)
    }

    /**
     * Pipeline principal : Capture → Signature → Sauvegarde.
     *
     * Chaque étape est effectuée sur [Dispatchers.IO] pour ne pas bloquer l'UI.
     * L'état de l'UI est mis à jour sur le Main dispatcher via StateFlow.
     */
    fun captureAndSign() {
        viewModelScope.launch {
            try {
                // Étape 1 : capture des bytes JPEG bruts depuis le capteur
                _uiState.value = UiState.Capturing
                val imageBytes = withContext(Dispatchers.IO) {
                    cameraManager.captureImageBytes()
                }

                // Étape 2 : signature des bytes par le TEE/StrongBox via Android Keystore
                _uiState.value = UiState.Signing
                val signatureResult = withContext(Dispatchers.IO) {
                    cryptoManager.signImageBytes(imageBytes)
                }

                // Étape 3 : persistance atomique (JPEG + JSON sidecar)
                val captureResult = withContext(Dispatchers.IO) {
                    persistFiles(imageBytes, signatureResult)
                }

                _uiState.value = UiState.Success(captureResult)
                Log.i(TAG, "Pipeline complet : ${captureResult.imageFile.name}")

            } catch (e: Exception) {
                Log.e(TAG, "Erreur pipeline: ${e.message}", e)
                _uiState.value = UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    /**
     * Sauvegarde atomique :
     *  1. Fichier JPEG   → CERTIF_<timestamp>.jpg
     *  2. Fichier preuve → CERTIF_<timestamp>.proof.json
     *
     * Dossier : [Context.getExternalFilesDir] — privé à l'app, aucune permission requise.
     */
    private fun persistFiles(
        imageBytes: ByteArray,
        signatureResult: CryptoManager.SignatureResult
    ): CaptureResult {
        val timestamp = DATE_FORMAT_FILE.format(Date())
        val baseName = "CERTIF_$timestamp"

        val outputDir = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: appContext.filesDir // Fallback sur le stockage interne si externe indisponible

        val imageFile = File(outputDir, "$baseName.jpg").also { it.writeBytes(imageBytes) }
        val proofFile = File(outputDir, "$baseName.proof.json").also {
            it.writeText(buildProofJson(imageFile.name, signatureResult))
        }

        Log.i(TAG, "Fichiers sauvegardés dans : ${outputDir.absolutePath}")
        return CaptureResult(imageFile, proofFile, signatureResult)
    }

    /**
     * Construit le JSON de preuve lisible par un outil tiers de vérification.
     *
     * Format suffisant pour rejouer : verify(SHA256withECDSA, publicKey, signature, imageBytes).
     */
    private fun buildProofJson(imageFileName: String, sig: CryptoManager.SignatureResult): String =
        JSONObject().apply {
            put("version", "1.0")
            put("image_file", imageFileName)
            put("timestamp_utc", DATE_FORMAT_ISO.format(Date()))
            put("algorithm", sig.algorithm)               // "SHA256withECDSA"
            put("key_algorithm", "EC")
            put("ec_curve", "secp256r1")
            put("key_format", "X.509 SubjectPublicKeyInfo")
            put("key_provider", CryptoManager.KEYSTORE_PROVIDER)
            put("hardware_backed", sig.isHardwareBacked)
            put("strongbox_backed", sig.isStrongBoxBacked)
            put("public_key_base64", sig.publicKeyBase64)  // Vérifiable avec OpenSSL
            put("signature_base64", sig.signatureBase64)
            put("verification_hint",
                "openssl dgst -sha256 -verify <(echo '${sig.publicKeyBase64}' | base64 -d | " +
                "openssl ec -pubin -inform DER) -signature <signature.bin> <image.jpg>")
        }.toString(2) // Indentation de 2 espaces pour lisibilité

    fun resetState() {
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.shutdown()
    }
}

/** Factory standard pour injecter [Context] dans le ViewModel. */
class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("ViewModel inconnu : ${modelClass.name}")
    }
}
