package com.daftar.kredi.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun canUseBiometric(ctx: Context): Boolean =
    BiometricManager.from(ctx)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

private fun showBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("دفتر الكريدي")
        .setSubtitle("افتح التطبيق بالبصمة")
        .setNegativeButtonText("استخدام الرمز")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(vm: AppViewModel, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val bio by vm.settings.biometricEnabled.collectAsState()
    val activity = remember { ctx.findActivity() }
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    val bioAvailable = bio && activity != null && canUseBiometric(ctx)

    LaunchedEffect(bioAvailable) {
        if (bioAvailable && activity != null) showBiometric(activity, onUnlocked)
    }

    fun onKey(k: String) {
        wrong = false
        if (k == "⌫") {
            pin = pin.dropLast(1)
        } else if (pin.length < 4) {
            val next = pin + k
            if (next.length == 4) {
                if (vm.settings.checkPin(next)) {
                    onUnlocked()
                } else {
                    wrong = true
                    pin = ""
                }
            } else {
                pin = next
            }
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("دفتر الكريدي", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("أدخل الرمز للمتابعة", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(
                        if (i < pin.length) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (wrong) "رمز خاطئ، حاول مرة أخرى" else " ",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { k ->
                            if (k.isEmpty()) {
                                Spacer(Modifier.size(76.dp))
                            } else {
                                Box(
                                    Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onKey(k) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(k, fontSize = 28.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bioAvailable && activity != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { showBiometric(activity, onUnlocked) }) {
                Text("استخدام البصمة")
            }
        }
    }
}
