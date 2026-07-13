package com.streamflixvip.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.ui.auth.AuthViewModel
import com.streamflixvip.app.ui.vip.VipSection
import com.streamflixvip.app.ui.vip.VipViewModel

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    userId: String?,
    userEmail: String?,
    onSignedOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(userEmail ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        if (userId != null) {
            val vipViewModel: VipViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return VipViewModel(userId) as T
                    }
                },
            )
            VipSection(viewModel = vipViewModel)
            Spacer(Modifier.height(24.dp))
        }

        Button(onClick = {
            authViewModel.signOut()
            onSignedOut()
        }) {
            Text("Sair")
        }
    }
}
