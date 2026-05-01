package com.aitunes.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aitunes.app.domain.model.AiAssistant
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
import com.aitunes.app.ui.theme.OnSurfaceVariant
import com.aitunes.app.ui.theme.SurfaceCard
import com.aitunes.app.ui.theme.SurfaceCardElevated
import com.aitunes.app.ui.theme.Teal400
import com.aitunes.app.ui.theme.Teal700
import com.aitunes.app.ui.theme.TealAccent
import kotlinx.coroutines.delay

private val assistants = listOf(
    AiAssistant(
        id = "health",
        name = "Salud",
        description = "Consulta s\u00EDntomas, bienestar y h\u00E1bitos saludables con IA m\u00E9dica.",
        icon = "\u2764\uFE0F",
        accentColor = HealthGreen,
        gradientColors = listOf(HealthGreenDark, HealthGreen, HealthGreenLight),
        modelLabel = "GPT-4o \u00B7 Medical"
    ),
    AiAssistant(
        id = "legal",
        name = "Legal",
        description = "Orientaci\u00F3n jur\u00EDdica, contratos y consultas legales generales.",
        icon = "\u2696\uFE0F",
        accentColor = LegalBlue,
        gradientColors = listOf(LegalBlueDark, LegalBlue, LegalBlueLight),
        modelLabel = "Claude 3.5 \u00B7 Legal"
    ),
    AiAssistant(
        id = "creative",
        name = "Creativo",
        description = "Genera ideas, textos, guiones y contenido creativo original.",
        icon = "\uD83C\uDFA8",
        accentColor = CreativePurple,
        gradientColors = listOf(CreativePurpleDark, CreativePurple, CreativePurpleLight),
        modelLabel = "Gemini Pro \u00B7 Creative"
    ),
    AiAssistant(
        id = "finance",
        name = "Finanzas",
        description = "An\u00E1lisis financiero, inversiones y planificaci\u00F3n econ\u00F3mica.",
        icon = "\uD83D\uDCB0",
        accentColor = FinanceAmber,
        gradientColors = listOf(FinanceAmberDark, FinanceAmber, FinanceAmberLight),
        modelLabel = "Mistral \u00B7 Finance"
    )
)

@Composable
fun HomeScreen(onAssistantClick: (AiAssistant) -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderSection() }
            itemsIndexed(assistants) { index, assistant ->
                AssistantCard(
                    assistant = assistant,
                    index = index,
                    onClick = { onAssistantClick(assistant) }
                )
            }
            item { Spacer(modifier = Modifier.height(120.dp)) }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        BottomNavBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 8.dp)
    ) {
        Text(
            text = "Tus asistentes",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Modelos de IA on-device. Sin conexi\u00F3n, sin l\u00EDmites.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = OnSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun HeaderIconButton(
    symbol: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SurfaceCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 14.sp,
            color = color
        )
    }
}

@Composable
private fun AssistantCard(
    assistant: AiAssistant,
    index: Int,
    onClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 80L)
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 400),
        label = "cardScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "cardAlpha"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "pressScale"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .scale(scale * pressScale)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SurfaceCard,
                        SurfaceCard.copy(alpha = 0.95f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                Modifier.background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            assistant.accentColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = assistant.gradientColors.map { it.copy(alpha = 0.25f) }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = assistant.icon, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = assistant.modelLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = assistant.accentColor.copy(alpha = 0.8f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(assistant.accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = assistant.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = assistant.description,
                fontSize = 14.sp,
                color = OnSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = assistant.gradientColors
                            )
                        )
                )
            }
        }
    }
}

private data class NavTab(
    val icon: ImageVector,
    val label: String,
    val activeColor: Color
)

@Composable
private fun BottomNavBar(modifier: Modifier = Modifier) {
    var selected by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        NavTab(Icons.Rounded.Home, "Home", Teal400),
        NavTab(Icons.Rounded.CloudDownload, "Models", LegalBlue),
        NavTab(Icons.AutoMirrored.Rounded.Chat, "Chats", CreativePurple),
        NavTab(Icons.Rounded.Settings, "Config", FinanceAmber)
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp)
    ) {
        val slotWidth = maxWidth / 5
        val barWidth = 24.dp
        val pillSlot = if (selected >= 2) selected + 1 else selected
        val barOffsetX by animateDpAsState(
            targetValue = slotWidth * pillSlot + (slotWidth - barWidth) / 2,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "barSlide"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = RoundedCornerShape(34.dp)
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    if (index == 2) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) { FabButton() }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        NavBarItem(
                            icon = tab.icon,
                            label = tab.label,
                            isSelected = selected == index,
                            onClick = { selected = index }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = barOffsetX)
                    .width(barWidth)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(TealAccent, Teal400)
                        )
                    )
            )
        }
    }
}

@Composable
private fun FabButton() {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(colors = listOf(Teal700, TealAccent))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "New",
            tint = Color.Black,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun NavBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.5f,
        animationSpec = tween(200),
        label = "navAlpha"
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = iconAlpha),
            modifier = Modifier
                .size(22.dp)
                .scale(iconScale)
        )
    }
}
