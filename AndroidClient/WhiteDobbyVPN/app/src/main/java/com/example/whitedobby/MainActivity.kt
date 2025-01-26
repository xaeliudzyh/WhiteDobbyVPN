package com.example.whitedobby

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.whitedobby.ui.theme.WhiteDobbyVPNTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.launch

data class UserData(
    val name: String? = null,
    val email: String? = null,
    val profilePictureUrl: String? = null
)

class MainActivity : ComponentActivity() {

    // Firebase: Firestore and Auth (Storage removed)
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Our Credential Manager wrapper
    private val googleAuthUiService by lazy {
        GoogleAuthUiService(
            context = applicationContext,
            credentialManager = androidx.credentials.CredentialManager.create(applicationContext)
        )
    }

    private lateinit var authStateListener: FirebaseAuth.AuthStateListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()

        // Enable Firestore local cache
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings

        // We still create notification channels and handle permissions
        createNotificationChannels()
        requestNotificationPermission()

        setContent {
            WhiteDobbyVPNTheme {
                // Make the entire background Light Gray
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.LightGray
                ) {
                    var currentUserState by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

                    // Listen to FirebaseAuth state changes
                    DisposableEffect(Unit) {
                        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            currentUserState = firebaseAuth.currentUser
                        }
                        auth.addAuthStateListener(authStateListener)
                        onDispose {
                            auth.removeAuthStateListener(authStateListener)
                        }
                    }

                    // If user is NOT authorized, show SignInScreen
                    if (currentUserState == null) {
                        SignInScreen(
                            onSignIn = {
                                lifecycleScope.launch {
                                    googleAuthUiService.signIn()
                                    googleAuthUiService.getSignedInUser()?.let { user ->
                                        saveUserData(user) // Save/update user info in Firestore
                                    }
                                }
                            }
                        )
                    } else {
                        // If user is logged in, build our navigation
                        val navController = rememberNavController()

                        NavHost(
                            navController = navController,
                            startDestination = "home" // Home screen
                        ) {
                            // Home screen
                            composable("home") {
                                HomeScreen(
                                    onGoToAccount = { navController.navigate("account") },
                                    onGoToSettings = { navController.navigate("settings") }
                                )
                            }
                            // Account screen
                            composable("account") {
                                AccountScreen(
                                    firestore = firestore,
                                    currentUser = currentUserState!!,
                                    onSignOut = {
                                        signOut(navController)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            // Settings screen
                            composable("settings") {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveUserData(user: FirebaseUser) {
        val userId = user.uid
        val userData = mapOf(
            "name" to user.displayName,
            "email" to user.email,
            "lastLogin" to FieldValue.serverTimestamp()
            // profilePictureUrl
        )

        firestore.collection("users")
            .document(userId)
            .set(userData)
            .addOnSuccessListener {
                Log.d("Firestore", "User data successfully written!")
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error writing user data", e)
            }
    }

    private fun signOut(navController: NavHostController) {
        lifecycleScope.launch {
            googleAuthUiService.signOut()
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                // Permission already granted
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // If we want to explain why notifications are needed
            } else {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notification permission was not granted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel1 = NotificationChannel(
                "important_channel",
                "Important Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "For the most important notifications."
            }
            val channel2 = NotificationChannel(
                "normal_channel",
                "Normal Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "For ordinary notifications."
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel1)
            notificationManager.createNotificationChannel(channel2)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(onSignIn: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = onSignIn) {
                Text(text = "Sign in")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGoToAccount: () -> Unit,
    onGoToSettings: () -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Home Screen") },
                actions = {
                    IconButton(onClick = onGoToAccount) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account"
                        )
                    }
                    IconButton(onClick = onGoToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // ~1/3 height
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { /* TODO: VPN logic */ }) {
                    Text("Connect")
                }
            }

            // Dropdown list of countries
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CountryDropdown()
            }

            // Fill the remaining space
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun CountryDropdown() {
    val countries = listOf("USA", "Canada", "Germany", "France", "Japan", "South Ossetia")
    var expanded by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(countries.first()) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { expanded = !expanded }) {
            Text(text = "Country: $selectedCountry")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            countries.forEach { country ->
                DropdownMenuItem(
                    text = { Text(country) },
                    onClick = {
                        selectedCountry = country
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = "VPN Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Clickable sections(potom eshe dobavlyu)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        // TODO: Key History logic
                    }
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Key History")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        // TODO: Traffic Info logic
                    }
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Traffic Info")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    firestore: FirebaseFirestore,
    currentUser: FirebaseUser,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val userId = currentUser.uid
    var userData by remember { mutableStateOf<UserData?>(null) }

    // Load user data from Firestore
    LaunchedEffect(userId) {
        val docRef = firestore.collection("users").document(userId)
        docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("Firestore", "Error reading document", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                userData = snapshot.toObject(UserData::class.java)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (userData == null) {
                CircularProgressIndicator()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Name: ${userData!!.name}")
                    Text(text = "Email: ${userData!!.email}")

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onSignOut) {
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}