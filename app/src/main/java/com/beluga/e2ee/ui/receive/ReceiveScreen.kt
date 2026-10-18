package com.beluga.e2ee.ui.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beluga.e2ee.ui.components.CameraScannerView
import com.beluga.e2ee.ui.components.QrCodeCard
import com.beluga.e2ee.ui.theme.BelugaCyan
import com.beluga.e2ee.ui.theme.BelugaError
import com.beluga.e2ee.ui.theme.BelugaMint
import com.beluga.e2ee.ui.theme.BelugaNavyDark
import com.beluga.e2ee.ui.theme.BelugaNavyLight
import com.beluga.e2ee.ui.theme.BelugaNavyMedium
import com.beluga.e2ee.ui.theme.BelugaTeal
import com.beluga.e2ee.ui.theme.BelugaWarning

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiveScreen(
    viewModel: ReceiveViewModel,
    onBack: () -> Unit,
    onReplyToContact: (userId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val resultState by viewModel.resultState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BelugaNavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Universal Scanner", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BelugaNavyDark)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live continuous CameraX scanner
            CameraScannerView(
                onQrScanned = { payload ->
                    viewModel.processQrPayload(payload)
                },
                onGalleryUrisPicked = { uris ->
                    viewModel.processGalleryUris(context, uris)
                },
                instructionText = "Scan any Beluga QR: Message, Handshake, or Contact Key",
                modifier = Modifier.fillMaxSize()
            )

            // Multi-Chunk In-Progress Live Overlay (Issue #8 fix)
            if (resultState is ScanResultState.MultiChunkProgress) {
                val progress = resultState as ScanResultState.MultiChunkProgress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(BelugaNavyDark.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Receiving from ${progress.contactId}…",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${progress.receivedCount}/${progress.totalCount} Chunks",
                                style = MaterialTheme.typography.labelMedium,
                                color = BelugaCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Visual Chip Grid of captured vs remaining chunks
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (i in 0 until progress.totalCount) {
                                val isCaptured = progress.receivedIndices.contains(i)
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = if (isCaptured) "${i + 1} ✓" else "${i + 1} …",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (isCaptured) BelugaMint.copy(alpha = 0.25f) else Color.DarkGray.copy(alpha = 0.5f),
                                        labelColor = if (isCaptured) BelugaMint else Color.Gray
                                    ),
                                    border = null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Keep camera steady on the cycling QR codes",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }

            // Error Notice Banner
            if (resultState is ScanResultState.Error) {
                val err = resultState as ScanResultState.Error
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF5A1A1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = BelugaError)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = err.message, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { viewModel.dismissResult() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.LightGray)
                        }
                    }
                }
            }

            // Bottom Sheets / Dialogs for Complete Results

            // 1. Decrypted Message Modal Sheet
            if (resultState is ScanResultState.MessageDecrypted) {
                val decrypted = resultState as ScanResultState.MessageDecrypted
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissResult() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = BelugaNavyDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(BelugaNavyLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BelugaMint, modifier = Modifier.size(36.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Message Decrypted",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "From: ${decrypted.contactId}", style = MaterialTheme.typography.bodyMedium, color = BelugaCyan)
                            Text(text = "•", color = Color.Gray)
                            Text(text = "Seq #${decrypted.seq}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Plaintext Display Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BelugaNavyMedium),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = decrypted.plaintext,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Beluga Decrypted Message", decrypted.plaintext)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BelugaNavyLight),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    viewModel.dismissResult()
                                    onReplyToContact(decrypted.contactId)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = BelugaNavyDark, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reply", color = BelugaNavyDark, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.dismissResult() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scan Another QR", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 2. Handshake Completed Modal Sheet
            if (resultState is ScanResultState.HandshakeCompleted) {
                val handshake = resultState as ScanResultState.HandshakeCompleted
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissResult() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = BelugaNavyDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(BelugaNavyLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BelugaMint, modifier = Modifier.size(36.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Handshake Completed!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "New cryptographic session established with ${handshake.contactId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .background(BelugaNavyLight, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "SID: ${handshake.sid}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = BelugaCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!handshake.weInitiated && handshake.replyQrBitmap != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Show this Reply QR to ${handshake.contactId} so their device can also finalize the session:",
                                style = MaterialTheme.typography.bodySmall,
                                color = BelugaMint,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            QrCodeCard(bitmap = handshake.replyQrBitmap, title = "Reply Handshake QR")
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.dismissResult()
                                onReplyToContact(handshake.contactId)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Send Message", color = BelugaNavyDark, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 3. Contact Discovered Modal Sheet
            if (resultState is ScanResultState.ContactDiscovered) {
                val discovered = resultState as ScanResultState.ContactDiscovered
                var contactName by remember { mutableStateOf(discovered.proposedName) }

                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissResult() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = BelugaNavyDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = BelugaCyan, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Add Scanned Contact", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            label = { Text("Contact Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BelugaCyan,
                                unfocusedBorderColor = BelugaNavyLight,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BelugaNavyMedium),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Short Key ID: ${discovered.shortKeyId}", color = BelugaMint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Safety Number: ${discovered.safetyNumber.replace("\n", " ").take(30)}…", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.saveDiscoveredContact(contactName) {
                                    Toast.makeText(context, "Contact '$contactName' saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = BelugaNavyDark)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Contact", color = BelugaNavyDark, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
