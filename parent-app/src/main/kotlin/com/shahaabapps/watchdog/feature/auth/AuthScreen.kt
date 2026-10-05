package com.shahaabapps.watchdog.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.components.rememberReducedMotion
import com.shahaabapps.core.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class FocusField { NONE, EMAIL, PASSWORD, CONFIRM }
private enum class DogMood { NEUTRAL, HAPPY, ERROR, SUCCESS }

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = AppTheme.colors.accent
    val gradient = AppTheme.palette.gradient.ifEmpty { listOf(accent, accent) }
    val onBg = MaterialTheme.colorScheme.onBackground
    val muted = AppTheme.colors.secondaryLabel
    val reducedMotion = rememberReducedMotion()

    var focusedField by remember { mutableStateOf(FocusField.NONE) }

    val coveringEyes = focusedField == FocusField.PASSWORD || focusedField == FocusField.CONFIRM
    val cover by animateFloatAsState(
        targetValue = if (coveringEyes) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 280, easing = FastOutSlowInEasing),
        label = "cover",
    )
    val lookTarget = if (focusedField == FocusField.EMAIL) {
        (state.email.length.coerceAtMost(18) / 18f) * 2f - 1f
    } else {
        0f
    }
    val look by animateFloatAsState(
        targetValue = lookTarget,
        animationSpec = tween(if (reducedMotion) 0 else 220),
        label = "look",
    )
    val mood = when {
        state.errorMessage != null -> DogMood.ERROR
        state.infoMessage != null -> DogMood.SUCCESS
        focusedField == FocusField.EMAIL -> DogMood.HAPPY
        else -> DogMood.NEUTRAL
    }
    // Stands on two legs while engaged (typing or covering); sits otherwise.
    val standing = mood == DogMood.HAPPY || mood == DogMood.SUCCESS || coveringEyes
    val stand by animateFloatAsState(
        targetValue = if (standing) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 340, easing = FastOutSlowInEasing),
        label = "stand",
    )
    // Slides the shades down its nose to read your email.
    val peek by animateFloatAsState(
        targetValue = if (focusedField == FocusField.EMAIL) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 240),
        label = "peek",
    )

    val enter = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) enter.snapTo(1f) else enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(gradient = gradient, accent = accent, animate = !reducedMotion)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .alpha(enter.value),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height((40 * (1f - enter.value)).dp))

            Dog(
                gradient = gradient,
                accent = accent,
                cover = cover,
                look = look,
                mood = mood,
                stand = stand,
                peek = peek,
                animate = !reducedMotion,
            )

            Crossfade(targetState = state.mode, label = "title") { mode ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (mode) {
                            AuthMode.SIGN_IN -> "Welcome back"
                            AuthMode.SIGN_UP -> "Create account"
                            AuthMode.FORGOT -> "Reset password"
                        },
                        color = onBg,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when (mode) {
                            AuthMode.SIGN_IN -> "Keep your family's devices safe."
                            AuthMode.SIGN_UP -> "A free account protects every device."
                            AuthMode.FORGOT -> "We'll email you a secure reset link."
                        },
                        color = muted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // Glass form card — light, elevated, subtly bordered.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppTheme.colors.card.copy(alpha = 0.72f))
                    .border(1.dp, onBg.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlassField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    hint = "Email",
                    icon = Icons.Filled.MailOutline,
                    accent = accent,
                    onBg = onBg,
                    muted = muted,
                    keyboardType = KeyboardType.Email,
                    animate = !reducedMotion,
                    onFocusChanged = { if (it) focusedField = FocusField.EMAIL else if (focusedField == FocusField.EMAIL) focusedField = FocusField.NONE },
                )
                AnimatedVisibility(
                    visible = !state.isForgot,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    GlassField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        hint = "Password",
                        icon = Icons.Filled.Lock,
                        accent = accent,
                        onBg = onBg,
                        muted = muted,
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        animate = !reducedMotion,
                        onFocusChanged = { if (it) focusedField = FocusField.PASSWORD else if (focusedField == FocusField.PASSWORD) focusedField = FocusField.NONE },
                    )
                }
                AnimatedVisibility(
                    visible = state.isSignUp,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        GlassField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            hint = "Confirm password",
                            icon = Icons.Filled.Lock,
                            accent = accent,
                            onBg = onBg,
                            muted = muted,
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            animate = !reducedMotion,
                            onFocusChanged = { if (it) focusedField = FocusField.CONFIRM else if (focusedField == FocusField.CONFIRM) focusedField = FocusField.NONE },
                        )
                        if (state.passwordsMismatch) {
                            Text(
                                text = "Passwords don't match",
                                color = AppTheme.colors.destructive,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                            )
                        }
                    }
                }

                state.errorMessage?.let {
                    Text(it, color = AppTheme.colors.destructive, fontSize = 13.sp)
                }
                state.infoMessage?.let {
                    Text(it, color = accent, fontSize = 13.sp)
                }

                GradientButton(
                    label = when (state.mode) {
                        AuthMode.SIGN_IN -> "Sign In"
                        AuthMode.SIGN_UP -> "Create Account"
                        AuthMode.FORGOT -> "Send reset link"
                    },
                    gradient = gradient,
                    enabled = state.canSubmit,
                    loading = state.isSubmitting,
                    onClick = viewModel::submit,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (state.mode) {
                    AuthMode.SIGN_IN -> {
                        LinkText("Forgot password?", accent) { viewModel.setMode(AuthMode.FORGOT) }
                        LinkText("New here?  Create an account", onBg.copy(alpha = 0.8f)) {
                            viewModel.setMode(AuthMode.SIGN_UP)
                        }
                    }
                    AuthMode.SIGN_UP -> LinkText("Already have an account?  Sign In", accent) {
                        viewModel.setMode(AuthMode.SIGN_IN)
                    }
                    AuthMode.FORGOT -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = accent, modifier = Modifier.size(16.dp))
                        LinkText("  Back to sign in", accent) { viewModel.setMode(AuthMode.SIGN_IN) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** An airy light backdrop washed with drifting theme-coloured light — static when reduced. */
@Composable
private fun AuroraBackground(gradient: List<Color>, accent: Color, animate: Boolean) {
    val colors = (gradient + accent)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(AppTheme.colors.groupedBackground, AppTheme.colors.card),
                ),
            ),
    ) {
        if (animate) {
            val transition = rememberInfiniteTransition(label = "aurora")
            val t by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
                label = "drift",
            )
            AuroraCanvas(colors = colors, t = t)
        } else {
            AuroraCanvas(colors = colors, t = 0.12f)
        }
    }
}

@Composable
private fun AuroraCanvas(colors: List<Color>, t: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val two = (2f * PI).toFloat()
        colors.forEachIndexed { i, c ->
            val phase = t * two + i * 1.7f
            val cx = w * (0.5f + 0.44f * cos(phase + i))
            val cy = h * (0.38f + 0.42f * sin(phase * 0.8f + i * 1.3f))
            val r = size.minDimension * (0.55f + 0.12f * sin(phase).let { it * it })
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.30f), c.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}

/**
 * A full-body spy dog that brings the auth flow to life: it sits when idle,
 * stands on two legs while you type, jumps for joy on success, slides its shades
 * down its nose to read your email, covers its eyes with its paws for passwords,
 * and droops its ears when something's wrong.
 */
@Composable
private fun Dog(
    gradient: List<Color>,
    accent: Color,
    cover: Float,
    look: Float,
    mood: DogMood,
    stand: Float,
    peek: Float,
    animate: Boolean,
) {
    if (animate) {
        val transition = rememberInfiniteTransition(label = "dog")
        val cycle by transition.animateFloat(
            0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "cycle",
        )
        val bob by transition.animateFloat(
            -1f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
        )
        val wag by transition.animateFloat(
            -1f, 1f, infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wag",
        )
        val blink = if (cycle > 0.95f) 1f - sin(((cycle - 0.95f) / 0.05f) * PI).toFloat() else 1f
        DogVisual(gradient, accent, cover, look, mood, stand, peek, blink = blink, bob = bob, wag = wag)
    } else {
        DogVisual(gradient, accent, cover, look, mood, stand, peek, blink = 1f, bob = 0f, wag = 0f)
    }
}

@Composable
private fun DogVisual(
    gradient: List<Color>,
    accent: Color,
    cover: Float,
    look: Float,
    mood: DogMood,
    stand: Float,
    peek: Float,
    blink: Float,
    bob: Float,
    wag: Float,
) {
    val fur = gradient
    val furDark = gradient.getOrElse(gradient.size - 1) { accent }
    val eyeDark = Color(0xFF241B16)
    val belly = Color(0xFFFBEFE4)
    val tongue = Color(0xFFF2788F)
    val lens = Color(0xFF14202B)
    val smile = when (mood) {
        DogMood.SUCCESS -> 1f
        DogMood.HAPPY -> 0.7f
        DogMood.NEUTRAL -> 0.4f
        DogMood.ERROR -> -0.6f
    }
    val earDrop = when (mood) {
        DogMood.ERROR -> 5f
        DogMood.HAPPY, DogMood.SUCCESS -> -2.5f
        DogMood.NEUTRAL -> 0f
    }

    Box(modifier = Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val u = size.minDimension / 120f
            fun px(v: Float) = v * u
            fun p(x: Float, y: Float) = Offset(px(x), px(y))
            val center = Offset(size.width / 2f, size.height / 2f)

            // Soft accent halo (the rotating ring is gone).
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                    center = center, radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
                center = center,
            )

            // Whole-body bob; a bigger joyful hop on success.
            val amp = if (mood == DogMood.SUCCESS) 6f else 3f
            val lift = if (mood == DogMood.SUCCESS) -4f else 0f
            translate(top = px(bob * amp + lift)) {

                // ---- Body morph: sitting (stand≈0) → standing on two legs (stand≈1) ----
                val bodyTop = lerpF(62f, 56f, stand)
                val bodyBot = lerpF(100f, 110f, stand)
                val halfW = lerpF(27f, 21f, stand)
                val feetY = bodyBot - 1f
                val footSpread = lerpF(15f, 9f, stand)

                // Tail (behind body), wagging — livelier when happy.
                val wagDeg = wag * (if (mood == DogMood.HAPPY || mood == DogMood.SUCCESS) 16f else 7f)
                rotate(degrees = wagDeg, pivot = p(60f + halfW - 4f, bodyBot - 14f)) {
                    drawPath(
                        Path().apply {
                            moveTo(px(60f + halfW - 6f), px(bodyBot - 10f))
                            quadraticBezierTo(px(60f + halfW + 12f), px(bodyBot - 26f), px(60f + halfW + 6f), px(bodyBot - 34f))
                            quadraticBezierTo(px(60f + halfW + 2f), px(bodyBot - 24f), px(60f + halfW - 2f), px(bodyBot - 8f))
                            close()
                        },
                        color = furDark,
                    )
                }

                // Feet.
                for (fx in listOf(60f - footSpread, 60f + footSpread)) {
                    drawOval(color = furDark, topLeft = p(fx - 8f, feetY - 5f), size = Size(px(16f), px(11f)))
                    drawOval(color = belly.copy(alpha = 0.5f), topLeft = p(fx - 4f, feetY), size = Size(px(8f), px(5f)))
                }

                // Torso (capsule) with a cream belly patch.
                drawRoundRect(
                    brush = Brush.verticalGradient(fur, startY = px(bodyTop), endY = px(bodyBot)),
                    topLeft = p(60f - halfW, bodyTop),
                    size = Size(px(2f * halfW), px(bodyBot - bodyTop)),
                    cornerRadius = CornerRadius(px(halfW), px(halfW)),
                )
                drawOval(
                    color = belly,
                    topLeft = p(60f - halfW * 0.6f, bodyTop + 6f),
                    size = Size(px(halfW * 1.2f), px((bodyBot - bodyTop) * 0.62f)),
                )

                // ---- Arms/paws: rest on the belly, or rise to cover the eyes ----
                val shoulderL = p(60f - halfW + 2f, bodyTop + 10f)
                val shoulderR = p(60f + halfW - 2f, bodyTop + 10f)
                val restL = p(52f, bodyTop + 22f)
                val restR = p(68f, bodyTop + 22f)
                val coverL = p(52f, 34f)
                val coverR = p(68f, 34f)
                val pawL = lerpO(restL, coverL, cover)
                val pawR = lerpO(restR, coverR, cover)
                drawLine(furDark, shoulderL, pawL, strokeWidth = px(8f), cap = StrokeCap.Round)
                drawLine(furDark, shoulderR, pawR, strokeWidth = px(8f), cap = StrokeCap.Round)

                // ---- Head ----
                val headC = p(60f, 36f)
                val headR = px(20f)
                // Ears behind the head.
                translate(top = px(earDrop)) {
                    drawPath(earPath(::px, left = true), color = furDark)
                    drawPath(earPath(::px, left = false), color = furDark)
                }
                drawCircle(brush = Brush.verticalGradient(fur, startY = px(16f), endY = px(56f)), radius = headR, center = headC)
                drawCircle(Color.White.copy(alpha = 0.10f), radius = headR, center = headC)

                // Snout + nose + mouth.
                drawOval(color = belly, topLeft = p(49f, 40f), size = Size(px(22f), px(16f)))
                drawOval(color = eyeDark, topLeft = p(56.5f, 42f), size = Size(px(7f), px(5.5f)))
                drawLine(eyeDark, p(60f, 47f), p(60f, 50f), strokeWidth = px(1.6f), cap = StrokeCap.Round)
                val cornerY = 50f - smile * 2f
                drawPath(
                    Path().apply { moveTo(px(60f), px(50f)); quadraticBezierTo(px(54f), px(50f + smile * 3.5f), px(52f), px(cornerY)) },
                    color = eyeDark, style = Stroke(width = px(1.8f), cap = StrokeCap.Round),
                )
                drawPath(
                    Path().apply { moveTo(px(60f), px(50f)); quadraticBezierTo(px(66f), px(50f + smile * 3.5f), px(68f), px(cornerY)) },
                    color = eyeDark, style = Stroke(width = px(1.8f), cap = StrokeCap.Round),
                )
                if (smile > 0.95f) {
                    drawRoundRect(
                        color = tongue, topLeft = p(56.5f, 50f), size = Size(px(7f), px(9f)),
                        cornerRadius = CornerRadius(px(3.5f), px(3.5f)),
                    )
                }

                // Eyes + eyebrows behind the shades.
                val eyeY = 31f
                val browY = eyeY - 7f + when (mood) { DogMood.ERROR -> 1.5f; else -> 0f }
                for (cx in listOf(53f, 67f)) {
                    val ry = 3.6f * blink
                    drawOval(color = Color.White, topLeft = Offset(px(cx - 3.6f), px(eyeY - ry)), size = Size(px(7.2f), px(2f * ry)))
                    if (blink > 0.35f) {
                        drawCircle(eyeDark, radius = px(1.9f), center = p(cx + look * 1.8f, eyeY + 0.6f))
                        drawCircle(Color.White, radius = px(0.7f), center = p(cx + look * 1.8f + 0.7f, eyeY - 0.4f))
                    }
                }
                // Eyebrows (angry inward on error, raised when happy).
                val browTilt = when (mood) { DogMood.ERROR -> 2.5f; DogMood.HAPPY, DogMood.SUCCESS -> -1.5f; else -> 0f }
                drawLine(eyeDark, p(49f, browY + browTilt), p(57f, browY - browTilt), strokeWidth = px(1.6f), cap = StrokeCap.Round)
                drawLine(eyeDark, p(63f, browY - browTilt), p(71f, browY + browTilt), strokeWidth = px(1.6f), cap = StrokeCap.Round)

                // ---- Spy sunglasses: slide down the nose to peek while reading email ----
                val gY = eyeY + peek * 8f
                val lensR = px(6.4f)
                for (cx in listOf(53f, 67f)) {
                    drawCircle(lens, radius = lensR, center = p(cx, gY))
                    drawCircle(accent.copy(alpha = 0.35f), radius = lensR, center = p(cx, gY), style = Stroke(width = px(1.4f)))
                    // Cool diagonal glint.
                    drawLine(Color.White.copy(alpha = 0.5f), p(cx - 3f, gY + 1.5f), p(cx + 0.5f, gY - 3f), strokeWidth = px(1.3f), cap = StrokeCap.Round)
                }
                drawLine(lens, p(57.5f, gY), p(62.5f, gY), strokeWidth = px(2.4f), cap = StrokeCap.Round)
                drawLine(lens, p(47f, gY - 1f), p(40f, eyeY - 3f), strokeWidth = px(2f), cap = StrokeCap.Round)
                drawLine(lens, p(73f, gY - 1f), p(80f, eyeY - 3f), strokeWidth = px(2f), cap = StrokeCap.Round)

                // ---- Paws on top when covering the eyes ----
                if (cover > 0.01f) {
                    val pawR = px(9f)
                    for (pw in listOf(pawL, pawR)) {
                        drawCircle(color = furDark, radius = pawR, center = pw)
                        drawCircle(color = Color.White.copy(alpha = 0.14f), radius = pawR * 0.55f, center = Offset(pw.x - pawR * 0.25f, pw.y - pawR * 0.25f))
                        for (dx in listOf(-3.3f, 0f, 3.3f)) {
                            drawCircle(color = belly.copy(alpha = 0.6f * cover), radius = px(1.2f), center = Offset(pw.x + px(dx), pw.y + px(3.2f)))
                        }
                    }
                }
            }
        }
    }
}

private fun earPath(px: (Float) -> Float, left: Boolean): Path = Path().apply {
    if (left) {
        moveTo(px(45f), px(24f))
        cubicTo(px(32f), px(22f), px(28f), px(40f), px(34f), px(50f))
        cubicTo(px(38f), px(56f), px(46f), px(50f), px(46f), px(42f))
        cubicTo(px(46f), px(34f), px(49f), px(26f), px(45f), px(24f))
    } else {
        moveTo(px(75f), px(24f))
        cubicTo(px(88f), px(22f), px(92f), px(40f), px(86f), px(50f))
        cubicTo(px(82f), px(56f), px(74f), px(50f), px(74f), px(42f))
        cubicTo(px(74f), px(34f), px(71f), px(26f), px(75f), px(24f))
    }
    close()
}

private fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun lerpO(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

@Composable
private fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onBg: Color,
    muted: Color,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    animate: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && animate) 1.015f else 1f,
        animationSpec = tween(180),
        label = "fieldScale",
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        leadingIcon = { Icon(icon, contentDescription = null) },
        placeholder = { Text(hint) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AppTheme.colors.card,
            unfocusedContainerColor = AppTheme.colors.card.copy(alpha = 0.5f),
            focusedBorderColor = accent,
            unfocusedBorderColor = onBg.copy(alpha = 0.14f),
            cursorColor = accent,
            focusedTextColor = onBg,
            unfocusedTextColor = onBg,
            focusedPlaceholderColor = muted,
            unfocusedPlaceholderColor = muted,
            focusedLeadingIconColor = accent,
            unfocusedLeadingIconColor = muted,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            },
    )
}

@Composable
private fun GradientButton(
    label: String,
    gradient: List<Color>,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val textColor by animateColorAsState(
        targetValue = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
        label = "btnText",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(if (enabled) 10.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradient))
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Text(label, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LinkText(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
    )
}
