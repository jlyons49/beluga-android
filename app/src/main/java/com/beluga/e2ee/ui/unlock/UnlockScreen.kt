package com.beluga.e2ee.ui.unlock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beluga.e2ee.ui.theme.BelugaCyan
import com.beluga.e2ee.ui.theme.BelugaError
import com.beluga.e2ee.ui.theme.BelugaMint
import com.beluga.e2ee.ui.theme.BelugaNavyDark
import com.beluga.e2ee.ui.theme.BelugaNavyLight
import com.beluga.e2ee.ui.theme.BelugaNavyMedium
import com.beluga.e2ee.ui.theme.BelugaWarning

@Composable
fun UnlockScreen(
    viewModel: UnlockViewModel,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = BelugaNavyDark
    ) {
        if (state.isCheckingDb) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BelugaCyan)
            }
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Beluga Logo / Shield Emblem
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(BelugaNavyLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isDatabaseCreated) Icons.Default.Lock else Icons.Default.Security,
                    contentDescription = null,
                    tint = BelugaCyan,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Beluga E2EE",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (state.isDatabaseCreated) {
                    "Unlock Encrypted Vault"
                } else {
                    "Create Encrypted Vault"
                },
                style = MaterialTheme.typography.titleMedium,
                color = BelugaCyan
            )

            Spacer(modifier = Modifier.height(24.dp))

            // First-run: Display Name Field
            AnimatedVisibility(visible = !state.isDatabaseCreated) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = { viewModel.onDisplayNameChanged(it) },
                        label = { Text("Display Name / Nickname") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = BelugaCyan)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BelugaCyan,
                            unfocusedBorderColor = BelugaNavyLight,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = BelugaCyan,
                            unfocusedLabelColor = Color.LightGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Master Password Field
            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.onPasswordChanged(it) },
                label = { Text(if (state.isDatabaseCreated) "Master Password" else "Choose Master Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = BelugaCyan)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = Color.LightGray
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (state.isDatabaseCreated) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (state.isDatabaseCreated) {
                            viewModel.unlockVault(onUnlocked)
                        }
                    }
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BelugaCyan,
                    unfocusedBorderColor = BelugaNavyLight,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = BelugaCyan,
                    unfocusedLabelColor = Color.LightGray
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Password strength bar (First Run)
            AnimatedVisibility(visible = !state.isDatabaseCreated && state.password.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val progress = when (state.passwordStrength) {
                        PasswordStrength.EMPTY -> 0f
                        PasswordStrength.WEAK -> 0.33f
                        PasswordStrength.MEDIUM -> 0.66f
                        PasswordStrength.STRONG -> 1f
                    }
                    val progressColor = when (state.passwordStrength) {
                        PasswordStrength.EMPTY -> Color.Transparent
                        PasswordStrength.WEAK -> BelugaError
                        PasswordStrength.MEDIUM -> BelugaWarning
                        PasswordStrength.STRONG -> BelugaMint
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = progressColor,
                        trackColor = BelugaNavyLight,
                    )
                    Text(
                        text = state.passwordStrength.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = progressColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Confirm Password Field (First Run - Issue #17 Fix)
            AnimatedVisibility(visible = !state.isDatabaseCreated) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = { viewModel.onConfirmPasswordChanged(it) },
                        label = { Text("Confirm Master Password") },
                        leadingIcon = {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = BelugaCyan)
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color.LightGray
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.createVault(onUnlocked) }
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BelugaCyan,
                            unfocusedBorderColor = BelugaNavyLight,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = BelugaCyan,
                            unfocusedLabelColor = Color.LightGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Error message
            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BelugaError,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = {
                    if (state.isDatabaseCreated) {
                        viewModel.unlockVault(onUnlocked)
                    } else {
                        viewModel.createVault(onUnlocked)
                    }
                },
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = BelugaNavyDark,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (state.isDatabaseCreated) "Unlock Vault" else "Create Encrypted Vault",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BelugaNavyDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Air-Gapped Security Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BelugaNavyMedium, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = BelugaMint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Zero Internet permissions. All cryptographic keys are derived and stored locally on your device with AES-256-GCM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}
