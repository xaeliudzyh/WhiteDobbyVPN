package com.example.whitedobby

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.example.whitedobby.ui.theme.WhiteDobbyVPNTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.auth.User
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch

data class UserData(
    val name: String? = null,
    val email: String? = null,
    val profilePictureUrl: String? = null
)

class MainActivity : ComponentActivity() {

    // Firebase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth
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

        // Инициализация Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Инициализация Firestore
        firestore = FirebaseFirestore.getInstance()

        // Инициализация Cloud Storage
        storage = FirebaseStorage.getInstance()

        // Включение локального кэша Firestore
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings

        createNotificationChannels()
        askNotificationPermission()

        setContent {
            WhiteDobbyVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Отслеживаем текущего FirebaseUser
                    var currentUserState by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
                    DisposableEffect(Unit) {
                        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            currentUserState = firebaseAuth.currentUser
                        }
                        auth.addAuthStateListener(authStateListener)
                        onDispose {
                            auth.removeAuthStateListener(authStateListener)
                        }
                    }

                    // Строим интерфейс
                    if (currentUserState != null) {
                        // Если пользователь уже вошёл
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = "main") {
                            composable("main") {
                                val userId = currentUserState!!.uid
                                UserScreen(
                                    firestore = firestore,
                                    userId = userId,
                                    onSignOut = { signOut(navController) },
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onPickImage = { pickImageFromGallery() }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    } else {
                        // Если пользователь НЕ вошёл, показываем экран авторизации
                        SignInScreen(
                            onSignIn = {
                                // Вместо старого GoogleSignIn - вызываем credentialManager
                                lifecycleScope.launch {
                                    googleAuthUiService.signIn()
                                    // После успешного входа (если он не выкинул Exception):
                                    googleAuthUiService.getSignedInUser()?.let { user ->
                                        saveUserData(user)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Сохраняем/обновляем данные пользователя в Firestore
    private fun saveUserData(user: FirebaseUser) {
        val userId = user.uid
        val userData = mapOf(
            "name" to user.displayName,
            "email" to user.email,
            "lastLogin" to FieldValue.serverTimestamp()
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

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                // разрешение уже предоставлено
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // если захочу как то объяснить пользователю - нахрена ему мои уведомления
            } else {
                // запросить разрешение
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
                "Permission isn't granted, so you can't see notifications",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel1 = NotificationChannel(
                "important_channel",
                "Важные Уведомления",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "для важнейших уведомлений."
            }
            val channel2 = NotificationChannel(
                "normal_channel",
                "Обычные Уведомления",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "для обычных уведомлений."
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel1)
            notificationManager.createNotificationChannel(channel2)
        }
    }

    /**
     * Выход из Firebase + CredentialManager
     */
    private fun signOut(navController: androidx.navigation.NavHostController) {
        lifecycleScope.launch {
            googleAuthUiService.signOut()
            // Когда вышли - сбрасываем роутинг:
            navController.navigate("main") {
                popUpTo("main") { inclusive = true }
            }
        }
    }

    private fun uploadFile(
        uri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val storageRef = storage.reference
        val user = auth.currentUser ?: return
        val fileRef = storageRef.child("profile_pictures/${user.uid}")

        val uploadTask = fileRef.putFile(uri)
        uploadTask.addOnSuccessListener {
            fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val fileUrl = downloadUri.toString()
                onSuccess(fileUrl)
            }.addOnFailureListener { exception ->
                onFailure(exception)
            }
        }.addOnFailureListener { exception ->
            onFailure(exception)
        }
    }

    // Выбор изображения
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uploadFile(it,
                onSuccess = { fileUrl ->
                    Log.d("CloudStorage", "File uploaded successfully: $fileUrl")
                    updateUserProfilePicture(fileUrl)
                },
                onFailure = { exception ->
                    Log.w("CloudStorage", "File upload failed", exception)
                }
            )
        }
    }

    private fun pickImageFromGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun updateUserProfilePicture(fileUrl: String) {
        val user = auth.currentUser ?: return
        val userId = user.uid
        firestore.collection("users")
            .document(userId)
            .update("profilePictureUrl", fileUrl)
            .addOnSuccessListener {
                Log.d("Firestore", "User profile picture updated")
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error updating profile picture", e)
            }
    }
}

@Composable
fun SignInScreen(onSignIn: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Button(onClick = { onSignIn() }) {
            Text(text = "Войти через Credential Manager")
        }
    }
}

@Composable
fun UserScreen(
    firestore: FirebaseFirestore,
    userId: String,
    onSignOut: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPickImage: () -> Unit
) {
    // Храним загруженные данные о пользователе
    val userState = remember { mutableStateOf<UserData?>(null) }

    DisposableEffect(userId) {
        val listenerRegistration = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // Обрабатываем ошибку
                    Log.w("Firestore", "Listen failed.", e)
                    return@addSnapshotListener
                }

                // Документ существует? -> мапим в UserData
                if (snapshot != null && snapshot.exists()) {
                    val userData = snapshot.toObject(UserData::class.java)
                    userState.value = userData
                } else {
                    Log.d("Firestore", "Current user data is null")
                }
            }

        onDispose {
            listenerRegistration.remove()
        }
    }

    // Если данные уже подгрузились, показываем главный экран
    userState.value?.let { userData ->
        MainScreen(
            user = userData,
            onSignOut = onSignOut,
            onNavigateToSettings = onNavigateToSettings,
            onPickImage = onPickImage
        )
    } ?: run {
        // Иначе крутим индикатор загрузки
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    user: UserData,                  // <-- меняем тип на UserData
    onSignOut: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPickImage: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Основной экран") },
                actions = {
                    IconButton(onClick = { onNavigateToSettings() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки"
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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Показываем аватар, если есть
            if (user.profilePictureUrl != null) {
                Image(
                    painter = rememberImagePainter(user.profilePictureUrl),
                    contentDescription = "Аватар",
                    modifier = Modifier.size(100.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Добро пожаловать, ${user.name}!")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Email: ${user.email}")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onSignOut() }) {
                Text(text = "Выйти")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onPickImage() }) {
                Text(text = "Загрузить фото профиля")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        // Содержимое экрана настроек
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = "Настройки VPN", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            // Здесь можно расположить любые элементы настроек
            Text(text = "TODO: Добавить настройки")
        }
    }
}