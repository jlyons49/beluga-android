package com.beluga.e2ee.ui.sharekey

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.beluga.e2ee.crypto.FingerprintGenerator
import com.beluga.e2ee.protocol.E2eSystem
import com.beluga.e2ee.qr.QrEncoder
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
fun ShareKeyScreen(
    e2eSystem: E2eSystem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sharePayload by remember { mutableStateOf("") }
    var safetyNumber by remember { mutableStateOf("") }
    var shortKeyId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val payload = e2eSystem.shareIdentityKey()
        sharePayload = payload
        val pubBytes = e2eSystem.getMyPublicKeyBytes()
        safetyNumber = FingerprintGenerator.generateSafetyNumber(pubBytes, multiline = true)
        shortKeyId = e2eSystem.getMyShortKeyId()
        qrBitmap = QrEncoder.encode(payload, 800)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BelugaNavyDark,
        topBar = {
            TopAppBar(
                title = { Text("My Identity Key", color = Color.White, fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Crisp White QR Card
            QrCodeCard(
                bitmap = qrBitmap,
                title = "Public Key QR (Mode 6)",
                caption = "Scan with Beluga on another device to add as contact"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Share QR image button
            Button(
                onClick = {
                    qrBitmap?.let { bmp ->
                        scope.launch {
                            QrImageSharer.shareSingleQr(
                                context = context,
                                bitmap = bmp,
                                title = "Beluga Public Key - $shortKeyId"
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BelugaCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = BelugaNavyDark)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Public Key QR Image", color = BelugaNavyDark, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 60-digit Safety Number Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BelugaNavyMedium, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "60-Digit Safety Number",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Box(
                        modifier = Modifier
                            .background(BelugaNavyLight, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = shortKeyId,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = BelugaMint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BelugaNavyDark, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = safetyNumber,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                            lineHeight = 24.sp
                        ),
                        color = BelugaCyan,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Beluga Safety Number", safetyNumber.replace("\n", " "))
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Safety number copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Safety Number", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
