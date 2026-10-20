package com.shahaabapps.watchdog.feature.security

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.firebase.auth.BackupCodeStatus
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
    val context = LocalContext.current
    // QR codes, setup keys and backup codes: keep them out of screenshots/Recents.
    com.shahaabapps.watchdog.util.SecureScreen()

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
                    state.error?.let { ErrorText(it) }
                    if (state.enrolled) {
                        BackupCodesCard(state.codeStatus, onRegenerate = viewModel::startRegenerate)
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

                MfaStep.REAUTH_CODE -> {
                    if (state.useBackupCode) {
                        Text("Use a backup code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Lost your authenticator? Enter one of your saved backup codes. This turns two-factor off so you can set it up again on your new phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.secondaryLabel,
                        )
                        OutlinedTextField(
                            value = state.backupCode,
                            onValueChange = viewModel::onBackupCode,
                            singleLine = true,
                            label = { Text("Backup code") },
                            placeholder = { Text("XXXXX-XXXXX") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.error?.let { ErrorText(it) }
                        CupertinoPrimaryButton(
                            text = "Turn off two-factor",
                            onClick = viewModel::submitReauthCode,
                            enabled = state.backupCode.isNotBlank() && !state.busy,
                        )
                        TextLink("Use my authenticator instead", viewModel::toggleBackupCode)
                    } else {
                        Text("Enter your verification code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Enter the 6-digit code from your authenticator app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.secondaryLabel,
                        )
                        com.shahaabapps.watchdog.ui.OtpInput(
                            value = state.code,
                            onValueChange = viewModel::onCode,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.error?.let { ErrorText(it) }
                        CupertinoPrimaryButton(
                            text = "Continue",
                            onClick = viewModel::submitReauthCode,
                            enabled = state.code.length == 6 && !state.busy,
                        )
                        TextLink("Lost your authenticator? Use a backup code", viewModel::toggleBackupCode)
                    }
                    CancelLink(viewModel::cancel)
                }

                MfaStep.CODES -> {
                    val allCodes = state.backupCodes.joinToString("\n")
                    Text("Save your backup codes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "If you lose your phone, each code gets you into your account once. Keep them somewhere safe, like a password manager or on paper. You won't see them again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    CupertinoCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            state.backupCodes.chunked(2).forEach { pair ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { c ->
                                        Text(
                                            c,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        TextLink("Copy all") {
                            com.shahaabapps.watchdog.util.copySensitive(context, "Backup codes", allCodes)
                            Toast.makeText(context, "Backup codes copied", Toast.LENGTH_SHORT).show()
                        }
                        TextLink("Share / save") {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Watch Dog backup codes")
                                putExtra(Intent.EXTRA_TEXT, "Watch Dog two-factor backup codes (each works once):\n\n$allCodes")
                            }
                            context.startActivity(Intent.createChooser(send, "Save backup codes"))
                        }
                    }
                    CupertinoPrimaryButton(text = "I've saved them", onClick = viewModel::doneWithCodes)
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
                                        .clickable { com.shahaabapps.watchdog.util.copySensitive(context, "2FA key", enr.sharedKey) }
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

/** Overview card: how many backup codes are left, and a way to get a new set. */
@Composable
private fun BackupCodesCard(status: BackupCodeStatus?, onRegenerate: () -> Unit) {
    CupertinoCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Backup codes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val (text, warn) = when {
                status == null -> "Checking…" to false
                status.remaining == 0 -> "You have no backup codes. Get some, so you can still sign in if you lose your phone." to true
                status.remaining <= 3 -> "${status.remaining} of ${status.total} left. Get a new set soon." to true
                else -> "${status.remaining} of ${status.total} left. Each one signs you in once if you lose your authenticator." to false
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (warn) AppTheme.colors.destructive else AppTheme.colors.secondaryLabel,
            )
            TextLink("Get new backup codes", onRegenerate)
        }
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) =
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.accent,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = Spacing.sm),
    )

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
