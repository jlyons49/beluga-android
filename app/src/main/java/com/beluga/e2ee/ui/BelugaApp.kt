package com.beluga.e2ee.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.beluga.e2ee.BelugaApplication
import com.beluga.e2ee.ui.contacts.AddContactScreen
import com.beluga.e2ee.ui.contacts.ContactsScreen
import com.beluga.e2ee.ui.contacts.ContactsViewModel
import com.beluga.e2ee.ui.home.HomeScreen
import com.beluga.e2ee.ui.receive.ReceiveScreen
import com.beluga.e2ee.ui.receive.ReceiveViewModel
import com.beluga.e2ee.ui.send.QrDisplayScreen
import com.beluga.e2ee.ui.send.SendMessageScreen
import com.beluga.e2ee.ui.send.SendViewModel
import com.beluga.e2ee.ui.session.SessionHandshakeScreen
import com.beluga.e2ee.ui.session.SessionViewModel
import com.beluga.e2ee.ui.sharekey.ShareKeyScreen
import com.beluga.e2ee.ui.unlock.UnlockScreen
import com.beluga.e2ee.ui.unlock.UnlockViewModel

sealed class Screen(val route: String) {
    object Unlock : Screen("unlock")
    object Home : Screen("home")
    object ShareKey : Screen("share_key")
    object Contacts : Screen("contacts")
    object AddContact : Screen("add_contact")
    object Handshake : Screen("handshake/{userId}") {
        fun createRoute(userId: String) = "handshake/$userId"
    }
    object SendMessage : Screen("send?userId={userId}") {
        fun createRoute(userId: String? = null) = if (userId != null) "send?userId=$userId" else "send"
    }
    object QrDisplay : Screen("qr_display")
    object Receive : Screen("receive")
}

@Composable
fun BelugaApp(
    application: BelugaApplication,
    isSecureScreen: Boolean,
    onToggleSecureScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val repository = application.databaseRepository
    val e2eSystem = application.e2eSystem
    val isUnlocked by repository.isUnlocked.collectAsState()

    // Issue #11 fix: When database is locked (auto-lock or manual), redirect to Unlock and pop backstack
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            navController.navigate(Screen.Unlock.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Shared ViewModels scoped across the navigation flow
    val unlockViewModel: UnlockViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UnlockViewModel(repository) as T
            }
        }
    )

    val contactsViewModel: ContactsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ContactsViewModel(e2eSystem, repository) as T
            }
        }
    )

    val sessionViewModel: SessionViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionViewModel(e2eSystem) as T
            }
        }
    )

    val sendViewModel: SendViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SendViewModel(e2eSystem, repository) as T
            }
        }
    )

    val receiveViewModel: ReceiveViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReceiveViewModel(e2eSystem) as T
            }
        }
    )

    NavHost(
        navController = navController,
        startDestination = if (isUnlocked) Screen.Home.route else Screen.Unlock.route,
        modifier = modifier
    ) {
        composable(Screen.Unlock.route) {
            UnlockScreen(
                viewModel = unlockViewModel,
                onUnlocked = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Unlock.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                e2eSystem = e2eSystem,
                repository = repository,
                isSecureScreen = isSecureScreen,
                onToggleSecureScreen = onToggleSecureScreen,
                onNavigateToShareKey = { navController.navigate(Screen.ShareKey.route) },
                onNavigateToContacts = { navController.navigate(Screen.Contacts.route) },
                onNavigateToSend = { navController.navigate(Screen.SendMessage.createRoute()) },
                onNavigateToReceive = { navController.navigate(Screen.Receive.route) }
            )
        }

        composable(Screen.ShareKey.route) {
            ShareKeyScreen(
                e2eSystem = e2eSystem,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Contacts.route) {
            ContactsScreen(
                viewModel = contactsViewModel,
                onBack = { navController.popBackStack() },
                onAddContact = { navController.navigate(Screen.AddContact.route) },
                onStartHandshake = { userId ->
                    navController.navigate(Screen.Handshake.createRoute(userId))
                },
                onSendMessage = { userId ->
                    navController.navigate(Screen.SendMessage.createRoute(userId))
                }
            )
        }

        composable(Screen.AddContact.route) {
            AddContactScreen(
                viewModel = contactsViewModel,
                onBack = { navController.popBackStack() },
                onContactSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Handshake.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            SessionHandshakeScreen(
                userId = userId,
                viewModel = sessionViewModel,
                onBack = { navController.popBackStack() },
                onSendMessage = { targetUser ->
                    navController.navigate(Screen.SendMessage.createRoute(targetUser)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(
            route = Screen.SendMessage.route,
            arguments = listOf(navArgument("userId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            SendMessageScreen(
                viewModel = sendViewModel,
                preselectedContactId = userId,
                onBack = { navController.popBackStack() },
                onNavigateToQrDisplay = { navController.navigate(Screen.QrDisplay.route) },
                onNavigateToContacts = { navController.navigate(Screen.Contacts.route) }
            )
        }

        composable(Screen.QrDisplay.route) {
            QrDisplayScreen(
                viewModel = sendViewModel,
                onBack = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        composable(Screen.Receive.route) {
            ReceiveScreen(
                viewModel = receiveViewModel,
                onBack = { navController.popBackStack() },
                onReplyToContact = { userId ->
                    navController.navigate(Screen.SendMessage.createRoute(userId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }
    }
}
