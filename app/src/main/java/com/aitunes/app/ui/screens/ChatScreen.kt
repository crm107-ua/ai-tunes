package com.aitunes.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aitunes.app.domain.model.AiAssistant
import com.aitunes.app.domain.model.ChatMessage
import com.aitunes.app.domain.model.MessageSender
import com.aitunes.app.domain.model.SectorId
import com.aitunes.app.ui.chat.ChatViewModel
import com.aitunes.app.ui.chat.IntelligencePhase
import com.aitunes.app.ui.theme.OnSurfaceVariant
import com.aitunes.app.ui.theme.TealAccent
import com.aitunes.app.ui.theme.PureBlack
import com.aitunes.app.ui.theme.SurfaceCard
import com.aitunes.app.ui.theme.SurfaceCardElevated

@Composable
fun ChatScreen(
    assistant: AiAssistant,
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onOpenModelLibrary: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val needsModel by viewModel.needsActiveModel.collectAsState()
    val sectorRequired by viewModel.sectorModelRequired.collectAsState()
    val ragMs by viewModel.ragDurationMs.collectAsState()
    val genMs by viewModel.genDurationMs.collectAsState()
    val intelPhase by viewModel.intelligencePhase.collectAsState()
    val modelLabel by viewModel.intelligenceModelLabel.collectAsState()
    val longTermBrain by viewModel.longTermMemoryAccess.collectAsState()
    val nativeEngineDiag by viewModel.nativeEngineDiagnostics.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Mensaje de bienvenida al instante; GGUF solo tras ~750 ms para que la UI reciba toques primero.
    LaunchedEffect(assistant.id) {
        viewModel.seedWelcomeIfEmpty(assistant)
        viewModel.refreshModelRequirement(initialDelayMs = 750L)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var skipResumeRefresh by remember(assistant.id) { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner, viewModel, assistant.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipResumeRefresh) {
                    skipResumeRefresh = false
                } else {
                    viewModel.refreshModelRequirement()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .imePadding()
    ) {
        IntelligenceStatusBar(
            sector = viewModel.sector,
            modelLabel = modelLabel,
            phase = intelPhase,
            ragMs = ragMs,
            genMs = genMs,
            longTermBrain = longTermBrain,
            accent = assistant.accentColor,
            engineDiagnostic = nativeEngineDiag
        )

        ChatHeader(
            assistant = assistant,
            onBackClick = onBackClick,
            onOpenModelLibrary = onOpenModelLibrary
        )

        AnimatedVisibility(visible = needsModel || sectorRequired != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TealAccent.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (val req = sectorRequired) {
                        null -> "Activa un modelo GGUF en la biblioteca para generar respuestas locales."
                        else ->
                            "Modelo requerido para ${viewModel.sector.displayLabel}: ${req.displayName} " +
                                "(${req.quantLabel}). Descárgalo en la biblioteca."
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenModelLibrary) {
                    Text("Biblioteca", color = TealAccent)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        ChatInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSendClick = {
                if (inputText.isNotBlank()) {
                    val toSend = inputText
                    inputText = ""
                    viewModel.sendUserMessage(
                        text = toSend,
                        accentColor = assistant.accentColor,
                        onNeedModelLibrary = onOpenModelLibrary
                    )
                }
            },
            onImageClick = { },
            onMicClick = { isRecording = !isRecording },
            isRecording = isRecording,
            assistantColor = assistant.accentColor
        )
    }
}

@Composable
private fun IntelligenceStatusBar(
    sector: SectorId,
    modelLabel: String,
    phase: IntelligencePhase,
    ragMs: Long?,
    genMs: Long?,
    longTermBrain: Boolean,
    accent: Color,
    engineDiagnostic: String
) {
    val statusLine = when (phase) {
        IntelligencePhase.Idle -> "Listo · ${sector.displayLabel}"
        IntelligencePhase.SearchingSectorMemories ->
            "Buscando en recuerdos de ${sector.displayLabel}..."
        IntelligencePhase.Generating -> "Generando respuesta..."
        IntelligencePhase.IndexingSemantic -> "Indexando hecho en capa semántica..."
    }
    val brainTransition = rememberInfiniteTransition(label = "brainPulse")
    val brainAlpha by brainTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brainAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Psychology,
            contentDescription = null,
            tint = accent.copy(alpha = if (longTermBrain) brainAlpha else 0.45f),
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelLabel.ifBlank { "Modelo sectorial" },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusLine,
                fontSize = 11.sp,
                color = OnSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val lat = buildString {
                ragMs?.let { append("Memoria: ${it} ms") }
                if (ragMs != null && genMs != null) append(" · ")
                genMs?.let { append("Gen: ${it} ms") }
            }
            if (lat.isNotBlank()) {
                Text(
                    text = lat,
                    fontSize = 10.sp,
                    color = accent.copy(alpha = 0.75f)
                )
            }
            if (engineDiagnostic.isNotBlank()) {
                Text(
                    text = engineDiagnostic,
                    fontSize = 10.sp,
                    color = Color(0xFFFFB74D),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(
    assistant: AiAssistant,
    onBackClick: () -> Unit,
    onOpenModelLibrary: () -> Unit
) {
    val indexedSize = when (assistant.id) {
        "health"   -> "3.2 GB"
        "legal"    -> "856 MB"
        "creative" -> "2.1 GB"
        "finance"  -> "1.8 GB"
        else       -> "—"
    }
    val indexedDocs = when (assistant.id) {
        "health"   -> "1,247 docs"
        "legal"    -> "634 docs"
        "creative" -> "891 docs"
        "finance"  -> "712 docs"
        else       -> "—"
    }

        Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = assistant.gradientColors.map { it.copy(alpha = 0.28f) }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = assistant.icon, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assistant.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    lineHeight = 17.sp
                )
                Text(
                    text = assistant.modelLabel,
                    fontSize = 11.sp,
                    color = assistant.accentColor.copy(alpha = 0.7f),
                    lineHeight = 13.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(assistant.accentColor.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = assistant.accentColor,
                    modifier = Modifier.size(12.dp)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = indexedSize,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        lineHeight = 12.sp
                    )
                    Text(
                        text = indexedDocs,
                        fontSize = 9.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.6f),
                        lineHeight = 10.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenModelLibrary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Biblioteca de modelos",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(
                            colors = listOf(
                                message.accentColor.copy(alpha = 0.9f),
                                message.accentColor.copy(alpha = 0.7f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                SurfaceCardElevated,
                                SurfaceCard
                            )
                        )
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {
            Text(
                text = when {
                    message.isLoading && message.content.isBlank() -> "Generando…"
                    else -> message.content
                },
                fontSize = 15.sp,
                color = if (isUser) Color.White else Color.White.copy(alpha = 0.9f),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onImageClick: () -> Unit,
    onMicClick: () -> Unit,
    isRecording: Boolean,
    assistantColor: Color
) {
    val hasText = value.isNotBlank()
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 4.dp)
    ) {
        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Text(
                        text = "Grabando audio...",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onImageClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = "Image",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp),
                placeholder = {
                    Text(
                        text = "Escribe un mensaje...",
                        fontSize = 15.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = Color.White,
                    lineHeight = 20.sp
                ),
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    disabledContainerColor = SurfaceCard,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = TealAccent
                )
            )

            if (hasText) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(assistantColor, assistantColor.copy(alpha = 0.8f))
                            )
                        )
                        .clickable { onSendClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "Mic",
                        tint = if (isRecording) Color.Red else OnSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scale)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

