package com.lexis.reader.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lexis.reader.ui.theme.LexisTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            LexisTheme(settings.theme) {
                Surface(Modifier.fillMaxSize()) { LexisRoot(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Text sent from another app's selection toolbar ("Look up in Lexis") or share sheet. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val text: CharSequence? = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        val word = text?.toString()?.trim()?.take(80)
        if (!word.isNullOrEmpty()) {
            vm.home()
            vm.openWord(word)
        }
    }
}

@Composable
fun LexisRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.consumeMessage()
        snackbar.showSnackbar(m)
    }

    Box(Modifier.fillMaxSize()) {
        when (val screen = vm.current) {
            Screen.Home -> HomeScreen(
                vm = vm,
                onOpenWord = vm::openWord,
                onOpenDictionaries = { vm.navigate(Screen.Dictionaries) },
            )
            is Screen.Entry -> EntryScreen(
                vm = vm,
                word = screen.word,
                onBack = { vm.back() },
                onOpenWord = vm::openWord,
            )
            Screen.Dictionaries -> DictionariesScreen(vm = vm, onBack = { vm.back() })
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}
