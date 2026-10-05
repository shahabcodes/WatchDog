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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
private enum class MascotMood { NEUTRAL, HAPPY, ERROR, SUCCESS }

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = AppTheme.colors.accent
    val gradient = AppTheme.palette.gradient.ifEmpty { listOf(accent, accent) }
    val reducedMotion = rememberReducedMotion()

    var focusedField by remember { mutableStateOf(FocusField.NONE) }

    // Mascot reactions, driven by what the user is doing right now.
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
        state.errorMessage != null -> MascotMood.ERROR
        state.infoMessage != null -> MascotMood.SUCCESS
        focusedField == FocusField.EMAIL -> MascotMood.HAPPY
        else -> MascotMood.NEUTRAL
    }

    // Entrance: content fades and rises in (instant when reduced).
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

            Mascot(
                gradient = gradient,
                accent = accent,
                cover = cover,
                look = look,
                mood = mood,
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
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when (mode) {
                            AuthMode.SIGN_IN -> "Keep your family's devices safe."
                            AuthMode.SIGN_UP -> "A free account protects every device."
                            AuthMode.FORGOT -> "We'll email you a secure reset link."
                        },
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // Glass form card.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlassField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    hint = "Email",
                    icon = Icons.Filled.MailOutline,
                    accent = accent,
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

            // Mode-switch links.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (state.mode) {
                    AuthMode.SIGN_IN -> {
                        LinkText("Forgot password?", accent) { viewModel.setMode(AuthMode.FORGOT) }
                        LinkText("New here?  Create an account", Color.White.copy(alpha = 0.85f)) {
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

/** A dark cosmic backdrop with glowing theme-coloured blobs — drifting, or static when reduced. */
@Composable
private fun AuroraBackground(gradient: List<Color>, accent: Color, animate: Boolean) {
    val colors = (gradient + accent)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0C1016), Color(0xFF060810)))),
    ) {
        if (animate) {
            val transition = rememberInfiniteTransition(label = "aurora")
            val t by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
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
            val cx = w * (0.5f + 0.42f * cos(phase + i))
            val cy = h * (0.42f + 0.40f * sin(phase * 0.8f + i * 1.3f))
            val r = size.minDimension * (0.55f + 0.12f * sin(phase).let { it * it })
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.42f), c.copy(alpha = 0.10f), Color.Transparent),
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
 * A living shield "buddy": its eyes track the email text, it covers its eyes with
 * its paws when a password field is focused, blinks, bobs, and changes mood.
 */
@Composable
private fun Mascot(
    gradient: List<Color>,
    accent: Color,
    cover: Float,
    look: Float,
    mood: MascotMood,
    animate: Boolean,
) {
    if (animate) {
        val transition = rememberInfiniteTransition(label = "mascot")
        val cycle by transition.animateFloat(
            0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "cycle",
        )
        val bob by transition.animateFloat(
            -1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
        )
        val blink = if (cycle > 0.95f) 1f - sin(((cycle - 0.95f) / 0.05f) * PI).toFloat() else 1f
        MascotVisual(gradient, accent, cover, look, mood, blink = blink, bob = bob)
    } else {
        MascotVisual(gradient, accent, cover, look, mood, blink = 1f, bob = 0f)
    }
}

@Composable
private fun MascotVisual(
    gradient: List<Color>,
    accent: Color,
    cover: Float,
    look: Float,
    mood: MascotMood,
    blink: Float,
    bob: Float,
) {
    val eyeWhite = Color.White
    val eyeDark = Color(0xFF16232E)
    val pawColor = gradient.getOrElse(gradient.size - 1) { accent }
    val smile = when (mood) {
        MascotMood.SUCCESS -> 1f
        MascotMood.HAPPY -> 0.7f
        MascotMood.NEUTRAL -> 0.4f
        MascotMood.ERROR -> -0.6f
    }

    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val u = size.minDimension / 108f
            fun px(v: Float) = v * u
            val bobPx = bob * 3f * u

            // Soft accent halo behind the buddy.
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.32f), Color.Transparent),
                    center = center, radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
                center = center,
            )

            translate(top = bobPx) {
                // Shield body in the theme gradient.
                val shield = Path().apply {
                    moveTo(px(54f), px(28f))
                    cubicTo(px(49f), px(28f), px(43f), px(30f), px(38f), px(31f))
                    cubicTo(px(34f), px(32f), px(32.5f), px(33.5f), px(32.5f), px(38f))
                    lineTo(px(32.5f), px(56f))
                    cubicTo(px(32.5f), px(69f), px(41f), px(78f), px(54f), px(83.5f))
                    cubicTo(px(67f), px(78f), px(75.5f), px(69f), px(75.5f), px(56f))
                    lineTo(px(75.5f), px(38f))
                    cubicTo(px(75.5f), px(33.5f), px(74f), px(32f), px(70f), px(31f))
                    cubicTo(px(65f), px(30f), px(59f), px(28f), px(54f), px(28f))
                    close()
                }
                drawPath(shield, brush = Brush.verticalGradient(gradient, startY = px(28f), endY = px(84f)))
                // Top sheen.
                drawPath(shield, color = Color.White.copy(alpha = 0.10f))

                val eyeY = 49f
                val lookDown = if (mood == MascotMood.HAPPY) 1.1f else 0f
                // Eyes (whites), squashed vertically while blinking.
                for (cx in listOf(46f, 62f)) {
                    val ry = 5.2f * blink
                    drawOval(
                        color = eyeWhite,
                        topLeft = Offset(px(cx - 5.2f), px(eyeY - ry)),
                        size = Size(px(10.4f), px(2f * ry)),
                    )
                }
                // Pupils follow the look direction (hidden while blinking shut).
                if (blink > 0.35f) {
                    for (cx in listOf(46f, 62f)) {
                        drawCircle(
                            color = eyeDark,
                            radius = px(2.5f),
                            center = Offset(px(cx + look * 2.3f), px(eyeY + lookDown)),
                        )
                        // Catch-light.
                        drawCircle(
                            color = Color.White,
                            radius = px(0.8f),
                            center = Offset(px(cx + look * 2.3f + 0.9f), px(eyeY + lookDown - 1f)),
                        )
                    }
                }

                // Cheeks when happy.
                if (smile > 0.5f) {
                    for (cx in listOf(41f, 67f)) {
                        drawCircle(
                            color = accent.copy(alpha = 0.35f * ((smile - 0.5f) / 0.5f)),
                            radius = px(3.2f),
                            center = Offset(px(cx), px(56f)),
                        )
                    }
                }

                // Mouth: smile / frown via a quadratic curve.
                val mouth = Path().apply {
                    moveTo(px(49f), px(61f))
                    quadraticBezierTo(px(54f), px(61f + smile * 6f), px(59f), px(61f))
                }
                drawPath(mouth, color = eyeDark, style = Stroke(width = px(2.3f), cap = StrokeCap.Round))

                // Paws rise from the chin to cover the eyes while a password field is focused.
                if (cover > 0.01f) {
                    val lp = lerp(Offset(px(44f), px(86f)), Offset(px(46f), px(49f)), cover)
                    val rp = lerp(Offset(px(64f), px(86f)), Offset(px(62f), px(49f)), cover)
                    val pawR = px(10f)
                    for (p in listOf(lp, rp)) {
                        drawCircle(color = pawColor, radius = pawR, center = p)
                        drawCircle(color = Color.White.copy(alpha = 0.12f), radius = pawR * 0.6f, center = Offset(p.x - pawR * 0.25f, p.y - pawR * 0.25f))
                    }
                }
            }
        }
    }
}

private fun lerp(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

@Composable
private fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
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
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            cursorColor = accent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
            focusedLeadingIconColor = accent,
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.6f),
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
        targetValue = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
        label = "btnText",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradient))
            .alpha(if (enabled) 1f else 0.45f)
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
