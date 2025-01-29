package com.example.whitedobby

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class GoogleAuthUiService(
    private val context: Context,
    private val credentialManager: CredentialManager
) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Авторизация через Credential Manager.
     * Получаем токен GoogleIdToken, передаём в Firebase, чтобы входить в аккаунт.
     */
    suspend fun signIn() {
        val getGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .build()

        val getCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(getGoogleIdOption)
            .build()

        try {
            // Запрашиваем credential через CredentialManager
            val credentialResponse = credentialManager.getCredential(
                request = getCredentialRequest,
                context = context
            )
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialResponse.credential.data)
            val idToken = googleIdTokenCredential.idToken
            // Теперь передаём токен в Firebase
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(firebaseCredential).await()
        } catch (e: Exception) {
            Log.e(GoogleAuthUiService::class.java.simpleName, "Google sign-in failed", e)
            if (e is CancellationException) throw e
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e
        }
    }

    fun getSignedInUser() = auth.currentUser
}