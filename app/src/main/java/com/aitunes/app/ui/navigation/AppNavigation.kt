package com.aitunes.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aitunes.app.AiTunesApplication
import com.aitunes.app.domain.model.AiAssistant
import com.aitunes.app.domain.model.SectorId
import com.aitunes.app.ui.chat.ChatViewModel
import com.aitunes.app.ui.chat.ChatViewModelFactory
import com.aitunes.app.ui.models.ModelLibraryScreen
import com.aitunes.app.ui.models.ModelLibraryViewModel
import com.aitunes.app.ui.models.ModelLibraryViewModelFactory
import com.aitunes.app.ui.screens.ChatScreen
import com.aitunes.app.ui.screens.HomeScreen
import com.aitunes.app.ui.theme.CreativePurple
import com.aitunes.app.ui.theme.CreativePurpleDark
import com.aitunes.app.ui.theme.CreativePurpleLight
import com.aitunes.app.ui.theme.FinanceAmber
import com.aitunes.app.ui.theme.FinanceAmberDark
import com.aitunes.app.ui.theme.FinanceAmberLight
import com.aitunes.app.ui.theme.HealthGreen
import com.aitunes.app.ui.theme.HealthGreenDark
import com.aitunes.app.ui.theme.HealthGreenLight
import com.aitunes.app.ui.theme.LegalBlue
import com.aitunes.app.ui.theme.LegalBlueDark
import com.aitunes.app.ui.theme.LegalBlueLight

private val assistants = mapOf(
    "health" to AiAssistant(
        id = "health",
        name = "Salud",
        description = "Consulta síntomas, bienestar y hábitos saludables con IA médica.",
        icon = "\u2764\uFE0F",
        accentColor = HealthGreen,
        gradientColors = listOf(HealthGreenDark, HealthGreen, HealthGreenLight),
        modelLabel = "GPT-4o · Medical"
    ),
    "legal" to AiAssistant(
        id = "legal",
        name = "Legal",
        description = "Orientación jurídica, contratos y consultas legales generales.",
        icon = "\u2696\uFE0F",
        accentColor = LegalBlue,
        gradientColors = listOf(LegalBlueDark, LegalBlue, LegalBlueLight),
        modelLabel = "Claude 3.5 · Legal"
    ),
    "creative" to AiAssistant(
        id = "creative",
        name = "Creativo",
        description = "Genera ideas, textos, guiones y contenido creativo original.",
        icon = "\uD83C\uDFA8",
        accentColor = CreativePurple,
        gradientColors = listOf(CreativePurpleDark, CreativePurple, CreativePurpleLight),
        modelLabel = "Gemini Pro · Creative"
    ),
    "finance" to AiAssistant(
        id = "finance",
        name = "Finanzas",
        description = "Análisis financiero, inversiones y planificación económica.",
        icon = "\uD83D\uDCB0",
        accentColor = FinanceAmber,
        gradientColors = listOf(FinanceAmberDark, FinanceAmber, FinanceAmberLight),
        modelLabel = "Mistral · Finance"
    )
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as AiTunesApplication
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onAssistantClick = { assistant ->
                    navController.navigate("chat/${assistant.id}")
                },
                onOpenModelLibrary = { navController.navigate("models") }
            )
        }
        composable("models") {
            val modelVm = viewModel<ModelLibraryViewModel>(
                factory = ModelLibraryViewModelFactory(app)
            )
            ModelLibraryScreen(
                viewModel = modelVm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "chat/{assistantId}",
            arguments = listOf(navArgument("assistantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assistantId = backStackEntry.arguments?.getString("assistantId") ?: "health"
            val assistant = assistants[assistantId] ?: assistants["health"]!!
            val sector = SectorId.fromAssistantId(assistantId)
            val chatVm = viewModel<ChatViewModel>(
                key = assistantId,
                factory = ChatViewModelFactory(app, sector)
            )
            ChatScreen(
                assistant = assistant,
                viewModel = chatVm,
                onBackClick = { navController.popBackStack() },
                onOpenModelLibrary = {
                    chatVm.refreshModelRequirement()
                    navController.navigate("models")
                }
            )
        }
    }
}
