package com.example.whitedobby


import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.FieldValue
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.example.whitedobby.ui.theme.WhiteDobbyVPNTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseUser
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlin.math.sign
import com.google.firebase.messaging.FirebaseMessaging
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat.getSystemService

class MainActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
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

        // Настройка Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        createNotificationChannels()
        askNotificationPermission()

        setContent {
            WhiteDobbyVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    //  состояние для текущего пользователя
                    var currentUserState by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
                    // отслеживание изменений аутентификации
                    DisposableEffect(Unit) {
                        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            currentUserState = firebaseAuth.currentUser
                        }
                        auth.addAuthStateListener(authStateListener)
                        onDispose {
                            auth.removeAuthStateListener(authStateListener)
                        }
                    }
                    if(currentUserState!=null) {//если логин удался
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = "main")
                        {
                            composable("main") {
                                val userId = currentUserState!!.uid
                                UserScreen(
                                    firestore = firestore, // Передаём firestore
                                    userId = userId,
                                    onSignOut = { signOut(navController) },
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onPickImage = { pickImageFromGallery() }
                                )
                            }
                            composable("settings") {
                                SettingsScreen (
                                onBack = { navController.popBackStack() })
                            }
                        }

                    } else {
                        SignInScreen(onSignIn = { signIn() })
                    }
                }
            }
        }
    }

    // Функция для сохранения данных пользователя в Firestore
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
        // только для API  >= 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
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
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) { // Разрешение предоставлено
        } else {
            Toast.makeText(this,
                "Permission isn't granted, so you can't see notifications", Toast.LENGTH_SHORT)
                .show()
            // TODO: Inform user that that your app will not show notifications.
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Канал 1: Важные уведомления
            val channel1 = NotificationChannel(
                "important_channel",
                "Важные Уведомления",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "для важнейших уведомлений."
            }

            // Канал 2: Обычные уведомления
            val channel2 = NotificationChannel(
                "normal_channel",
                "Обычные Уведомления",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "для обычных уведомлений."
            }

            // Регистрация каналов в системе
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel1)
            notificationManager.createNotificationChannel(channel2)
        }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            // Обработка ошибок
            e.printStackTrace()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Вход выполнен успешно -> сохраняем данные пользователя в Firestore после успешного входа
                    auth.currentUser?.let { user ->
                        saveUserData(user)
                    }
                } else {
                    task.exception?.printStackTrace()
                }
            }
    }

    //загружает файл в Cloud Storage
    private fun uploadFile(uri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val storageRef = storage.reference
        val user = auth.currentUser ?: return
        val fileRef = storageRef.child("profile_pictures/${user.uid}")

        val uploadTask = fileRef.putFile(uri)

        uploadTask.addOnSuccessListener {
            // Получение URL загруженного файла
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

    // функции для выбора изображения
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uploadFile(it, onSuccess = { fileUrl ->
                Log.d("CloudStorage", "File uploaded successfully: $fileUrl")
                // Обновите URL профиля пользователя в Firestore
                updateUserProfilePicture(fileUrl)
            }, onFailure = { exception ->
                Log.w("CloudStorage", "File upload failed", exception)
            })
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

    private fun signOut(navController: NavHostController) {
        // Выход из Firebase Auth
        auth.signOut()

        // Выход из Google Sign-In
        googleSignInClient.signOut().addOnCompleteListener(this) {
        }
    }
}

data class User(
    val name: String? = null,
    val email: String? = null,
    val profilePictureUrl: String? = null
)


@Composable
fun UserScreen(
    firestore: FirebaseFirestore,
    userId: String,
    onSignOut: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPickImage: () -> Unit
) {
    val userState = remember { mutableStateOf<User?>(null) }

    DisposableEffect(userId) {
        val listenerRegistration = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("Firestore", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    userState.value = user
                } else {
                    Log.d("Firestore", "Current data: null")
                }
            }

        onDispose {
            listenerRegistration.remove()
        }
    }

    userState.value?.let { user ->
        MainScreen(
            user = user,
            onSignOut = onSignOut,
            onNavigateToSettings = onNavigateToSettings,
            onPickImage = onPickImage
        )
    } ?: run {
        // Показать индикатор загрузки
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
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
            Text(text = "Войти через Google")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    user: User,
    onSignOut: () -> Unit,
    onNavigateToSettings:() -> Unit,
    onPickImage: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Основной экран") },
                actions = {
                    // Кнопка настроек
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
            if (user.profilePictureUrl != null) {
                // Отображение аватара пользователя
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
fun SettingsScreen(onBack: () -> Unit) {
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
            // элементы настроек
            Text(text = "TODO()")
        }
    }
}