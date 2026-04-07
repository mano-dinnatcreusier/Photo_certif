package com.photocertif

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photocertif.crypto.CryptoManager
import com.photocertif.data.CaptureResult
import com.photocertif.data.UiState
import com.photocertif.ui.MainViewModel
import com.photocertif.ui.MainViewModelFactory
import com.photocertif.ui.theme.PhotoCertifTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoCertifTheme {
                PhotoCertifApp()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PhotoCertifApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(context))

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (hasCameraPermission) {
            CameraScreen(viewModel = viewModel)
        } else {
            PermissionScreen(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Camera screen — vue principale
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CameraScreen(viewModel: MainViewModel) {
    val uiState      by viewModel.uiState.collectAsState()
    val securityInfo by viewModel.securityInfo.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context        = LocalContext.current          // ← extrait ICI, hors de remember{}
    val scope          = rememberCoroutineScope()

    // Correct : context est capturé dans la portée Composable, pas dans le lambda remember{}
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(previewView) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Preview caméra ──────────────────────────────────────────────────
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // ── Dégradé haut ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
        )

        // ── Badge de sécurité (haut centre) ────────────────────────────────
        securityInfo?.let { info ->
            SecurityBadge(
                info = info,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 52.dp)
            )
        }

        // ── Dégradé bas ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
        )

        // ── Contrôles bas ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Indicateur d'état animé
            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "status"
            ) { state ->
                when (state) {
                    is UiState.Capturing -> StatusLabel("📷  Capture en cours…")
                    is UiState.Signing   -> StatusLabel("🔐  Signature TEE/StrongBox…", Color(0xFF00E676))
                    is UiState.Error     -> StatusLabel("⚠️  ${state.message}", Color(0xFFFF5252))
                    else                 -> Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Bouton déclencheur
            CaptureButton(
                isIdle    = uiState is UiState.Idle || uiState is UiState.Success || uiState is UiState.Error,
                isLoading = uiState is UiState.Capturing || uiState is UiState.Signing,
                onClick   = {
                    if (uiState is UiState.Success || uiState is UiState.Error) viewModel.resetState()
                    else scope.launch { viewModel.captureAndSign() }
                }
            )
        }

        // ── Fiche résultat (glisse depuis le bas) ──────────────────────────
        AnimatedVisibility(
            visible = uiState is UiState.Success,
            enter   = slideInVertically { it } + fadeIn(),
            exit    = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            (uiState as? UiState.Success)?.let { state ->
                ResultCard(
                    result   = state.result,
                    onDismiss = { viewModel.resetState() },
                    modifier  = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composants réutilisables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SecurityBadge(info: CryptoManager.KeyProvisioningResult, modifier: Modifier = Modifier) {
    val (label, color, icon) = when {
        info.isStrongBoxBacked -> Triple("StrongBox SE", Color(0xFF00E676), Icons.Filled.Security)
        info.isHardwareBacked  -> Triple("TEE Matériel",  Color(0xFF40C4FF), Icons.Filled.Lock)
        else                   -> Triple("⚠ Logiciel",   Color(0xFFFF5252), Icons.Filled.Warning)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color(0xAA0A0A14),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CaptureButton(isIdle: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    val accentColor = Color(0xFF00BCD4)
    Box(contentAlignment = Alignment.Center) {
        // Anneau extérieur
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .border(3.dp, if (isIdle) accentColor else Color(0xFF444460), CircleShape)
        )
        // Bouton intérieur
        FilledIconButton(
            onClick  = onClick,
            enabled  = isIdle && !isLoading,
            modifier = Modifier.size(68.dp),
            colors   = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isLoading) Color(0xFF1E1E30) else Color.White
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(30.dp),
                    color       = accentColor,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector        = Icons.Filled.CameraAlt,
                    contentDescription = "Capturer et signer cryptographiquement",
                    tint               = Color(0xFF0A0A14),
                    modifier           = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
fun StatusLabel(text: String, color: Color = Color.White) {
    Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun ResultCard(result: CaptureResult, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val sig = result.signatureResult
    val securityLabel = when {
        sig.isStrongBoxBacked -> "StrongBox Secure Element"
        sig.isHardwareBacked  -> "TEE Matériel"
        else                  -> "Logiciel (non recommandé)"
    }
    val securityColor = if (sig.isStrongBoxBacked) Color(0xFF00E676) else Color(0xFF40C4FF)

    Surface(
        modifier = modifier.padding(12.dp),
        shape    = RoundedCornerShape(20.dp),
        color    = Color(0xF013131F),
        border   = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // En-tête
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF00E676), modifier = Modifier.size(22.dp))
                    Text("Photo certifiée", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, null, tint = Color(0xFF9898B0))
                }
            }

            HorizontalDivider(color = Color(0xFF2E2E45))

            // Informations fichiers
            SectionTitle("Fichiers produits")
            InfoRow("Image JPEG", result.imageFile.name)
            InfoRow("Preuve JSON", result.proofFile.name)
            InfoRow("Dossier", result.imageFile.parentFile?.name ?: "—", mono = true)

            HorizontalDivider(color = Color(0xFF2E2E45))

            // Informations cryptographiques
            SectionTitle("Preuve cryptographique")
            InfoRow("Algorithme", sig.algorithm, mono = true)
            InfoRow("Courbe EC", "secp256r1 (NIST P-256)", mono = true)
            InfoRow("Niveau matériel", securityLabel, valueColor = securityColor)
            InfoRow(
                label = "Signature (extrait)",
                value = sig.signatureBase64.take(40) + "…",
                mono  = true
            )
            InfoRow(
                label = "Clé publique (extrait)",
                value = sig.publicKeyBase64.take(40) + "…",
                mono  = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Pastille algo
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp),
                color    = Color(0xFF00E676).copy(alpha = 0.08f),
                border   = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.Shield, null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text       = "AndroidKeyStore · EC P-256 · SHA-256",
                        color      = Color(0xFF00E676),
                        fontSize   = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text       = text.uppercase(),
        color      = Color(0xFF9898B0),
        fontSize   = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false,
    valueColor: Color = Color.White
) {
    Row(
        modifier                = Modifier.fillMaxWidth(),
        horizontalArrangement   = Arrangement.SpaceBetween,
        verticalAlignment       = Alignment.Top
    ) {
        Text(
            text     = label,
            color    = Color(0xFF9898B0),
            fontSize = 13.sp,
            modifier = Modifier.weight(0.38f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text       = value,
            color      = valueColor,
            fontSize   = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            overflow   = TextOverflow.Ellipsis,
            maxLines   = 1,
            modifier   = Modifier.weight(0.62f),
            textAlign  = TextAlign.End
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Écran de demande de permission
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Outlined.Shield,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text       = "PhotoCertif",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color      = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Signature cryptographique matérielle de vos photos via l'Android Keystore (TEE/StrongBox).",
            style     = MaterialTheme.typography.bodyMedium,
            color     = Color(0xFF9898B0),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest) {
            Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Autoriser la caméra")
        }
    }
}
