package com.todoapp.data.remote.google

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.tasks.TasksScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _account = MutableStateFlow<GoogleSignInAccount?>(null)
    val account: StateFlow<GoogleSignInAccount?> = _account.asStateFlow()

    val isSignedIn: Boolean get() = _account.value != null

    val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(TasksScopes.TASKS),
                Scope(CalendarScopes.CALENDAR),
                Scope(GmailScopes.GMAIL_COMPOSE)
            )
            .build()
        GoogleSignIn.getClient(context, options)
    }

    /**
     * Restore the last signed-in account on app start.
     * Call this from Application.onCreate or MainActivity.
     */
    fun restoreSignIn() {
        val last = GoogleSignIn.getLastSignedInAccount(context)
        if (last != null && hasRequiredScopes(last)) {
            _account.value = last
        }
    }

    fun onSignInSuccess(account: GoogleSignInAccount) {
        _account.value = account
    }

    fun signOut() {
        signInClient.signOut()
        _account.value = null
    }

    private fun hasRequiredScopes(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(
            account,
            Scope(TasksScopes.TASKS),
            Scope(CalendarScopes.CALENDAR),
            Scope(GmailScopes.GMAIL_COMPOSE)
        )
    }

    fun getAccount(): GoogleSignInAccount? = _account.value
}
