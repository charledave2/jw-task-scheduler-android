package com.quarry.congregationscheduler

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Fingerprint
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@Composable
internal fun AppSecurityShell(lock: AppLockController) {
    val lifecycleOwner = LocalContext.current as androidx.lifecycle.LifecycleOwner
    val scope = rememberCoroutineScope()
    DisposableEffect(lock, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) scope.launch {
                // Let the opaque lock window attach before showing the legacy fingerprint dialog.
                withFrameNanos { }
                withFrameNanos { }
                lock.onForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val preferences = LocalContext.current.getSharedPreferences("scheduler_preferences", android.content.Context.MODE_PRIVATE)
    val dark = preferences.getBoolean("dark_appearance", false)
    val colors = if(dark) darkColorScheme(primary=Color(0xFFA9C6A8),onPrimary=Color(0xFF172318),surface=Color(0xFF20231E),background=Color(0xFF151713),onSurface=Color(0xFFE8E9E1),onSurfaceVariant=Color(0xFFB9BDB2))
        else lightColorScheme(primary=Color(0xFF5C745F),onPrimary=Color.White,surface=Color(0xFFF6F6F3),background=Color(0xFFF6F6F3),onSurface=Color(0xFF252721),onSurfaceVariant=Color(0xFF686D64))
    Box(Modifier.fillMaxSize()) {
        // Retain photo pickers, navigation and local editing state behind the opaque lock window.
        // On a locked cold launch no app data is initialized until authentication succeeds.
        if (lock.hasOpenedSession) {
            Box(Modifier.fillMaxSize().graphicsLayer(alpha=if(lock.locked)0f else 1f).then(if(lock.locked)Modifier.clearAndSetSemantics{} else Modifier)) {
                SchedulerApp(lock)
            }
        }
        if(lock.locked) MaterialTheme(colorScheme=colors) {
            Dialog(onDismissRequest=lock::closeApp,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,securePolicy=SecureFlagPolicy.SecureOn,decorFitsSystemWindows=false)) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Image(painterResource(R.drawable.csp_logo),contentDescription=null,modifier=Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)))
                        Spacer(Modifier.height(24.dp))
                        Text("App locked",fontSize=24.sp,fontWeight=FontWeight.Bold)
                        Text("Unlock to view your workspace.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=20.dp))
                        lock.message?.let { Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=16.dp)) }
                        Button(onClick=lock::unlock,enabled=!lock.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                            Icon(Icons.TwoTone.Fingerprint,null,Modifier.size(22.dp));Spacer(Modifier.width(8.dp));Text(if(lock.busy)"Authenticating…" else "Unlock")
                        }
                        TextButton(onClick=lock::openSecuritySettings,enabled=!lock.busy,modifier=Modifier.padding(top=8.dp)) {
                            Icon(Icons.TwoTone.Settings,null,Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text("Phone security settings")
                        }
                    }
                }
            }
        }
    }
}
