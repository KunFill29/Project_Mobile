package com.example.project_mobile.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project_mobile.data.FirebaseManager
import com.example.project_mobile.data.UserProfile
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val TAG = "AuthViewModel"

    val isLoggedIn: Boolean get() = auth.currentUser != null
    val currentUserUID: String? get() = auth.currentUser?.uid

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        val user = auth.currentUser
        if (user != null) {
            viewModelScope.launch {
                _authState.value = AuthState.Loading
                val profile = FirebaseManager.getUserProfile(user.uid)
                _userProfile.value = profile
                _authState.value = AuthState.Authenticated(user.uid)
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Success : AuthState()
        object Unauthenticated : AuthState()
        object ResetPasswordSent : AuthState()
        data class Error(val message: String) : AuthState()
        data class Authenticated(val uid: String) : AuthState()
    }

    fun signUp(email: String, password: String, name: String, phone: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank() || phone.isBlank()) {
            _authState.value = AuthState.Error("All fields are required")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            FirebaseManager.signUp(email, password, name, phone)
                .onSuccess { uid ->
                    _userProfile.value = UserProfile(uid, name, email, phone)
                    _authState.value = AuthState.Authenticated(uid)
                }
                .onFailure { e ->
                    _authState.value = AuthState.Error(e.message ?: "Sign up failed")
                }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Email and password are required")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            FirebaseManager.signIn(email, password)
                .onSuccess { uid ->
                    val profile = FirebaseManager.getUserProfile(uid)
                    _userProfile.value = profile
                    _authState.value = AuthState.Authenticated(uid)
                }
                .onFailure { e ->
                    _authState.value = AuthState.Error(e.message ?: "Invalid email or password")
                }
        }
    }

    fun signOut() {
        FirebaseManager.signOut()
        _authState.value = AuthState.Unauthenticated
        _userProfile.value = null
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _authState.value = AuthState.Error("Email cannot be empty")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.ResetPasswordSent
            } catch (e: FirebaseAuthException) {
                val message = when (e.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> "Account not found"
                    "ERROR_INVALID_EMAIL"  -> "Invalid email format"
                    else -> "An error occurred. Please try again."
                }
                _authState.value = AuthState.Error(message)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error resetting password")
            }
        }
    }

    fun updateUserProfile(name: String, phone: String) {
        val current = _userProfile.value ?: return
        viewModelScope.launch {
            val updatedProfile = current.copy(name = name, phone = phone)
            if (FirebaseManager.saveUserProfile(updatedProfile)) {
                _userProfile.value = updatedProfile
            }
        }
    }

    fun loginWithGoogle(context: Context) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credentialManager = CredentialManager.create(context)
                val signInWithGoogleOption = GetSignInWithGoogleOption
                    .Builder("549487191582-068p7hv37n6hlddfl0b665gse1baj0rt.apps.googleusercontent.com")
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val result = credentialManager.getCredential(request = request, context = context)
                val credential = result.credential
                
                if (credential is CustomCredential && 
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    
                    val authResult = auth.signInWithCredential(firebaseCredential).await()
                    val user = authResult.user
                    
                    if (user != null) {
                        var profile = FirebaseManager.getUserProfile(user.uid)
                        if (profile == null) {
                            profile = UserProfile(
                                uid = user.uid,
                                name = user.displayName ?: "Google User",
                                email = user.email ?: "",
                                phone = user.phoneNumber ?: ""
                            )
                            FirebaseManager.saveUserProfile(profile)
                        }
                        _userProfile.value = profile
                        _authState.value = AuthState.Authenticated(user.uid)
                    } else {
                        _authState.value = AuthState.Error("Google sign in failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google sign in error", e)
                _authState.value = AuthState.Error(e.message ?: "Google sign in failed")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
