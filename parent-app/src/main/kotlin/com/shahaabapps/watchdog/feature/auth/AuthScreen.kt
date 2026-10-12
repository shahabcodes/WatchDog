package com.shahaabapps.watchdog.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.shahaabapps.core.ui.theme.AppTheme

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onBg = MaterialTheme.colorScheme.onBackground
    val accent = AppTheme.colors.accent

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        // Back button for the secondary modes; logo mark for sign in.
        if (state.mode == AuthMode.SIGN_IN) {
            Spacer(Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        } else {
            Surface(
                shape = CircleShape,
                color = AppTheme.colors.card,
                border = BorderStroke(1.dp, AppTheme.colors.separator),
                modifier = Modifier.size(44.dp).clickable { viewModel.setMode(AuthMode.SIGN_IN) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = onBg,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = when (state.mode) {
                AuthMode.SIGN_IN -> "Welcome back"
                AuthMode.SIGN_UP -> "Create your account"
                AuthMode.FORGOT -> "Reset password"
            },
            fontFamily = AppFonts.display,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            color = onBg,
        )
        Text(
            text = when (state.mode) {
                AuthMode.SIGN_IN -> "Log in to keep an eye on your family."
                AuthMode.SIGN_UP -> "A parent account controls the family."
                AuthMode.FORGOT -> "Enter your email and we'll send you a link to set a new password."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(26.dp))

        FieldLabel("Email")
        AuthField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
        )

        AnimatedVisibility(
            visible = !state.isForgot,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                FieldLabel("Password")
                PasswordField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    placeholder = "••••••••",
                )
            }
        }

        AnimatedVisibility(
            visible = state.isSignUp,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                FieldLabel("Confirm password")
                PasswordField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    placeholder = "••••••••",
                    isError = state.passwordsMismatch,
                )
                if (state.passwordsMismatch) {
                    Text(
                        "Passwords don't match",
                        color = AppTheme.colors.destructive,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
            }
        }

        // Forgot-password link on the sign-in form.
        if (state.mode == AuthMode.SIGN_IN) {
            Text(
                "Forgot password?",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 12.dp)
                    .clickable { viewModel.setMode(AuthMode.FORGOT) },
            )
        }

        state.errorMessage?.let {
            Text(
                it,
                color = AppTheme.colors.destructive,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        state.infoMessage?.let {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppTheme.colors.card,
                border = BorderStroke(1.dp, AppTheme.colors.separator),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(
                    it,
                    color = accent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = when (state.mode) {
                AuthMode.SIGN_IN -> "Log in"
                AuthMode.SIGN_UP -> "Create account"
                AuthMode.FORGOT -> "Send reset link"
            },
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            onClick = viewModel::submit,
        )

        Spacer(Modifier.height(28.dp))
        // Footer: switch between sign in and sign up.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            when (state.mode) {
                AuthMode.SIGN_UP, AuthMode.FORGOT -> {
                    Text("Already have an account? ", color = AppTheme.colors.secondaryLabel, fontSize = 15.sp)
                    Text(
                        "Log in",
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.setMode(AuthMode.SIGN_IN) },
                    )
                }
                AuthMode.SIGN_IN -> {
                    Text("New here? ", color = AppTheme.colors.secondaryLabel, fontSize = 15.sp)
                    Text(
                        "Create an account",
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.setMode(AuthMode.SIGN_UP) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.colors.secondaryLabel,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun AuthField(
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
        placeholder = { Text(placeholder, color = AppTheme.colors.secondaryLabel.copy(alpha = 0.7f)) },
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AppTheme.colors.card,
            unfocusedContainerColor = AppTheme.colors.card,
            errorContainerColor = AppTheme.colors.card,
            focusedIndicatorColor = AppTheme.colors.accent,
            unfocusedIndicatorColor = AppTheme.colors.separator,
            errorIndicatorColor = AppTheme.colors.destructive,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    AuthField(
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
                tint = AppTheme.colors.secondaryLabel,
                modifier = Modifier.clickable { visible = !visible },
            )
        },
    )
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val accent = AppTheme.colors.accent
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) accent else accent.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(enabled = enabled && !loading, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
