package com.beluga.e2ee.ui.session

import android.graphics.Bitmap
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beluga.e2ee.qr.QrImageSharer
import com.beluga.e2ee.qr.ZxingQrAnalyzer
import com.beluga.e2ee.ui.components.CameraScannerView
import com.beluga.e2ee.ui.components.QrCodeCard
import com.beluga.e2ee.ui.theme.BelugaCyan
import com.beluga.e2ee.ui.theme.BelugaError
import com.beluga.e2ee.ui.theme.BelugaMint
import com.beluga.e2ee.ui.theme.BelugaNavyDark
import com.beluga.e2ee.ui.theme.BelugaNavyLight
import com.beluga.e2ee.ui.theme.BelugaNavyMedium
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHandshakeScreen(
    userId: String,
    viewModel: SessionViewModel,
    onBack: () -> Unit,
    onSendMessage: (userId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        viewModel.loadHandshake(userId)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BelugaNavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Handshake: $userId", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BelugaNavyDark)
            )
        }
    ) { innerPadding ->
        if (state.isCompleted) {
            // Handshake Completed Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(BelugaNavyMedium, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = BelugaMint,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Session Established!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Encrypted session ready with $userId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Session ID Badge Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BelugaNavyMedium),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Shared Session ID (SID)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.completedSession?.sid ?: "--------",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = BelugaCyan
                        )
                    }
                }

                // If peer initiated first, show our reply QR so peer can scan it
                if (!state.weInitiated && state.replyQrBitmap != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Show this Reply QR to $userId so their device can also complete the handshake:",
                        style = MaterialTheme.typography.bodySmall,
                        color = BelugaMint,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    QrCodeCard(
                        bitmap = state.replyQrBitmap,
                        title = "Reply Handshake QR"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onSendMessage(userId) },
                    colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = BelugaNavyDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Encrypted Message", color = BelugaNavyDark, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Handshake In-Progress: 2-Tab Guided Stepper
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TabRow(
                    selectedTabIndex = state.selectedTab,
                    containerColor = BelugaNavyMedium,
                    contentColor = BelugaCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                            color = BelugaCyan
                        )
                    }
                ) {
                    Tab(
                        selected = state.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = {
                            Text(
                                "Step 1: Show QR",
                                color = if (state.selectedTab == 0) BelugaCyan else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = state.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = {
                            Text(
                                "Step 2: Scan Peer QR",
                                color = if (state.selectedTab == 1) BelugaCyan else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }

                if (state.errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4A1010))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = BelugaError,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (state.selectedTab == 0) {
                    // Step 1: Show QR
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Show this ephemeral handshake QR to $userId",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = BelugaCyan)
                            }
                        } else {
                            QrCodeCard(
                                bitmap = state.qrBitmap,
                                title = "Mode 3 Handshake QR",
                                caption = "Survives screen rotation. Let your peer scan this code."
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    state.qrBitmap?.let { bmp ->
                                        scope.launch {
                                            QrImageSharer.shareSingleQr(
                                                context = context,
                                                bitmap = bmp,
                                                title = "Beluga Handshake - $userId"
                                            )
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = BelugaNavyDark, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share QR", color = BelugaNavyDark, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.regenerateHandshake() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Regenerate", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.selectTab(1) },
                            colors = ButtonDefaults.buttonColors(containerColor = BelugaNavyLight),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Next: Scan Peer's Handshake QR →", color = Color.White)
                        }
                    }
                } else {
                    // Step 2: Scan Peer QR
                    CameraScannerView(
                        onQrScanned = { qrText ->
                            viewModel.processScannedPeerQr(qrText)
                        },
                        onGalleryUrisPicked = { uris ->
                            uris.firstOrNull()?.let { uri ->
                                val res = ZxingQrAnalyzer.decodeFromUri(context, uri)
                                res.onSuccess { qrText ->
                                    viewModel.processScannedPeerQr(qrText)
                                }
                            }
                        },
                        instructionText = "Scan $userId's Mode 3 Handshake QR code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
