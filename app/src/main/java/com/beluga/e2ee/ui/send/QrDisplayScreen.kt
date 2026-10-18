package com.beluga.e2ee.ui.send

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
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beluga.e2ee.qr.QrImageSharer
import com.beluga.e2ee.ui.components.QrCodeCard
import com.beluga.e2ee.ui.theme.BelugaCyan
import com.beluga.e2ee.ui.theme.BelugaMint
import com.beluga.e2ee.ui.theme.BelugaNavyDark
import com.beluga.e2ee.ui.theme.BelugaNavyLight
import com.beluga.e2ee.ui.theme.BelugaNavyMedium
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrDisplayScreen(
    viewModel: SendViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val totalChunks = state.chunkBitmaps.size
    val currentBmp = state.chunkBitmaps.getOrNull(state.currentChunkIndex)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BelugaNavyDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (totalChunks > 1) "Animated QR (${state.currentChunkIndex + 1}/$totalChunks)" else "Encrypted Message QR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High Contrast QR Card
            QrCodeCard(
                bitmap = currentBmp,
                title = if (totalChunks > 1) {
                    "Chunk ${state.currentChunkIndex + 1} of $totalChunks"
                } else {
                    "Encrypted Message (Mode 1)"
                },
                caption = if (totalChunks > 1) {
                    "Hold recipient camera steady to scan cycling frames"
                } else {
                    "Scan with Beluga on recipient's device"
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-chunk controls (only shown when totalChunks > 1)
            if (totalChunks > 1) {
                // Progress Indicator
                LinearProgressIndicator(
                    progress = { (state.currentChunkIndex + 1f) / totalChunks.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = BelugaCyan,
                    trackColor = BelugaNavyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation and Play/Pause Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.previousChunk() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(BelugaNavyMedium, CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Previous Chunk", tint = Color.White)
                    }

                    Button(
                        onClick = { viewModel.toggleAutoCycle() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isAutoCycling) BelugaMint else BelugaCyan
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isAutoCycling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = BelugaNavyDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isAutoCycling) "Pause Auto-Cycle" else "Play Auto-Cycle",
                            color = BelugaNavyDark,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { viewModel.nextChunk() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(BelugaNavyMedium, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Next Chunk", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Speed Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Speed: ", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))

                    listOf(600L to "Fast (600ms)", 900L to "Normal (900ms)", 1200L to "Slow (1200ms)").forEach { (ms, label) ->
                        FilterChip(
                            selected = state.cycleSpeedMs == ms,
                            onClick = { viewModel.setCycleSpeed(ms) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BelugaCyan,
                                selectedLabelColor = BelugaNavyDark
                            ),
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Share QR Button
            Button(
                onClick = {
                    scope.launch {
                        if (totalChunks > 1) {
                            QrImageSharer.shareMultipleQrs(
                                context = context,
                                bitmaps = state.chunkBitmaps,
                                title = "Beluga Encrypted Message ($totalChunks chunks)"
                            )
                        } else {
                            currentBmp?.let { bmp ->
                                QrImageSharer.shareSingleQr(
                                    context = context,
                                    bitmap = bmp,
                                    title = "Beluga Encrypted Message"
                                )
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BelugaNavyLight),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (totalChunks > 1) "Share All $totalChunks QR Images" else "Share QR Image",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done", color = Color.White)
            }
        }
    }
}
