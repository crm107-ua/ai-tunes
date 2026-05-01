package com.aitunes.app.ui.models

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aitunes.app.data.models.ModelDownloadProgress
import com.aitunes.app.data.models.RegisteredModel
import com.aitunes.app.ui.theme.OnSurfaceVariant
import com.aitunes.app.ui.theme.PureBlack
import com.aitunes.app.ui.theme.SurfaceCard
import com.aitunes.app.ui.theme.Teal400
import com.aitunes.app.ui.theme.TealAccent

@Composable
fun ModelLibraryScreen(
    viewModel: ModelLibraryViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activeId by viewModel.activeModelId.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableLongStateOf(0L) }
    var showClearStorageDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* La descarga ya está en curso; el permiso solo mejora la notificación del sistema. */ }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick += 1L
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val freeBytes = remember(refreshTick) { availableStorageBytes() }
    val totalHint = remember(refreshTick) { totalStorageBytes() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Atrás",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Biblioteca de modelos",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Tier detectado: ${viewModel.deviceTier.name}",
                    fontSize = 12.sp,
                    color = TealAccent.copy(alpha = 0.85f)
                )
                Text(
                    text = "Prioriza TinyLlama o Llama 3.2 1B en móvil. MobileVLM (visión) no está incluido: este motor solo usa GGUF de texto.",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        StorageBar(freeBytes = freeBytes, totalBytes = totalHint)

        OutlinedButton(
            onClick = { showClearStorageDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.9f))
        ) {
            Text(
                text = "Vaciar almacenamiento de modelos",
                fontSize = 13.sp
            )
        }
        Text(
            text = "Elimina del teléfono todos los GGUF guardados por la app (interno y carpeta de descargas de la app) y cancela descargas activas.",
            fontSize = 11.sp,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (showClearStorageDialog) {
            AlertDialog(
                onDismissRequest = { showClearStorageDialog = false },
                title = {
                    Text(
                        text = "¿Vaciar modelos?",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "Se borrarán los archivos locales y las descargas en curso. Tendrás que volver a descargar un modelo para usar el chat offline.",
                        color = OnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearStorageDialog = false
                            viewModel.clearAllModelStorage {
                                Toast.makeText(
                                    context,
                                    "Almacenamiento de modelos vaciado.",
                                    Toast.LENGTH_LONG
                                ).show()
                                refreshTick += 1L
                            }
                        }
                    ) {
                        Text("Vaciar", color = TealAccent, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearStorageDialog = false }) {
                        Text("Cancelar", color = OnSurfaceVariant)
                    }
                },
                containerColor = SurfaceCard,
                titleContentColor = Color.White,
                textContentColor = OnSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            items(viewModel.models, key = { it.id }) { model ->
                key(model.id, refreshTick, activeId) {
                    ModelRow(
                        model = model,
                        downloaded = viewModel.isDownloaded(model.id),
                        active = model.id == activeId,
                        recommended = viewModel.isRecommended(model),
                        downloadState = downloadProgress[model.id],
                        onDownload = {
                            viewModel.download(model)
                            refreshTick += 1L
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onActivate = { viewModel.activate(model.id) },
                        onDelete = {
                            viewModel.delete(model.id)
                            refreshTick += 1L
                        }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun StorageBar(freeBytes: Long, totalBytes: Long) {
    val usedFraction = if (totalBytes > 0) {
        ((totalBytes - freeBytes).toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    val freeGb = freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Almacenamiento interno",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { usedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Teal400,
            trackColor = Color.White.copy(alpha = 0.08f),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Libre: ${"%.2f".format(freeGb)} GB",
            fontSize = 12.sp,
            color = OnSurfaceVariant
        )
    }
}

@Composable
private fun ModelRow(
    model: RegisteredModel,
    downloaded: Boolean,
    active: Boolean,
    recommended: Boolean,
    downloadState: ModelDownloadProgress?,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    val recBg = if (recommended) {
        Brush.linearGradient(
            listOf(
                TealAccent.copy(alpha = 0.22f),
                Color.Transparent
            )
        )
    } else {
        Brush.linearGradient(listOf(SurfaceCard, SurfaceCard))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(recBg)
            .border(
                width = if (recommended) 1.dp else 1.dp,
                color = if (recommended) TealAccent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${model.quantLabel} · ${formatApproxSize(model.approximateSizeBytes)}",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
                if (recommended) {
                    Text(
                        text = "Recomendado para tu dispositivo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TealAccent,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Text(
                text = if (downloaded) "Descargado" else "No descargado",
                fontSize = 11.sp,
                color = if (downloaded) Teal400 else OnSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = model.description,
            fontSize = 13.sp,
            color = OnSurfaceVariant,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (!downloaded && downloadState != null) {
            val pctText = when {
                downloadState.isIndeterminate -> null
                else -> {
                    val p = (downloadState.fraction * 100f).toInt().coerceIn(0, 100)
                    "$p %"
                }
            }
            val detailText = when {
                downloadState.bytesTotal > 0L && downloadState.bytesDownloaded >= 0L ->
                    "${formatHumanBytes(downloadState.bytesDownloaded)} de ${formatHumanBytes(downloadState.bytesTotal)}"
                downloadState.bytesDownloaded >= 0L ->
                    "${formatHumanBytes(downloadState.bytesDownloaded)} acumulados"
                else -> ""
            }
            val diskVerifyText =
                if (downloadState.bytesOnDisk > 0L) {
                    "Verificado en archivo: ${formatHumanBytes(downloadState.bytesOnDisk)}"
                } else null
            Column(modifier = Modifier.fillMaxWidth()) {
                downloadState.statusHint?.let { hint ->
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TealAccent,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Descarga en curso",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = pctText ?: "Sin % aún",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )
                }
                if (detailText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detailText,
                        fontSize = 11.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
                diskVerifyText?.let { line ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        color = Teal400.copy(alpha = 0.95f),
                        lineHeight = 15.sp
                    )
                }
                if (downloadState.isIndeterminate && downloadState.statusHint == null && detailText.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "El gestor de Android prepara la descarga; si hay red, el porcentaje aparece al avanzar los bytes.",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (downloadState.isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Teal400,
                        trackColor = Color.White.copy(alpha = 0.08f),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { downloadState.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Teal400,
                        trackColor = Color.White.copy(alpha = 0.08f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!downloaded) {
                Button(
                    onClick = onDownload,
                    enabled = downloadState == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal400, contentColor = Color.Black)
                ) { Text(if (downloadState != null) "Descargando…" else "Descargar") }
            } else {
                Button(
                    onClick = onActivate,
                    enabled = !active,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black)
                ) { Text(if (active) "Activo" else "Activar") }
                OutlinedButton(
                    onClick = onDelete,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Eliminar") }
            }
        }
    }
}

private fun formatHumanBytes(bytes: Long): String {
    if (bytes < 0L) return "—"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return "%.2f GB".format(gb)
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    if (mb >= 1.0) return "%.1f MB".format(mb)
    val kb = bytes.toDouble() / 1024.0
    return "%.0f KB".format(kb)
}

private fun formatApproxSize(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return "%.0f MB".format(mb)
}

private fun availableStorageBytes(): Long {
    val path = Environment.getDataDirectory().absolutePath
    return try {
        val stat = StatFs(path)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.availableBlocksLong * stat.blockSizeLong
        } else {
            @Suppress("DEPRECATION")
            stat.availableBlocks.toLong() * stat.blockSize
        }
    } catch (_: Exception) {
        0L
    }
}

private fun totalStorageBytes(): Long {
    val path = Environment.getDataDirectory().absolutePath
    return try {
        val stat = StatFs(path)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.blockCountLong * stat.blockSizeLong
        } else {
            @Suppress("DEPRECATION")
            stat.blockCount.toLong() * stat.blockSize
        }
    } catch (_: Exception) {
        0L
    }
}
