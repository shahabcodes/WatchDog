package com.shahaabapps.watchdog.feature.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.GuardianHeader
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

@Composable
fun MfaScreen(
    onBack: () -> Unit,
    viewModel: MfaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground),
    ) {
        GuardianHeader(title = "Two-factor authentication", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            when (state.step) {
                MfaStep.OVERVIEW -> {
                    CupertinoCard {
                        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Shield, contentDescription = null, tint = if (state.enrolled) AppTheme.colors.success else AppTheme.colors.secondaryLabel)
                                Text(
                                    if (state.enrolled) "  On" else "  Off",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                if (state.enrolled)
                                    "Your account asks for a code from your authenticator app after your password."
                                else
                                    "Add a second step at sign-in using Google Authenticator (or any TOTP app).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.secondaryLabel,
                            )
                        }
                    }
                    state.info?.let { InfoText(it) }
                    if (state.enrolled) {
                        Text(
                            "Turn off two-factor authentication",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.destructive,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::startDisable).padding(Spacing.sm),
                        )
                    } else {
                        CupertinoPrimaryButton(text = "Turn on", onClick = viewModel::startEnable)
                    }
                }

                MfaStep.VERIFY_EMAIL -> {
                    Text("Verify your email first", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Two-factor needs a verified email. We'll send a link to ${state.email.ifBlank { "your email" }} — tap it, then come back and press \"I've verified\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    state.info?.let { InfoText(it) }
                    state.error?.let { ErrorText(it) }
                    CupertinoPrimaryButton(
                        text = if (state.verificationSent) "Resend verification email" else "Send verification email",
                        onClick = viewModel::sendVerificationEmail,
                        enabled = !state.busy,
                    )
                    if (state.verificationSent) {
                        CupertinoPrimaryButton(
                            text = "I've verified — continue",
                            onClick = viewModel::checkedVerification,
                            enabled = !state.busy,
                        )
                    }
                    CancelLink(viewModel::cancel)
                }

                MfaStep.REAUTH -> {
                    Text("Confirm your password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "For your security, re-enter your password to change two-factor settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPassword,
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let { ErrorText(it) }
                    CupertinoPrimaryButton(
                        text = "Continue",
                        onClick = viewModel::submitReauth,
                        enabled = state.password.isNotEmpty() && !state.busy,
                    )
                    CancelLink(viewModel::cancel)
                }

                MfaStep.ENROLL -> {
                    Text("Scan this in your authenticator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Open Google Authenticator → add account → scan. Or enter the key below manually.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    state.enrollment?.let { enr ->
                        CupertinoCard {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                QrImage(content = enr.otpauthUrl, modifier = Modifier.size(220.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppTheme.colors.groupedBackground)
                                        .clickable { clipboard.setText(AnnotatedString(enr.sharedKey)) }
                                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                ) {
                                    Text(
                                        enr.sharedKey,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "Copy key",
                                        tint = AppTheme.colors.accent,
                                        modifier = Modifier.padding(start = Spacing.sm).size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text("Enter the 6-digit code", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.secondaryLabel)
                    com.shahaabapps.watchdog.ui.OtpInput(
                        value = state.code,
                        onValueChange = viewModel::onCode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let { ErrorText(it) }
                    CupertinoPrimaryButton(
                        text = "Verify & turn on",
                        onClick = viewModel::confirmEnroll,
                        enabled = state.code.length == 6 && !state.busy,
                    )
                    CancelLink(viewModel::cancel)
                }
            }
        }
    }
}

@Composable
private fun InfoText(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.success, fontWeight = FontWeight.SemiBold)

@Composable
private fun ErrorText(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.destructive)

@Composable
private fun CancelLink(onClick: () -> Unit) =
    Text(
        "Cancel",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.secondaryLabel,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.sm),
    )
