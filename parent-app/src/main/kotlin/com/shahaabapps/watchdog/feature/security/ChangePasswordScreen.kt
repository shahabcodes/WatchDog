package com.shahaabapps.watchdog.feature.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.firebase.auth.PasswordRule
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.GuardianHeader
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground),
    ) {
        GuardianHeader(title = "Change password", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            when (state.step) {
                ChangePasswordStep.FORM -> {
                    PasswordInput("Current password", state.current, viewModel::onCurrent)
                    PasswordInput("New password", state.newPassword, viewModel::onNew)
                    Text(
                        state.newProblem ?: PasswordRule.HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.newProblem != null) AppTheme.colors.destructive else AppTheme.colors.secondaryLabel,
                    )
                    PasswordInput("Confirm new password", state.confirm, viewModel::onConfirm)
                    if (state.mismatch) {
                        Text("Passwords don't match", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.destructive)
                    }
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.destructive)
                    }
                    CupertinoPrimaryButton(
                        text = if (state.busy) "Changing…" else "Change password",
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                    )
                }

                ChangePasswordStep.CODE -> {
                    Text("Enter your verification code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Enter the 6-digit code from your authenticator app to confirm it's you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    com.shahaabapps.watchdog.ui.OtpInput(
                        value = state.code,
                        onValueChange = viewModel::onCode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.destructive)
                    }
                    CupertinoPrimaryButton(
                        text = "Continue",
                        onClick = viewModel::submitCode,
                        enabled = state.code.length == 6 && !state.busy,
                    )
                }

                ChangePasswordStep.DONE -> {
                    Text("Password changed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Use your new password next time you sign in, on any phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    CupertinoPrimaryButton(text = "Done", onClick = onBack)
                }
            }
        }
    }
}

@Composable
private fun PasswordInput(label: String, value: String, onValueChange: (String) -> Unit) =
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
