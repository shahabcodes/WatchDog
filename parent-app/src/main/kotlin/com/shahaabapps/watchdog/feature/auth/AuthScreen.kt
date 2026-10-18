package com.shahaabapps.watchdog.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.theme.AppFonts
import com.shahaabapps.watchdog.R

// Lighthouse palette (fixed — the auth screens are pre-login and always on-brand).
private val NAVY_DEEP = Color(0xFF00133F)
private val NAVY = Color(0xFF012169)
private val NAVY_BORDER = Color(0xFF3A5AA8)
private val GOLD = Color(0xFFF5B83D)
private val SUBTITLE = Color(0xFFC7D3F0)
private val INK = Color(0xFF1A1C1E)
private val FIELD_BG = Color(0xFFF7F9FD)
private val FIELD_BORDER = Color(0xFFD4DDF0)
private val LABEL = Color(0xFF4A5878)

/**
 * Signed-out flow: the Welcome landing screen first, then the sign in / sign up
 * / reset form. Welcome is the entry; the form's back button returns to it.
 */
@Composable
fun AuthFlow(
    viewModel: AuthViewModel = hiltViewModel(),
    joinState: com.shahaabapps.watchdog.ui.ViewerJoinState = com.shahaabapps.watchdog.ui.ViewerJoinState.Idle,
    onJoinAsViewer: (String) -> Unit = {},
    onClearJoinError: () -> Unit = {},
) {
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    var showViewerDialog by rememberSaveable { mutableStateOf(false) }
    com.shahaabapps.core.ui.theme.LighthouseType {
        if (showWelcome) {
            WelcomeScreen(
                onCreateAccount = { viewModel.setMode(AuthMode.SIGN_UP); showWelcome = false },
                onSignIn = { viewModel.setMode(AuthMode.SIGN_IN); showWelcome = false },
                onViewWithCode = { showViewerDialog = true },
            )
        } else {
            AuthScreen(onBack = { showWelcome = true }, viewModel = viewModel)
        }
    }
    if (showViewerDialog) {
        ViewerJoinDialog(
            joinState = joinState,
            onJoin = onJoinAsViewer,
            onDismiss = { showViewerDialog = false; onClearJoinError() },
        )
    }
}

@Composable
private fun ViewerJoinDialog(
    joinState: com.shahaabapps.watchdog.ui.ViewerJoinState,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val joining = joinState is com.shahaabapps.watchdog.ui.ViewerJoinState.Joining
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // A scanned/decoded QR carries the full pairing payload; pull the code out of it
    // (or use the raw text if it's just a plain code) and join.
    fun useScanned(contents: String?) {
        val resolved = com.shahaabapps.core.model.PairingCodec.decode(contents)?.code
            ?: contents?.trim()?.ifBlank { null }
        if (resolved != null) { code = resolved.uppercase(); onJoin(resolved) }
    }

    val scanLauncher = rememberLauncherForActivityResult(com.journeyapps.barcodescanner.ScanContract()) { result ->
        useScanned(result.contents)
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            val contents = withContext(Dispatchers.IO) {
                com.shahaabapps.watchdog.feature.dashboard.QrImageDecoder.decodeFromUri(context, uri)
            }
            useScanned(contents)
        }
    }

    com.shahaabapps.core.ui.components.AppDialog(
        onDismiss = onDismiss,
        title = "View with a code",
        icon = androidx.compose.material.icons.Icons.Filled.Visibility,
        confirmText = if (joining) "Joining…" else "View",
        onConfirm = { onJoin(code) },
        confirmEnabled = code.isNotBlank() && !joining,
        dismissText = "Cancel",
        dismissOnOutsideTouch = !joining,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Scan the Watch Dog viewer QR (or the Shield pairing code), or type the code. You'll see everything, read-only.",
                textAlign = TextAlign.Center,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ViewerScanChip(
                    text = "Scan QR",
                    icon = androidx.compose.material.icons.Icons.Filled.QrCodeScanner,
                    enabled = !joining,
                    modifier = Modifier.weight(1f),
                ) {
                    scanLauncher.launch(
                        com.journeyapps.barcodescanner.ScanOptions()
                            .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                            .setPrompt("Scan the viewer QR").setBeepEnabled(false).setOrientationLocked(false),
                    )
                }
                ViewerScanChip(
                    text = "From gallery",
                    icon = androidx.compose.material.icons.Icons.Filled.Image,
                    enabled = !joining,
                    modifier = Modifier.weight(1f),
                ) {
                    galleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                }
            }
            androidx.compose.material3.OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                singleLine = true,
                label = { Text("Or enter the code") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            (joinState as? com.shahaabapps.watchdog.ui.ViewerJoinState.Error)?.let {
                Text(it.message, color = Color(0xFFC1362B), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ViewerScanChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.5.dp, com.shahaabapps.core.ui.theme.AppTheme.colors.separator), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = com.shahaabapps.core.ui.theme.AppTheme.colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The lighthouse scene with its two gold beams glowing and dimming, matching the
 * prototype's slow pulse (opacity ~0.18↔0.42 over ~3.2s). The beams live in their
 * own drawable overlaid exactly on the static scene so only they animate. A still,
 * mid-glow frame is shown when the system has animations disabled.
 */
@Composable
private fun LighthouseScene(modifier: Modifier = Modifier) {
    val reduceMotion = remember {
        try {
            android.animation.ValueAnimator.getDurationScale() == 0f
        } catch (_: Throwable) {
            false
        }
    }
    val beamAlpha = if (reduceMotion) {
        0.32f
    } else {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "beam")
        transition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.42f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    durationMillis = 1600,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "beamAlpha",
        ).value
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_lighthouse_scene),
            contentDescription = "A lighthouse shining over the sea at night",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_lighthouse_beams),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = beamAlpha,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit, onViewWithCode: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NAVY_DEEP)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LighthouseScene(modifier = Modifier.fillMaxWidth().height(290.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("WATCH DOG", color = GOLD, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(
                "A guiding light\nfor your family.",
                color = Color.White,
                fontFamily = AppFonts.lighthouse,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 36.sp,
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "See where they are, guide their screen time, and know they got home safe.",
                color = SUBTITLE,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        GoldButton(text = "Create an account", enabled = true, loading = false, onClick = onCreateAccount)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(BorderStroke(1.5.dp, NAVY_BORDER), RoundedCornerShape(16.dp))
                .clickable(onClick = onSignIn),
            contentAlignment = Alignment.Center,
        ) {
            Text("I already have an account", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Setting up a child's phone? Open the Shield app on it.",
            color = SUBTITLE,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            "View with a pairing code",
            color = GOLD,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(onClick = onViewWithCode),
        )
    }
}

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var agreed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NAVY_DEEP)
            .imePadding(),
    ) {
        // Navy header.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(BorderStroke(1.5.dp, NAVY_BORDER), CircleShape)
                        .clickable {
                            if (state.mode == AuthMode.FORGOT) viewModel.setMode(AuthMode.SIGN_IN) else onBack()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Lighthouse mark on the sign-in screen.
                if (state.mode == AuthMode.SIGN_IN) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NAVY),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.ic_lighthouse),
                            contentDescription = "Watch Dog",
                            modifier = Modifier.size(54.dp),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (state.mode) {
                        AuthMode.SIGN_IN -> "Welcome back"
                        AuthMode.SIGN_UP -> "Light the way"
                        AuthMode.FORGOT -> "Reset password"
                    },
                    fontFamily = AppFonts.lighthouse,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.7).sp,
                    color = Color.White,
                )
                Text(
                    when (state.mode) {
                        AuthMode.SIGN_IN -> "Sign in to check in on your family."
                        AuthMode.SIGN_UP -> "Create your parent account. Your children won't need one."
                        AuthMode.FORGOT -> "We'll email you a secure reset link."
                    },
                    fontSize = 15.sp,
                    color = SUBTITLE,
                )
            }
        }

        // White sheet.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Two-factor step: shown after a correct password when the account has an
            // authenticator app enrolled.
            if (state.mfaRequired) {
                FieldLabel("Verification code")
                Text(
                    "Enter the 6-digit code from your authenticator app.",
                    color = LABEL,
                    fontSize = 14.sp,
                )
                com.shahaabapps.watchdog.ui.OtpInput(
                    value = state.mfaCode,
                    onValueChange = viewModel::onMfaCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.errorMessage?.let {
                    Text(it, color = Color(0xFFC1362B), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                GoldButton(
                    text = "Verify",
                    enabled = state.mfaCode.length == 6 && !state.isSubmitting,
                    loading = state.isSubmitting,
                    onClick = viewModel::submitMfa,
                )
                Text(
                    "Use a different account",
                    color = NAVY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable { viewModel.cancelMfa() },
                )
                return@Column
            }

            val forgotSent = state.isForgot && state.infoMessage != null
            if (!forgotSent) {
                FieldLabel("Email")
                LhField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                )
            }

            AnimatedVisibility(!state.isForgot, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Password")
                    LhPasswordField(value = state.password, onValueChange = viewModel::onPasswordChange, placeholder = "Your password")
                    if (state.isSignUp) PasswordStrength(state.password)
                }
            }

            AnimatedVisibility(state.isSignUp, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Confirm password")
                    LhPasswordField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChange,
                        placeholder = "Re-enter your password",
                        isError = state.passwordsMismatch,
                    )
                    if (state.passwordsMismatch) {
                        Text("Passwords don't match", color = Color(0xFFC1362B), fontSize = 12.sp)
                    }
                }
            }

            // Forgot link on sign in.
            if (state.mode == AuthMode.SIGN_IN) {
                Text(
                    "Forgot password?",
                    color = NAVY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { viewModel.setMode(AuthMode.FORGOT) },
                )
            }

            // Terms on sign up.
            if (state.isSignUp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = NAVY),
                    )
                    Text(
                        "I'm the parent or guardian and agree to the Terms and Privacy Policy.",
                        color = LABEL,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            state.errorMessage?.let {
                Text(it, color = Color(0xFFC1362B), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (forgotSent) {
                // "Check your inbox" confirmation card.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFEEF1F8))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Email, contentDescription = null, tint = NAVY, modifier = Modifier.size(56.dp))
                    Text("Check your inbox", color = NAVY, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        state.infoMessage ?: "If an account exists for that email, a reset link is on its way.",
                        color = LABEL,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                state.infoMessage?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE4F1EA))
                            .padding(14.dp),
                    ) {
                        Text(it, color = Color(0xFF145C3B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (!forgotSent) {
                val canSubmit = state.canSubmit && (!state.isSignUp || agreed)
                GoldButton(
                    text = when (state.mode) {
                        AuthMode.SIGN_IN -> "Sign in"
                        AuthMode.SIGN_UP -> "Create account"
                        AuthMode.FORGOT -> "Send reset link"
                    },
                    enabled = canSubmit,
                    loading = state.isSubmitting,
                    onClick = viewModel::submit,
                )
            }

            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                when (state.mode) {
                    AuthMode.SIGN_IN -> {
                        Text("New to Watch Dog? ", color = LABEL, fontSize = 15.sp)
                        Text("Create an account", color = NAVY, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.setMode(AuthMode.SIGN_UP) })
                    }
                    else -> {
                        Text("Already have an account? ", color = LABEL, fontSize = 15.sp)
                        Text("Sign in", color = NAVY, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.setMode(AuthMode.SIGN_IN) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LABEL)
}

@Composable
private fun LhField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        isError = isError,
        placeholder = { Text(placeholder, color = LABEL.copy(alpha = 0.7f)) },
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FIELD_BG,
            unfocusedContainerColor = FIELD_BG,
            errorContainerColor = FIELD_BG,
            focusedIndicatorColor = NAVY,
            unfocusedIndicatorColor = FIELD_BORDER,
            errorIndicatorColor = Color(0xFFC1362B),
            focusedTextColor = INK,
            unfocusedTextColor = INK,
        ),
    )
}

@Composable
private fun LhPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    LhField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        isError = isError,
        trailing = {
            Icon(
                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (visible) "Hide password" else "Show password",
                tint = LABEL,
                modifier = Modifier.clickable { visible = !visible },
            )
        },
    )
}

@Composable
private fun PasswordStrength(pw: String) {
    var score = 0
    if (pw.length >= 8) score++
    if (pw.any { it.isUpperCase() } && pw.any { it.isLowerCase() }) score++
    if (pw.any { it.isDigit() }) score++
    if (pw.any { !it.isLetterOrDigit() } || pw.length >= 14) score++
    val palette = listOf(Color(0xFFC1362B), Color(0xFFD9622B), Color(0xFFB7860B), Color(0xFF1E8E5A))
    val labels = listOf("Too short", "Weak", "Okay", "Good", "Strong")
    val color = if (score > 0) palette[score - 1] else FIELD_BORDER
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i < score) color else FIELD_BORDER),
                )
            }
        }
        Text(
            if (pw.isEmpty()) "Use 8+ characters with a number" else "${labels[score]} password",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (pw.isEmpty()) LABEL else color,
        )
    }
}

@Composable
private fun GoldButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && enabled && !loading) 0.96f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "goldScale",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) GOLD else GOLD.copy(alpha = 0.45f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = NAVY_DEEP, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, color = NAVY_DEEP, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
