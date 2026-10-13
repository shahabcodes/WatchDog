package com.shahaabapps.watchdog.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
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
                if (state.mode != AuthMode.SIGN_IN) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable { viewModel.setMode(AuthMode.SIGN_IN) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Lighthouse mark.
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (state.mode) {
                        AuthMode.SIGN_IN -> "Welcome back"
                        AuthMode.SIGN_UP -> "Light the way"
                        AuthMode.FORGOT -> "Reset password"
                    },
                    fontFamily = AppFonts.display,
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
            FieldLabel("Email")
            LhField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
            )

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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) GOLD else GOLD.copy(alpha = 0.45f))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = NAVY_DEEP, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, color = NAVY_DEEP, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
