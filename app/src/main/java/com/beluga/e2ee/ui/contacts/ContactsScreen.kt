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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beluga.e2ee.ui.theme.BelugaCyan
import com.beluga.e2ee.ui.theme.BelugaError
import com.beluga.e2ee.ui.theme.BelugaMint
import com.beluga.e2ee.ui.theme.BelugaNavyDark
import com.beluga.e2ee.ui.theme.BelugaNavyLight
import com.beluga.e2ee.ui.theme.BelugaNavyMedium
import com.beluga.e2ee.ui.theme.BelugaTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onBack: () -> Unit,
    onAddContact: () -> Unit,
    onStartHandshake: (userId: String) -> Unit,
    onSendMessage: (userId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val contactItems by viewModel.contactItems.collectAsState()
    var verifyDialogContact by remember { mutableStateOf<ContactItemUi?>(null) }
    var deleteDialogContact by remember { mutableStateOf<ContactItemUi?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BelugaNavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Contacts & Sessions", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BelugaNavyDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddContact,
                containerColor = BelugaCyan,
                contentColor = BelugaNavyDark,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { innerPadding ->
        if (contactItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Contacts Saved",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scan a peer's Mode 6 QR code or tap '+' below to add a contact",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(contactItems, key = { it.contact.userId }) { item ->
                    ContactCard(
                        item = item,
                        onVerifyClick = { verifyDialogContact = item },
                        onHandshakeClick = { onStartHandshake(item.contact.userId) },
                        onSendMessageClick = { onSendMessage(item.contact.userId) },
                        onDeleteClick = { deleteDialogContact = item }
                    )
                }
            }
        }
    }

    // Safety Number Verification Dialog
    verifyDialogContact?.let { item ->
        AlertDialog(
            onDismissRequest = { verifyDialogContact = null },
            containerColor = BelugaNavyMedium,
            title = {
                Text(
                    text = "Verify Safety Number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "Compare this 60-digit number with ${item.contact.userId}'s device in person or via trusted channel:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BelugaNavyDark, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = item.safetyNumber.chunked(20).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            ),
                            color = BelugaCyan,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (item.contact.verified) "Status: Verified ✓" else "Status: Unverified",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.contact.verified) BelugaMint else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                viewModel.toggleVerified(item.contact.userId, item.contact.verified)
                                verifyDialogContact = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.contact.verified) Color.DarkGray else BelugaMint
                            )
                        ) {
                            Text(
                                text = if (item.contact.verified) "Mark Unverified" else "Mark Verified",
                                color = BelugaNavyDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { verifyDialogContact = null }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // Delete Contact Confirmation Dialog
    deleteDialogContact?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteDialogContact = null },
            containerColor = BelugaNavyMedium,
            title = { Text("Delete Contact?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${item.contact.userId}'? Any active session and pending handshakes will also be removed.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteContact(item.contact.userId)
                        deleteDialogContact = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BelugaError)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogContact = null }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun ContactCard(
    item: ContactItemUi,
    onVerifyClick: () -> Unit,
    onHandshakeClick: () -> Unit,
    onSendMessageClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BelugaNavyMedium),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: User ID, Short Key ID badge, Verified indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.contact.userId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (item.contact.verified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Contact",
                            tint = BelugaMint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(BelugaNavyLight, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.shortKeyId,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = BelugaCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Session Status Badge
            val session = item.session
            if (session != null) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF003B46), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Active Session [${session.sid}] · Sent #${session.txSeq} / Rx #${session.lastRxSeq}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BelugaCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(BelugaNavyLight, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "No Active Session",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Handshake Button
                Button(
                    onClick = onHandshakeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (session == null) BelugaCyan else BelugaNavyLight
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = if (session == null) BelugaNavyDark else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (session == null) "Handshake" else "Rotate",
                        color = if (session == null) BelugaNavyDark else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Send Message Button (active only if session exists)
                if (session != null) {
                    Button(
                        onClick = onSendMessageClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BelugaTeal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Verify Safety Number Button
                OutlinedButton(
                    onClick = onVerifyClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = "Safety Number", tint = BelugaMint, modifier = Modifier.size(16.dp))
                }

                // Delete Contact Button
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
            }
        }
    }
}
