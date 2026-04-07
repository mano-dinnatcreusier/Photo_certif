package com.photocertif.data

import com.photocertif.crypto.CryptoManager
import java.io.File

/**
 * Résultat d'une capture réussie : les deux fichiers produits sur disque
 * et les métadonnées de signature pour affichage dans l'UI.
 */
data class CaptureResult(
    val imageFile: File,
    val proofFile: File,
    val signatureResult: CryptoManager.SignatureResult
)

/** États possibles de l'UI — machine à états simple et explicite. */
sealed class UiState {
    /** Prêt à capturer */
    object Idle : UiState()
    /** Acquisition de la photo en cours */
    object Capturing : UiState()
    /** Signature cryptographique en cours (TEE/StrongBox) */
    object Signing : UiState()
    /** Succès : fichiers produits et signés */
    data class Success(val result: CaptureResult) : UiState()
    /** Erreur récupérable */
    data class Error(val message: String) : UiState()
}
