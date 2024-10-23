package com.example.whitedobby

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.example.whitedobby.ui.theme.WhiteDobbyVPNTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Инициализация Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Настройка Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Убедитесь, что этот ID указан в google-services.json
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            WhiteDobbyVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (auth.currentUser != null) {
                        MainScreen(auth.currentUser!!)
                    } else {
                        SignInScreen(onSignIn = { signIn() })
                    }
                }
            }
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
                    // Вход выполнен успешно
                    setContent {
                        WhiteDobbyVPNTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                MainScreen(auth.currentUser!!)
                            }
                        }
                    }
                } else {
                    // Обработка ошибок входа
                    task.exception?.printStackTrace()
                }
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
fun MainScreen(user: com.google.firebase.auth.FirebaseUser) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Основной экран") },
                actions = {
                    // Кнопка настроек
                    IconButton(onClick = { /* Открыть панель настроек */ }) {
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
            if (user.photoUrl != null) {
                // Отображение аватара пользователя
                Image(
                    painter = rememberImagePainter(user.photoUrl),
                    contentDescription = "Аватар",
                    modifier = Modifier.size(100.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Добро пожаловать, ${user.displayName}!")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Email: ${user.email}")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* Реализуйте выход из аккаунта */ }) {
                Text(text = "Выйти")
            }
        }
    }
}
