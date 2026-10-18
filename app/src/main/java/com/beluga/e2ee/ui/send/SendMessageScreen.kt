package com.beluga.e2ee.ui.send

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beluga.e2ee.ui.theme.BelugaCyan
import com.beluga.e2ee.ui.theme.BelugaError
import com.beluga.e2ee.ui.theme.BelugaMint
import com.beluga.e2ee.ui.theme.BelugaNavyDark
import com.beluga.e2ee.ui.theme.BelugaNavyLight
import com.beluga.e2ee.ui.theme.BelugaNavyMedium
import com.beluga.e2ee.ui.theme.BelugaWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMessageScreen(
    viewModel: SendViewModel,
    preselectedContactId: String?,
    onBack: () -> Unit,
    onNavigateToQrDisplay: () -> Unit,
    onNavigateToContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val activeContacts by viewModel.activeContacts.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(preselectedContactId) {
        if (!preselectedContactId.isNullOrEmpty()) {
            viewModel.selectContact(preselectedContactId)
        }
    }

    // Auto-select first active contact if none selected
    LaunchedEffect(activeContacts) {
        if (state.selectedContactId == null && activeContacts.isNotEmpty()) {
            viewModel.selectContact(activeContacts.first().userId)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BelugaNavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Compose Message", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BelugaNavyDark)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (activeContacts.isEmpty()) {
                // No active sessions notice
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToContacts),
                    colors = CardDefaults.cardColors(containerColor = BelugaNavyMedium),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = BelugaWarning,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Active Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You need an active cryptographic session before sending messages. Tap here to initiate a handshake with a contact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToContacts,
                            colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = BelugaNavyDark)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Go to Contacts & Sessions", color = BelugaNavyDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Contact Selector
                Text(
                    text = "Recipient",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = state.selectedContactId ?: "Select contact",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BelugaCyan,
                            unfocusedBorderColor = BelugaNavyLight,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(BelugaNavyMedium)
                    ) {
                        activeContacts.forEach { contact ->
                            DropdownMenuItem(
                                text = { Text(contact.userId, color = Color.White) },
                                onClick = {
                                    viewModel.selectContact(contact.userId)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Plaintext Message Input
                Text(
                    text = "Plaintext Message",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = state.plaintext,
                    onValueChange = { viewModel.onPlaintextChanged(it) },
                    placeholder = { Text("Type your message here…", color = Color.Gray) },
                    minLines = 6,
                    maxLines = 14,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BelugaCyan,
                        unfocusedBorderColor = BelugaNavyLight,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Live Payload & Chunk Estimator Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BelugaNavyMedium, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${state.plaintext.length} chars · ~${state.estimatedCompressedBytes} compressed bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                        Text(
                            text = if (state.estimatedChunkCount == 1) {
                                "Single QR Code (Mode 1)"
                            } else {
                                "${state.estimatedChunkCount} Animated Chunks (Mode 2)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.estimatedChunkCount == 1) BelugaMint else BelugaCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(BelugaNavyLight, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${state.estimatedChunkCount} QR",
                            style = MaterialTheme.typography.labelMedium,
                            color = BelugaCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.errorMessage ?: "",
                        color = BelugaError,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Encrypt and Generate QR Button
                Button(
                    onClick = {
                        viewModel.encryptAndGenerateQrs(onNavigateToQrDisplay)
                    },
                    enabled = !state.isEncrypting && state.plaintext.isNotBlank() && state.selectedContactId != null,
                    colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isEncrypting) {
                        CircularProgressIndicator(
                            color = BelugaNavyDark,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = BelugaNavyDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Encrypt & Generate QR Code",
                            color = BelugaNavyDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
