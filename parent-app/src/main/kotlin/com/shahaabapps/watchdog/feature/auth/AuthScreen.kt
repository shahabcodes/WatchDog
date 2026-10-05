package com.shahaabapps.watchdog.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = AppTheme.colors.accent
    val gradient = AppTheme.palette.gradient.ifEmpty { listOf(accent, accent) }

    // Entrance: content fades and rises in on first composition.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(gradient = gradient, accent = accent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .alpha(enter.value),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height((40 * (1f - enter.value)).dp))

            GlowLogo(gradient = gradient, accent = accent)

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

/** A dark cosmic backdrop with slowly drifting glowing blobs in the theme colours. */
@Composable
private fun AuroraBackground(gradient: List<Color>, accent: Color) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val colors = (gradient + accent)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0C1016), Color(0xFF060810)))),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val two = (2f * Math.PI).toFloat()
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
}

/** The brand shield with a soft pulsing halo and expanding accent rings. */
@Composable
private fun GlowLogo(gradient: List<Color>, accent: Color) {
    val transition = rememberInfiniteTransition(label = "logo")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f
            // Two expanding rings staggered in phase.
            for (i in 0..1) {
                val p = ((pulse + i * 0.5f) % 1f)
                drawCircle(
                    color = accent.copy(alpha = (1f - p) * 0.4f),
                    radius = maxR * (0.5f + 0.5f * p),
                    center = center,
                    style = Stroke(width = 3f * (1f - p) + 1f),
                )
            }
            // Soft halo.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                    center = center, radius = maxR,
                ),
                radius = maxR, center = center,
            )
        }
        Box(
            modifier = Modifier
                .scale(breathe)
                .size(78.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
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
        modifier = Modifier.fillMaxWidth(),
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
    val alpha by animateColorAsState(
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
            Text(label, color = alpha, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
