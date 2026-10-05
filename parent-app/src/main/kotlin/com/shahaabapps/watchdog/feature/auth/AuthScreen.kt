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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * A living dog that makes the auth flow feel alive: its ears perk or droop with
 * its mood, its eyes track the email text, it covers its eyes with its paws when
 * a password field is focused, blinks, bobs, and pants happily on success.
 */
@Composable
private fun Dog(
    gradient: List<Color>,
    accent: Color,
    cover: Float,
    look: Float,
    mood: DogMood,
    animate: Boolean,
) {
    if (animate) {
        val transition = rememberInfiniteTransition(label = "dog")
        val cycle by transition.animateFloat(
            0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "cycle",
        )
        val bob by transition.animateFloat(
            -1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
        )
        val blink = if (cycle > 0.95f) 1f - sin(((cycle - 0.95f) / 0.05f) * PI).toFloat() else 1f
        DogVisual(gradient, accent, cover, look, mood, blink = blink, bob = bob, orbit = cycle)
    } else {
        DogVisual(gradient, accent, cover, look, mood, blink = 1f, bob = 0f, orbit = 0.12f)
    }
}

@Composable
private fun DogVisual(
    gradient: List<Color>,
    accent: Color,
    cover: Float,
    look: Float,
    mood: DogMood,
    blink: Float,
    bob: Float,
    orbit: Float,
) {
    val fur = gradient
    val furDark = gradient.getOrElse(gradient.size - 1) { accent }
    val eyeDark = Color(0xFF2A2320)
    val muzzle = Color(0xFFFDF3EA)
    val tongue = Color(0xFFF2788F)
    val smile = when (mood) {
        DogMood.SUCCESS -> 1f
        DogMood.HAPPY -> 0.7f
        DogMood.NEUTRAL -> 0.4f
        DogMood.ERROR -> -0.6f
    }
    val earDrop = when (mood) {
        DogMood.ERROR -> 4f
        DogMood.HAPPY, DogMood.SUCCESS -> -2.5f
        DogMood.NEUTRAL -> 0f
    }

    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val u = size.minDimension / 108f
            fun px(v: Float) = v * u
            val center = Offset(size.width / 2f, size.height / 2f)
            val bobPx = bob * 3f * u

            // Soft accent halo + a thin futuristic orbit ring with a travelling dot.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                    center = center, radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
                center = center,
            )
            val ringR = size.minDimension * 0.46f
            drawCircle(accent.copy(alpha = 0.18f), radius = ringR, center = center, style = Stroke(width = px(0.8f)))
            val a = orbit * (2f * PI).toFloat()
            drawCircle(
                color = accent,
                radius = px(2.2f),
                center = Offset(center.x + cos(a) * ringR, center.y + sin(a) * ringR),
            )

            translate(top = bobPx) {
                // Ears (behind the head), drooping shapes that perk with mood.
                translate(top = px(earDrop)) {
                    drawPath(
                        Path().apply {
                            moveTo(px(37f), px(34f))
                            cubicTo(px(26f), px(34f), px(23f), px(52f), px(29f), px(63f))
                            cubicTo(px(34f), px(70f), px(43f), px(64f), px(43f), px(54f))
                            cubicTo(px(43f), px(44f), px(44f), px(36f), px(37f), px(34f))
                            close()
                        },
                        color = furDark,
                    )
                    drawPath(
                        Path().apply {
                            moveTo(px(71f), px(34f))
                            cubicTo(px(82f), px(34f), px(85f), px(52f), px(79f), px(63f))
                            cubicTo(px(74f), px(70f), px(65f), px(64f), px(65f), px(54f))
                            cubicTo(px(65f), px(44f), px(64f), px(36f), px(71f), px(34f))
                            close()
                        },
                        color = furDark,
                    )
                }

                // Head.
                drawCircle(
                    brush = Brush.verticalGradient(fur, startY = px(26f), endY = px(78f)),
                    radius = px(25f),
                    center = Offset(px(54f), px(50f)),
                )
                drawCircle(Color.White.copy(alpha = 0.10f), radius = px(25f), center = Offset(px(54f), px(50f)))

                // Muzzle.
                drawOval(
                    color = muzzle,
                    topLeft = Offset(px(54f - 13f), px(53f)),
                    size = Size(px(26f), px(20f)),
                )

                // Eyes (whites) with tracking pupils, squashed while blinking.
                val eyeY = 46f
                val lookDown = if (mood == DogMood.HAPPY) 1.0f else 0f
                for (cx in listOf(45f, 63f)) {
                    val ry = 5f * blink
                    drawOval(
                        color = Color.White,
                        topLeft = Offset(px(cx - 5f), px(eyeY - ry)),
                        size = Size(px(10f), px(2f * ry)),
                    )
                }
                if (blink > 0.35f) {
                    for (cx in listOf(45f, 63f)) {
                        drawCircle(eyeDark, radius = px(2.5f), center = Offset(px(cx + look * 2.2f), px(eyeY + lookDown)))
                        drawCircle(Color.White, radius = px(0.8f), center = Offset(px(cx + look * 2.2f + 0.9f), px(eyeY + lookDown - 1f)))
                    }
                }

                // Nose + mouth (and tongue when overjoyed).
                drawOval(color = eyeDark, topLeft = Offset(px(54f - 3.2f), px(57f)), size = Size(px(6.4f), px(5f)))
                drawLine(eyeDark, Offset(px(54f), px(62f)), Offset(px(54f), px(64f)), strokeWidth = px(1.6f), cap = StrokeCap.Round)
                val cornerY = 64f - smile * 2f
                drawPath(
                    Path().apply {
                        moveTo(px(54f), px(64f))
                        quadraticBezierTo(px(49f), px(64f + smile * 3f), px(47f), px(cornerY))
                    },
                    color = eyeDark, style = Stroke(width = px(1.8f), cap = StrokeCap.Round),
                )
                drawPath(
                    Path().apply {
                        moveTo(px(54f), px(64f))
                        quadraticBezierTo(px(59f), px(64f + smile * 3f), px(61f), px(cornerY))
                    },
                    color = eyeDark, style = Stroke(width = px(1.8f), cap = StrokeCap.Round),
                )
                if (smile > 0.95f) {
                    drawOval(color = tongue, topLeft = Offset(px(54f - 3.5f), px(64f)), size = Size(px(7f), px(9f)))
                }

                // Paws rise from below to cover the eyes while a password field is focused.
                if (cover > 0.01f) {
                    val lp = lerp(Offset(px(42f), px(92f)), Offset(px(45f), px(46f)), cover)
                    val rp = lerp(Offset(px(66f), px(92f)), Offset(px(63f), px(46f)), cover)
                    val pawR = px(10f)
                    for (p in listOf(lp, rp)) {
                        drawCircle(color = furDark, radius = pawR, center = p)
                        drawCircle(color = Color.White.copy(alpha = 0.14f), radius = pawR * 0.55f, center = Offset(p.x - pawR * 0.25f, p.y - pawR * 0.25f))
                        // Toe beans.
                        for (dx in listOf(-3.5f, 0f, 3.5f)) {
                            drawCircle(color = Color.White.copy(alpha = 0.22f), radius = px(1.3f), center = Offset(p.x + px(dx), p.y + px(3.5f)))
                        }
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
