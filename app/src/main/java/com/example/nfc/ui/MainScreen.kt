package com.example.nfc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nfc.data.BlockCategory
import com.example.nfc.data.FieldEncoding
import com.example.nfc.data.IcodeSlixDataFormatter
import com.example.nfc.data.MemoryBlock
import com.example.nfc.data.SampleCardReverseEngineer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chainway C5 · RFID HF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ISO 15693 · NXP ICODE SLIX (112B)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    ConnectionBadge(
                        connectionState = state.connectionState,
                        onReconnect = { viewModel.initHardware() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Thanh 2 Tab điều hướng chính
            PrimaryTabRow(
                selectedTabIndex = if (state.activeTab in 0..1) state.activeTab else 0,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = { Text("1. Đọc - Ghi Thẻ NFC", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    icon = { Icon(Icons.Default.Nfc, contentDescription = null) }
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    text = { Text("2. Reverse Engineer", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    icon = { Icon(Icons.Default.Science, contentDescription = null) }
                )
            }

            // Nội dung theo 2 Tab
            when (state.activeTab) {
                0 -> ReadWriteTab(viewModel = viewModel, state = state)
                else -> ReverseEngineerTab(viewModel = viewModel, state = state)
            }
        }
    }
}

@Composable
fun ConnectionBadge(
    connectionState: HardwareConnectionState,
    onReconnect: () -> Unit
) {
    val (color, text) = when (connectionState) {
        HardwareConnectionState.CONNECTED -> Color(0xFF2E7D32) to "C5 Sẵn sàng"
        HardwareConnectionState.SIMULATED -> Color(0xFFE65100) to "Giả lập"
        HardwareConnectionState.CONNECTING -> Color(0xFF1565C0) to "Đang kết nối..."
        HardwareConnectionState.DISCONNECTED -> Color(0xFF757575) to "Ngắt kết nối"
        HardwareConnectionState.ERROR -> Color(0xFFC62828) to "Lỗi"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (connectionState == HardwareConnectionState.DISCONNECTED || connectionState == HardwareConnectionState.ERROR) {
                IconButton(onClick = onReconnect, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Thử lại",
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReadWriteTab(viewModel: MainViewModel, state: MainUiState) {
    var showBlocksDetail by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Thẻ hiển thị UID
        item {
            TagInfoCard(
                tag = state.tag,
                isOperating = state.isOperating,
                onScan = { viewModel.scanInventory() }
            )
        }

        // 2. Form dữ liệu y tế / Payload (có nút lớn: ⚡ Điền mẫu Roche)
        item {
            MedicalDataFormCard(viewModel = viewModel, state = state)
        }

        // 3. Các nút thao tác đọc/ghi
        item {
            ActionControlsCard(viewModel = viewModel, state = state)
        }

        // 4. Thanh trạng thái hoạt động
        item {
            StatusMessageBanner(message = state.statusMessage, isOperating = state.isOperating)
        }

        // 5. Thẻ hiển thị nội dung đọc được từ thẻ & Xem chi tiết 28 Blocks nếu cần
        if (state.tag != null && state.tag.blocks.isNotEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nội dung trong thẻ:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            TextButton(onClick = { showBlocksDetail = !showBlocksDetail }) {
                                Text(
                                    if (showBlocksDetail) "Thu gọn Blocks" else "Xem 28 Blocks (${state.tag.blocks.size})",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (state.tag.decodedText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.tag.decodedText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        if (showBlocksDetail) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.tag.blocks.forEach { block ->
                                    MemoryBlockRow(block = block)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagInfoCard(
    tag: com.example.nfc.data.IcodeSlixTag?,
    isOperating: Boolean,
    onScan: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thẻ RFID ISO 15693",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onScan,
                    enabled = !isOperating,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Quét UID")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (tag != null) {
                Text(
                    text = "Mã định danh (UID 8-Byte):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = tag.prettyUid.ifEmpty { "Chưa phát hiện thẻ" },
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tag.isSlixValid) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Chip NXP ICODE SLIX chính hãng (E0:04:01...)",
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "Loại chip: ISO 15693 (Không thuộc dải ICODE SLIX chuẩn)",
                            color = Color(0xFFE65100),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Huy hiệu trạng thái dữ liệu trên thẻ
                val (statusBg, statusFg, statusTitle) = when (tag.dataStatus) {
                    IcodeSlixDataFormatter.TagDataStatus.VALID_MEDICAL_DATA -> Triple(
                        Color(0xFFE8F5E9), Color(0xFF2E7D32), "✔ ĐÃ CÓ DỮ LIỆU Y TẾ (REF: ${tag.medicalData?.materialRef ?: "OK"})"
                    )
                    IcodeSlixDataFormatter.TagDataStatus.EMPTY_TAG -> Triple(
                        Color(0xFFEDE7F6), Color(0xFF512DA8), "⚪ THẺ TRẮNG (Chưa có dữ liệu / Bộ nhớ trống)"
                    )
                    IcodeSlixDataFormatter.TagDataStatus.CUSTOM_PAYLOAD -> Triple(
                        Color(0xFFFFF3E0), Color(0xFFE65100), "✏ DỮ LIỆU CHUỖI TỰ DO (${tag.decodedText.length} bytes)"
                    )
                    IcodeSlixDataFormatter.TagDataStatus.UNKNOWN_DATA -> Triple(
                        Color(0xFFFFEBEE), Color(0xFFC62828), "⚠ DỮ LIỆU LẠ / KHÔNG XÁC ĐỊNH"
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusTitle,
                        color = statusFg,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Text(
                    text = "Chưa phát hiện thẻ. Đưa thẻ lại gần ăng-ten C5 và nhấn \"Quét UID\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MedicalDataFormCard(viewModel: MainViewModel, state: MainUiState) {
    val currentPayload = if (state.isCustomMode) {
        state.customText
    } else {
        IcodeSlixDataFormatter.buildMedicalPayload(state.materialRef, state.lot, state.exp)
    }
    val bytesCount = currentPayload.toByteArray(Charsets.US_ASCII).size
    val blocksCount = if (bytesCount == 0) 0 else ((bytesCount + 3) / 4)
    val maxBytes = IcodeSlixDataFormatter.MAX_USER_MEMORY_BYTES
    val progress = (bytesCount.toFloat() / maxBytes).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nút LỚN nổi bật: ⚡ ĐIỀN NGAY MẪU ROCHE
            Button(
                onClick = { viewModel.loadRocheSample() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚡ Điền ngay mẫu Roche (REF: 09015051190, LOT, EXP)",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dữ liệu chuẩn bị ghi:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Lựa chọn chế độ Form vs Custom
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !state.isCustomMode,
                        onClick = { viewModel.setCustomMode(false) },
                        label = { Text("Mẫu Y tế", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = state.isCustomMode,
                        onClick = { viewModel.setCustomMode(true) },
                        label = { Text("Chuỗi tự do", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!state.isCustomMode) {
                // Form 3 trường theo mẫu vật tư y tế
                OutlinedTextField(
                    value = state.materialRef,
                    onValueChange = { viewModel.updateMaterialRef(it) },
                    label = { Text("Mã vật tư (Material Ref)") },
                    placeholder = { Text("09015051190") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.lot,
                        onValueChange = { viewModel.updateLot(it) },
                        label = { Text("Số LOT") },
                        placeholder = { Text("93077101") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = state.exp,
                        onValueChange = { viewModel.updateExp(it) },
                        label = { Text("Hạn dùng (EXP)") },
                        placeholder = { Text("2027-02-28") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                OutlinedTextField(
                    value = state.customText,
                    onValueChange = { viewModel.updateCustomText(it) },
                    label = { Text("Chuỗi văn bản (Tối đa 112 Bytes)") },
                    placeholder = { Text("Nhập dữ liệu...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thước đo dung lượng bộ nhớ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dung lượng: $bytesCount / $maxBytes Bytes ($blocksCount / 28 Blocks)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bytesCount > maxBytes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Padding 0x20",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (bytesCount > maxBytes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ActionControlsCard(viewModel: MainViewModel, state: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Hành động (Read / Write / Clear)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nút Đọc thẻ
                Button(
                    onClick = { viewModel.readUserMemory() },
                    enabled = !state.isOperating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Đọc thẻ")
                }

                // Nút Ghi thẻ
                Button(
                    onClick = { viewModel.writeUserMemory() },
                    enabled = !state.isOperating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ghi thẻ")
                }

                // Nút Xóa thẻ
                OutlinedButton(
                    onClick = { viewModel.clearCard() },
                    enabled = !state.isOperating,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun StatusMessageBanner(message: String, isOperating: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOperating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun MemoryBlocksTab(blocks: List<MemoryBlock>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("BẢNG 28 BLOCKS (4 BYTES/BLOCK)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text("112 BYTES EEPROM", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (blocks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa đọc dữ liệu bộ nhớ.\nNhấn nút \"Đọc thẻ\" ở tab trước để tải danh sách block.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(blocks) { block ->
                    MemoryBlockRow(block = block)
                }
            }
        }
    }
}

@Composable
fun MemoryBlockRow(block: MemoryBlock) {
    val isBlank = block.hexValue == "20202020" || block.hexValue == "00000000"

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isBlank) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isBlank) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Block %02d".format(block.blockIndex),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(75.dp)
            )

            Text(
                text = block.hexValue,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "[ ${block.asciiValue} ]",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isBlank) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ConsoleLogsTab(logs: List<String>, onClearLogs: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nhật ký thực thi phần cứng", fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClearLogs) {
                Icon(Icons.Default.Clear, contentDescription = "Xóa logs", modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxSize()
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Chưa có log nào.", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = when {
                                log.contains("Lỗi") || log.contains("thất bại") -> Color(0xFFFF6B6B)
                                log.contains("thành công") || log.contains("Sẵn sàng") -> Color(0xFF69F0AE)
                                else -> Color(0xFFE0E0E0)
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlockReadWriteTab(viewModel: MainViewModel, state: MainUiState) {
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Thẻ thông tin UID
        item {
            TagInfoCard(
                tag = state.tag,
                isOperating = state.isOperating,
                onScan = { viewModel.scanInventory() }
            )
        }

        // 2. Thao tác Single Block (0..27)
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Đọc / Ghi Single Block",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Bộ điều hướng Block index
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.selectBlock(state.selectedBlockIndex - 1) },
                                enabled = state.selectedBlockIndex > 0 && !state.isOperating,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Block trước", modifier = Modifier.size(18.dp))
                            }

                            Text(
                                text = "Block %02d".format(state.selectedBlockIndex),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            IconButton(
                                onClick = { viewModel.selectBlock(state.selectedBlockIndex + 1) },
                                enabled = state.selectedBlockIndex < IcodeSlixDataFormatter.TOTAL_BLOCKS - 1 && !state.isOperating,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Block kế", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Slider chọn block nhanh từ 0 đến 27
                    Slider(
                        value = state.selectedBlockIndex.toFloat(),
                        onValueChange = { viewModel.selectBlock(it.toInt()) },
                        valueRange = 0f..27f,
                        steps = 26,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Ô nhập Hex (8 ký tự Hex = 4 Bytes)
                    OutlinedTextField(
                        value = state.singleBlockHex,
                        onValueChange = { viewModel.updateSingleBlockHex(it) },
                        label = { Text("Dữ liệu Hex Block (8 ký tự = 4 Bytes)") },
                        placeholder = { Text("20202020") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Ô nhập ASCII tương ứng (4 ký tự)
                    OutlinedTextField(
                        value = state.singleBlockAscii,
                        onValueChange = { viewModel.updateSingleBlockAscii(it) },
                        label = { Text("Ký tự ASCII đại diện (Tối đa 4 ký tự)") },
                        placeholder = { Text("TEXT") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Nút thao tác Single Block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.readSingleBlock() },
                            enabled = !state.isOperating,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đọc Block %02d".format(state.selectedBlockIndex))
                        }

                        Button(
                            onClick = { viewModel.writeSingleBlock() },
                            enabled = !state.isOperating,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ghi Block %02d".format(state.selectedBlockIndex))
                        }
                    }
                }
            }
        }

        // 3. Thao tác Toàn bộ thẻ (Raw Hex Dump Tool - 224 ký tự Hex)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Toàn bộ thẻ (Raw Hex Dump)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = { viewModel.readAllToRawHex() },
                            enabled = !state.isOperating,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đọc tất cả ra Hex", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Chuỗi Hex gồm 28 blocks liên tiếp (Tối đa 224 ký tự Hex = 112 Bytes). Thích hợp để clone nguyên bản thẻ mẫu sang thẻ mới.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.rawHexDump,
                        onValueChange = { viewModel.updateRawHexDump(it) },
                        label = { Text("Chuỗi Raw Hex (224 Hex Chars)") },
                        placeholder = { Text("Dán chuỗi Hex 112 Bytes vào đây...") },
                        maxLines = 5,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Thước đo ký tự Hex
                    val hexLength = state.rawHexDump.trim().replace(" ", "").replace("\n", "").length
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Độ dài: $hexLength / 224 ký tự (${hexLength / 2} / 112 Bytes)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hexLength > 224) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Sao chép",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(state.rawHexDump))
                                }
                            )
                            Text(
                                text = "Xóa",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable {
                                    viewModel.updateRawHexDump("")
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Nút Ghi toàn bộ Raw Hex vào thẻ
                    Button(
                        onClick = { viewModel.writeRawHexDumpToTag() },
                        enabled = !state.isOperating && state.rawHexDump.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ghi Raw Hex vào thẻ (Clone/Replicate)")
                    }
                }
            }
        }

        // 4. Trạng thái hoạt động
        item {
            StatusMessageBanner(message = state.statusMessage, isOperating = state.isOperating)
        }
    }
}

@Composable
fun ReverseEngineerTab(viewModel: MainViewModel, state: MainUiState) {
    val report = state.reverseReport

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Banner giới thiệu
        item {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Công cụ Reverse Engineer Thẻ Mẫu",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Dò tìm & đối chiếu mã REF, LOT, EXP từ chuỗi dữ liệu thẻ mẫu theo chuẩn ASCII, BCD, Integer 32-bit và Checksum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Card 1: Chuỗi dữ liệu thô từ thẻ mẫu (Raw Hex Payload)
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Nút LỚN: ⚡ NẠP CHUỖI MẪU ROCHE & PHÂN TÍCH NGAY
                    Button(
                        onClick = { viewModel.loadReversePreset(2) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚡ Nạp chuỗi thẻ mẫu Roche & Phân tích ngay",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "1. Chuỗi dữ liệu gốc từ thẻ mẫu (Hex Dump)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Các nút nạp nhanh Preset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.loadReversePreset(2) },
                            label = { Text("Mẫu BCD", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.loadReversePreset(1) },
                            label = { Text("Mẫu ASCII", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.importCurrentTagToReverse() },
                            label = { Text("Lấy từ thẻ", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.loadReversePreset(3) },
                            label = { Text("Thẻ trắng", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.reverseHexInput,
                        onValueChange = { viewModel.updateReverseHexInput(it) },
                        label = { Text("Chuỗi Hex thẻ mẫu (${state.reverseHexInput.length / 2} Bytes)") },
                        placeholder = { Text("Nhập chuỗi 112 Bytes dạng Hex...") },
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Card 2: Thông số cần dò tìm trên vỏ hộp/nhãn mẫu
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Thông số in trên vỏ hộp cần kiểm tra",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.reverseTargetRef,
                        onValueChange = { viewModel.updateReverseTargetRef(it) },
                        label = { Text("Mã vật tư (REF)") },
                        placeholder = { Text("09015051190") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.reverseTargetLot,
                            onValueChange = { viewModel.updateReverseTargetLot(it) },
                            label = { Text("Số LOT") },
                            placeholder = { Text("93077101") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = state.reverseTargetExp,
                            onValueChange = { viewModel.updateReverseTargetExp(it) },
                            label = { Text("Hạn dùng (EXP)") },
                            placeholder = { Text("2027-02-28") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.runReverseAnalysis() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kiểm tra & Phân tích chuỗi")
                    }
                }
            }
        }

        // Card 3: Kết quả phân tích (Report)
        if (report != null) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "3. Kết quả phân tích chuỗi thẻ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (report.detectedFields.isNotEmpty()) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ) {
                                Text(
                                    text = "${report.detectedFields.size} trường khớp",
                                    color = if (report.detectedFields.isNotEmpty()) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tóm tắt Checksum & UID
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "CRC-16: 0x${report.crc16Iso15693}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "CRC-32: 0x${report.crc32Hex}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = if (report.uidMatchFound) "UID: Có nhúng" else "UID: Không nhúng",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.uidMatchFound) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Danh sách trường tìm thấy
                        if (report.detectedFields.isNotEmpty()) {
                            Text(
                                text = "CÁC TRƯỜNG PHÁT HIỆN ĐƯỢC:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            report.detectedFields.forEach { field ->
                                DetectedFieldItem(field = field)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        } else {
                            Text(
                                text = "Không tìm thấy mẫu chuỗi nào tương ứng với thông số nhãn đã nhập. Vui lòng kiểm tra lại chuỗi Hex thẻ mẫu hoặc giá trị REF/LOT/EXP.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bản đồ bộ nhớ 28 Blocks phân màu trực quan
                        Text(
                            text = "BẢN ĐỒ BỘ NHỚ 28 BLOCKS (COLOR-CODED MAP):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        ReverseMemoryGrid(categories = report.blockCategories)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Nút chuyển chuỗi này sang Tab 1 để ghi thẻ
                        Button(
                            onClick = { viewModel.transferReverseHexToRawWrite() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chuyển sang Tab 1 để ghi thẻ")
                        }
                    }
                }
            }
        }

        // Card 4: Banner trạng thái
        item {
            StatusMessageBanner(message = state.statusMessage, isOperating = state.isOperating)
        }
    }
}

@Composable
fun DetectedFieldItem(field: com.example.nfc.data.DetectedField) {
    val (badgeColor, badgeText) = when (field.encoding) {
        FieldEncoding.ASCII_TEXT -> Color(0xFF1976D2) to "ASCII Ký tự"
        FieldEncoding.BCD_NUMERIC -> Color(0xFF388E3C) to "BCD (Nén số)"
        FieldEncoding.INTEGER_BIG_ENDIAN -> Color(0xFFF57C00) to "Int 32-bit (BE)"
        FieldEncoding.INTEGER_LITTLE_ENDIAN -> Color(0xFFFFA000) to "Int 32-bit (LE)"
        FieldEncoding.DATE_BCD -> Color(0xFF0097A7) to "Date BCD"
        FieldEncoding.CHECKSUM_CRC -> Color(0xFF7B1FA2) to "Checksum CRC"
        FieldEncoding.UNKNOWN -> Color(0xFF757575) to "Unknown"
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = badgeColor.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = field.fieldName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = badgeColor
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Vị trí: Block ${field.startBlock}..${field.endBlock} (Byte ${field.startByte}, dài ${field.lengthBytes}B)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "Hex khớp: [ ${field.matchedHex} ]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = field.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReverseMemoryGrid(categories: List<BlockCategory>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Grid 4 cột x 7 dòng = 28 blocks
        for (row in 0 until 7) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until 4) {
                    val blockIdx = row * 4 + col
                    val category = if (blockIdx < categories.size) categories[blockIdx] else BlockCategory.BLANK_SPACE
                    val (bgColor, fgColor) = when (category) {
                        BlockCategory.MATERIAL_REF -> Color(0xFF1976D2) to Color.White
                        BlockCategory.LOT_NUMBER -> Color(0xFF388E3C) to Color.White
                        BlockCategory.EXPIRATION -> Color(0xFFF57C00) to Color.White
                        BlockCategory.COUNTER_TESTS -> Color(0xFF7B1FA2) to Color.White
                        BlockCategory.CHECKSUM -> Color(0xFFC2185B) to Color.White
                        BlockCategory.CUSTOM_DATA -> Color(0xFFFBC02D) to Color.Black
                        BlockCategory.BLANK_SPACE -> Color(0xFFEEEEEE) to Color(0xFF757575)
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = bgColor,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "B%02d".format(blockIdx),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = fgColor
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Chú thích màu sắc (Legend)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendDot(color = Color(0xFF1976D2), label = "REF")
            LegendDot(color = Color(0xFF388E3C), label = "LOT")
            LegendDot(color = Color(0xFFF57C00), label = "EXP")
            LegendDot(color = Color(0xFF7B1FA2), label = "UID/CRC")
            LegendDot(color = Color(0xFFFBC02D), label = "Dữ liệu")
            LegendDot(color = Color(0xFFBDBDBD), label = "Trống")
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
