package com.paperpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.paperpilot.ui.screens.HomeScreen
import com.paperpilot.ui.screens.PdfDetailScreen
import com.paperpilot.ui.screens.QuizScreen
import com.paperpilot.ui.screens.SettingsScreen
import com.paperpilot.ui.theme.PaperpilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaperpilotTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav()
                }
            }
        }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenQuiz = { pdfId -> nav.navigate("quiz/$pdfId") },
                onOpenPdf = { pdfId -> nav.navigate("detail/$pdfId") }
            )
        }
        composable("detail/{pdfId}") { backStack ->
            val id = backStack.arguments?.getString("pdfId")?.toLongOrNull() ?: 0L
            PdfDetailScreen(pdfId = id, onBack = { nav.popBackStack() }, onStartQuiz = { nav.navigate("quiz/$id") })
        }
        composable("quiz/{pdfId}") { backStack ->
            val id = backStack.arguments?.getString("pdfId")?.toLongOrNull() ?: 0L
            QuizScreen(pdfId = id, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
