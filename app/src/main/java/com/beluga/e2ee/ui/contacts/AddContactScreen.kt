package com.beluga.e2ee.ui.contacts

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beluga.e2ee.qr.ZxingQrAnalyzer
import com.beluga.e2ee.ui.components.CameraScannerView
import com.beluga.e2ee.ui.theme.BelugaCyan
import com.beluga.e2ee.ui.theme.BelugaError
import com.beluga.e2ee.ui.theme.BelugaMint
import com.beluga.e2ee.ui.theme.BelugaNavyDark
import com.beluga.e2ee.ui.theme.BelugaNavyLight
import com.beluga.e2ee.ui.theme.BelugaNavyMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    viewModel: ContactsViewModel,
    onBack: () -> Unit,
    onContactSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.addContactState.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetAddContactState()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BelugaNavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Add Contact", color = Color.White, fontWeight = FontWeight.Bold) },
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
        ) {
            if (state.scannedMessage == null) {
                // Scanner Mode
                CameraScannerView(
                    onQrScanned = { qrText ->
                        viewModel.onScanMode6Qr(qrText)
                    },
                    onGalleryUrisPicked = { uris ->
                        uris.firstOrNull()?.let { uri ->
                            val result = ZxingQrAnalyzer.decodeFromUri(context, uri)
                            result.onSuccess { qrText ->
                                viewModel.onScanMode6Qr(qrText)
                            }
                        }
                    },
                    instructionText = "Scan Contact's Mode 6 Public Key QR code",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Scanned Mode: Review and Save
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(BelugaNavyMedium, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = BelugaCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Public Key Scanned",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Contact Name Input (auto-filled from msg.id, user can edit)
                    OutlinedTextField(
                        value = state.nameInput,
                        onValueChange = { viewModel.onNameInputChanged(it) },
                        label = { Text("Contact Name / Nickname") },
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

                    // Key Details Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BelugaNavyMedium),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Short Key ID", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    state.shortKeyId,
                                    color = BelugaMint,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Safety Number Preview", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                state.safetyNumber,
                                color = BelugaCyan,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (state.errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.errorMessage ?: "",
                            color = BelugaError,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.saveContact(onContactSaved) },
                        colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = BelugaNavyDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Contact", color = BelugaNavyDark, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.resetAddContactState() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan Again", color = Color.White)
                    }
                }
            }
        }
    }
}
