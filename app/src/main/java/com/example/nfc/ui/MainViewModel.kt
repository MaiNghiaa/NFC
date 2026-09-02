package com.example.nfc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nfc.data.IcodeSlixDataFormatter
import com.example.nfc.data.IcodeSlixTag
import com.example.nfc.data.MedicalMaterialData
import com.example.nfc.data.MemoryBlock
import com.example.nfc.rfid.ChainwayHfManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.nfc.data.SampleCardReverseEngineer
import com.example.nfc.data.ReverseEngineerReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HardwareConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SIMULATED,
    ERROR
}

data class MainUiState(
    val connectionState: HardwareConnectionState = HardwareConnectionState.DISCONNECTED,
    val connectionMessage: String = "Chưa kết nối module HF Chainway",
    val tag: IcodeSlixTag? = null,
    val isOperating: Boolean = false,
    val statusMessage: String = "Sẵn sàng",
    val activeTab: Int = 0,
    // Form vật tư y tế
    val materialRef: String = "09015051190",
    val lot: String = "93077101",
    val exp: String = "2027-02-28",
    // Chế độ nhập tự do
    val isCustomMode: Boolean = false,
    val customText: String = "",
    // Đọc / Ghi Single Block
    val selectedBlockIndex: Int = 0,
    val singleBlockHex: String = "20202020",
    val singleBlockAscii: String = "    ",
    // Đọc / Ghi Raw Hex Dump (Toàn bộ 112 Bytes)
    val rawHexDump: String = "",
    // Phân tích Reverse Engineer từ thẻ mẫu
    val reverseHexInput: String = SampleCardReverseEngineer.Presets.ROCHE_BINARY_BCD_HEX,
    val reverseTargetRef: String = "09015051190",
    val reverseTargetLot: String = "93077101",
    val reverseTargetExp: String = "2027-02-28",
    val reverseReport: ReverseEngineerReport? = null,
    // Nhật ký hoạt động
    val logs: List<String> = emptyList()
)

class MainViewModel(
    private val rfidManager: ChainwayHfManager = ChainwayHfManager.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadRocheSample()
        initHardware()
    }

    private fun addLog(message: String) {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timeStamp] $message"
        _uiState.value = _uiState.value.copy(
            logs = listOf(logEntry) + _uiState.value.logs.take(49)
        )
    }

    fun initHardware() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState = HardwareConnectionState.CONNECTING,
                connectionMessage = "Đang kết nối module RFID HF Chainway..."
            )
            addLog("Đang khởi tạo module HF (ISO 15693)...")

            val result = rfidManager.initHardware()
            if (result.isSuccess) {
                if (rfidManager.isSimulated()) {
                    _uiState.value = _uiState.value.copy(
                        connectionState = HardwareConnectionState.SIMULATED,
                        connectionMessage = "Chế độ Giả lập (Không phát hiện phần cứng C5)"
                    )
                    addLog("Đã bật chế độ giả lập an toàn để thử nghiệm giao diện.")
                } else {
                    _uiState.value = _uiState.value.copy(
                        connectionState = HardwareConnectionState.CONNECTED,
                        connectionMessage = "Đã kết nối module HF Chainway C5"
                    )
                    addLog("Kết nối thành công phần cứng Chainway RFID HF.")
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    connectionState = HardwareConnectionState.ERROR,
                    connectionMessage = "Lỗi khởi tạo: ${result.exceptionOrNull()?.message}"
                )
                addLog("Khởi tạo phần cứng thất bại: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun freeHardware() {
        viewModelScope.launch {
            rfidManager.freeHardware()
            _uiState.value = _uiState.value.copy(
                connectionState = HardwareConnectionState.DISCONNECTED,
                connectionMessage = "Đã ngắt kết nối module HF"
            )
            addLog("Đã tắt nguồn module RFID HF.")
        }
    }

    fun updateMaterialRef(value: String) {
        _uiState.value = _uiState.value.copy(materialRef = value)
    }

    fun updateLot(value: String) {
        _uiState.value = _uiState.value.copy(lot = value)
    }

    fun updateExp(value: String) {
        _uiState.value = _uiState.value.copy(exp = value)
    }

    fun setCustomMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isCustomMode = enabled)
    }

    fun updateCustomText(value: String) {
        _uiState.value = _uiState.value.copy(customText = value)
    }

    fun loadRocheSample() {
        val sample = MedicalMaterialData.SAMPLE_ROCHE
        val payload = sample.rawPayload
        val hexBlocks = IcodeSlixDataFormatter.formatTextToBlocks(payload)
        val memoryBlocks = (0 until IcodeSlixDataFormatter.TOTAL_BLOCKS).map { i ->
            if (i < hexBlocks.size) {
                val hex = hexBlocks[i]
                MemoryBlock(i, hex, IcodeSlixDataFormatter.hexBlockToReadableAscii(hex))
            } else {
                MemoryBlock(i, "20202020", "    ")
            }
        }
        val sampleTag = IcodeSlixTag(
            rawUid = "E0040153238F7F40",
            prettyUid = "E0:04:01:53:23:8F:7F:40",
            isSlixValid = true,
            blocks = memoryBlocks,
            decodedText = payload,
            medicalData = sample
        )
        _uiState.value = _uiState.value.copy(
            tag = sampleTag,
            materialRef = sample.materialRef,
            lot = sample.lot,
            exp = sample.exp,
            customText = sample.rawPayload,
            statusMessage = "Đã nạp sẵn dữ liệu mẫu Roche Syphilis vào 28 blocks"
        )
        addLog("Đã nạp dữ liệu mẫu Roche Syphilis (UID: E0:04:01:53:23:8F:7F:40, 11 blocks).")
    }

    /**
     * Quét thẻ ISO 15693 để lấy UID
     */
    fun scanInventory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, statusMessage = "Đang quét tìm thẻ ISO 15693...")
            addLog("Bắt đầu lệnh INVENTORY...")

            val result = rfidManager.inventory()
            result.onSuccess { rawUid ->
                val pretty = IcodeSlixDataFormatter.formatPrettyUid(rawUid)
                val isSlix = IcodeSlixDataFormatter.isNxpIcodeSlix(rawUid)
                val newTag = (_uiState.value.tag ?: IcodeSlixTag(rawUid)).copy(
                    rawUid = rawUid,
                    prettyUid = pretty,
                    isSlixValid = isSlix
                )
                _uiState.value = _uiState.value.copy(
                    tag = newTag,
                    statusMessage = "Tìm thấy thẻ: $pretty",
                    isOperating = false
                )
                addLog("Quét thành công! UID: $pretty | Chip: ${if (isSlix) "NXP ICODE SLIX (Hợp lệ)" else "Khác / Không xác định"}")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Quét thẻ thất bại: ${error.message}",
                    isOperating = false
                )
                addLog("Quét thẻ thất bại: ${error.message}")
            }
        }
    }

    /**
     * Đọc toàn bộ 28 Blocks User Memory
     */
    fun readUserMemory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, statusMessage = "Đang đọc User Memory (28 Blocks)...")
            addLog("Bắt đầu đọc 28 Blocks User Memory...")

            // Quét UID trước nếu chưa có
            if (_uiState.value.tag == null) {
                val invRes = rfidManager.inventory()
                if (invRes.isSuccess) {
                    val uid = invRes.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        tag = IcodeSlixTag(rawUid = uid)
                    )
                    addLog("Nhận dạng UID: ${IcodeSlixDataFormatter.formatPrettyUid(uid)}")
                }
            }

            val result = rfidManager.readAllBlocks()
            result.onSuccess { blocks ->
                val hexBlocks = blocks.map { it.hexValue }
                val decodedText = IcodeSlixDataFormatter.parseBlocksToText(hexBlocks)
                val medicalData = IcodeSlixDataFormatter.parseMedicalPayload(decodedText)
                val status = IcodeSlixDataFormatter.evaluateTagStatus(blocks)

                val updatedTag = (_uiState.value.tag ?: IcodeSlixTag("E004010000000000")).copy(
                    blocks = blocks,
                    decodedText = decodedText,
                    medicalData = medicalData,
                    dataStatus = status
                )

                val statusDesc = when (status) {
                    IcodeSlixDataFormatter.TagDataStatus.VALID_MEDICAL_DATA -> "Dữ liệu Y tế hợp lệ (Ref: ${medicalData?.materialRef})"
                    IcodeSlixDataFormatter.TagDataStatus.EMPTY_TAG -> "THẺ TRẮNG (Chưa có dữ liệu / 100% rỗng)"
                    IcodeSlixDataFormatter.TagDataStatus.CUSTOM_PAYLOAD -> "Dữ liệu chuỗi tự do"
                    IcodeSlixDataFormatter.TagDataStatus.UNKNOWN_DATA -> "Dữ liệu lạ / Không xác định"
                }

                // Đồng bộ lên form nếu tìm thấy dữ liệu y tế hợp lệ
                val newState = if (medicalData != null && medicalData.materialRef.isNotEmpty()) {
                    _uiState.value.copy(
                        tag = updatedTag,
                        materialRef = medicalData.materialRef,
                        lot = medicalData.lot,
                        exp = medicalData.exp,
                        customText = decodedText,
                        statusMessage = "Đọc xong: $statusDesc",
                        isOperating = false
                    )
                } else {
                    _uiState.value.copy(
                        tag = updatedTag,
                        customText = decodedText,
                        statusMessage = "Đọc xong: $statusDesc",
                        isOperating = false
                    )
                }
                _uiState.value = newState
                addLog("Trạng thái thẻ: $statusDesc")
                if (decodedText.isNotEmpty()) {
                    addLog("Nội dung giải mã: \"$decodedText\"")
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Đọc dữ liệu thất bại: ${error.message}",
                    isOperating = false
                )
                addLog("Lỗi khi đọc bộ nhớ: ${error.message}")
            }
        }
    }

    /**
     * Ghi dữ liệu vào thẻ theo quy tắc:
     * - Cắt khối 4 Bytes
     * - Đệm 0x20
     * - Ghi từng single block từ Block 0
     */
    fun writeUserMemory() {
        val isCustom = _uiState.value.isCustomMode
        val payload = if (isCustom) {
            _uiState.value.customText.trim()
        } else {
            IcodeSlixDataFormatter.buildMedicalPayload(
                materialRef = _uiState.value.materialRef,
                lot = _uiState.value.lot,
                exp = _uiState.value.exp
            )
        }

        if (payload.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Dữ liệu trống, vui lòng nhập thông tin cần ghi!")
            addLog("Lỗi: Dữ liệu trống.")
            return
        }

        // Tự động kiểm tra nếu là chuỗi Raw Hex thô (từ công cụ reverse engineer hoặc nhập hex)
        val cleanHex = payload.replace(" ", "").replace("\n", "").uppercase()
        val isPureHex = isCustom && cleanHex.length >= 8 && cleanHex.matches(Regex("^[0-9A-F]+$"))

        if (!isPureHex) {
            val rawBytesLength = payload.toByteArray(Charsets.US_ASCII).size
            if (rawBytesLength > IcodeSlixDataFormatter.MAX_USER_MEMORY_BYTES) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Dữ liệu ($rawBytesLength bytes) vượt quá giới hạn 112 Bytes!"
                )
                addLog("Lỗi: Dữ liệu ($rawBytesLength bytes) vượt quá dung lượng 112 Bytes!")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, statusMessage = "Đang ghi dữ liệu vào thẻ...")
            val writeResult = if (isPureHex) {
                addLog("Ghi chuỗi Raw Hex (${cleanHex.length / 2} Bytes) vào thẻ...")
                rfidManager.writeRawHexDump(cleanHex)
            } else {
                val rawBytesLength = payload.toByteArray(Charsets.US_ASCII).size
                val chunks = IcodeSlixDataFormatter.formatTextToBlocks(payload)
                addLog("Đóng gói dữ liệu ($rawBytesLength Bytes) thành ${chunks.size} blocks (Padding 0x20).")
                rfidManager.writeDataString(payload)
            }

            writeResult.onSuccess { writtenCount ->
                addLog("Đã ghi thành công $writtenCount blocks vào thẻ.")
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Ghi thành công $writtenCount blocks!",
                    isOperating = false
                )
                readUserMemory()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Ghi thẻ thất bại: ${error.message}",
                    isOperating = false
                )
                addLog("Lỗi khi ghi thẻ: ${error.message}")
            }
        }
    }

    /**
     * Xóa sạch User Memory (Ghi đệm 0x20 vào tất cả 28 Blocks)
     */
    fun clearCard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, statusMessage = "Đang xóa sạch 28 Blocks...")
            addLog("Bắt đầu xóa trắng 28 Blocks (ghi đệm 0x20)...")

            val res = rfidManager.clearUserMemory()
            res.onSuccess {
                addLog("Đã xóa sạch bộ nhớ User Memory (28 Blocks).")
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Đã xóa sạch thẻ!",
                    isOperating = false
                )
                readUserMemory()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Xóa thẻ thất bại: ${error.message}",
                    isOperating = false
                )
                addLog("Lỗi khi xóa thẻ: ${error.message}")
            }
        }
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    fun setActiveTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    // ==========================================
    // CÁC THAO TÁC SINGLE BLOCK (0..27)
    // ==========================================

    fun selectBlock(index: Int) {
        val safeIndex = index.coerceIn(0, IcodeSlixDataFormatter.TOTAL_BLOCKS - 1)
        val currentTag = _uiState.value.tag
        val hex = currentTag?.blocks?.getOrNull(safeIndex)?.hexValue ?: "20202020"
        val ascii = IcodeSlixDataFormatter.hexBlockToReadableAscii(hex)
        _uiState.value = _uiState.value.copy(
            selectedBlockIndex = safeIndex,
            singleBlockHex = hex,
            singleBlockAscii = ascii
        )
    }

    fun updateSingleBlockHex(hex: String) {
        val clean = hex.trim().replace(" ", "").uppercase()
        val ascii = IcodeSlixDataFormatter.hexBlockToReadableAscii(clean)
        _uiState.value = _uiState.value.copy(
            singleBlockHex = clean,
            singleBlockAscii = ascii
        )
    }

    fun updateSingleBlockAscii(ascii: String) {
        val cleanAscii = if (ascii.length > 4) ascii.substring(0, 4) else ascii
        val padded = cleanAscii.padEnd(4, ' ')
        val hex = IcodeSlixDataFormatter.bytesToHex(padded.toByteArray(Charsets.US_ASCII))
        _uiState.value = _uiState.value.copy(
            singleBlockHex = hex,
            singleBlockAscii = padded
        )
    }

    fun readSingleBlock(index: Int? = null) {
        val targetIndex = index ?: _uiState.value.selectedBlockIndex
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, statusMessage = "Đang đọc Block $targetIndex...")
            addLog("Đọc Single Block $targetIndex...")
            val result = rfidManager.readBlock(targetIndex)
            result.onSuccess { hex ->
                val ascii = IcodeSlixDataFormatter.hexBlockToReadableAscii(hex)
                val currentBlocks = _uiState.value.tag?.blocks?.toMutableList() ?: (0 until 28).map { MemoryBlock(it) }.toMutableList()
                if (targetIndex in currentBlocks.indices) {
                    currentBlocks[targetIndex] = MemoryBlock(targetIndex, hex, ascii)
                }
                val updatedTag = (_uiState.value.tag ?: IcodeSlixTag("E004010000000000")).copy(blocks = currentBlocks)
                _uiState.value = _uiState.value.copy(
                    tag = updatedTag,
                    selectedBlockIndex = targetIndex,
                    singleBlockHex = hex,
                    singleBlockAscii = ascii,
                    statusMessage = "Đọc Block $targetIndex thành công: $hex",
                    isOperating = false
                )
                addLog("Block $targetIndex: Hex=$hex | ASCII=[$ascii]")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Đọc Block $targetIndex thất bại: ${error.message}",
                    isOperating = false
                )
                addLog("Lỗi đọc Block $targetIndex: ${error.message}")
            }
        }
    }

    fun writeSingleBlock() {
        val blockIdx = _uiState.value.selectedBlockIndex
        var hexData = _uiState.value.singleBlockHex.trim().uppercase()
        while (hexData.length < 8) hexData += "20"
        if (hexData.length > 8) hexData = hexData.substring(0, 8)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, statusMessage = "Đang ghi Block $blockIdx ($hexData)...")
            addLog("Ghi Block $blockIdx với Hex: $hexData")
            val result = rfidManager.writeBlock(blockIdx, hexData)
            result.onSuccess {
                addLog("Ghi thành công Block $blockIdx.")
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Ghi Block $blockIdx thành công!",
                    isOperating = false
                )
                readSingleBlock(blockIdx)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Ghi Block $blockIdx thất bại: ${error.message}",
                    isOperating = false
                )
                addLog("Lỗi ghi Block $blockIdx: ${error.message}")
            }
        }
    }

    // ==========================================
    // CÁC THAO TÁC RAW HEX DUMP (TOÀN BỘ 112 BYTES)
    // ==========================================

    fun updateRawHexDump(hex: String) {
        _uiState.value = _uiState.value.copy(rawHexDump = hex)
    }

    fun readAllToRawHex() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, statusMessage = "Đang đọc toàn bộ 28 Blocks ra Raw Hex...")
            addLog("Đọc toàn bộ User Memory để xuất Raw Hex...")
            val result = rfidManager.readAllBlocks()
            result.onSuccess { blocks ->
                val fullHex = blocks.joinToString("") { it.hexValue }
                _uiState.value = _uiState.value.copy(
                    rawHexDump = fullHex,
                    statusMessage = "Đã xuất chuỗi Raw Hex (${fullHex.length} ký tự Hex = ${fullHex.length / 2} Bytes)",
                    isOperating = false
                )
                addLog("Xuất Raw Hex thành công: ${fullHex.take(32)}...")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Đọc Hex thất bại: ${error.message}",
                    isOperating = false
                )
                addLog("Lỗi đọc toàn bộ hex: ${error.message}")
            }
        }
    }

    fun writeRawHexDumpToTag() {
        val hex = _uiState.value.rawHexDump.trim()
        if (hex.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Chuỗi Raw Hex trống!")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, statusMessage = "Đang ghi chuỗi Raw Hex vào thẻ...")
            addLog("Bắt đầu ghi Raw Hex Dump (${hex.length / 2} Bytes) vào thẻ...")
            val result = rfidManager.writeRawHexDump(hex)
            result.onSuccess { writtenCount ->
                addLog("Ghi thành công $writtenCount blocks từ chuỗi Raw Hex.")
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Ghi Raw Hex thành công $writtenCount blocks!",
                    isOperating = false
                )
                readUserMemory()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Ghi Raw Hex thất bại: ${error.message}",
                    isOperating = false
                )
                addLog("Lỗi ghi Raw Hex: ${error.message}")
            }
        }
    }

    // ==========================================
    // CÔNG CỤ REVERSE ENGINEERING THẺ MẪU
    // ==========================================

    fun updateReverseHexInput(value: String) {
        _uiState.value = _uiState.value.copy(reverseHexInput = value)
    }

    fun updateReverseTargetRef(value: String) {
        _uiState.value = _uiState.value.copy(reverseTargetRef = value)
    }

    fun updateReverseTargetLot(value: String) {
        _uiState.value = _uiState.value.copy(reverseTargetLot = value)
    }

    fun updateReverseTargetExp(value: String) {
        _uiState.value = _uiState.value.copy(reverseTargetExp = value)
    }

    fun loadReversePreset(presetIndex: Int) {
        val hex = when (presetIndex) {
            1 -> SampleCardReverseEngineer.Presets.ROCHE_SYPHILIS_ASCII_HEX
            2 -> SampleCardReverseEngineer.Presets.ROCHE_BINARY_BCD_HEX
            else -> SampleCardReverseEngineer.Presets.BLANK_TAG_HEX
        }
        val presetName = when (presetIndex) {
            1 -> "Mẫu Roche Syphilis (Dạng ASCII)"
            2 -> "Mẫu Roche Chuẩn Thiết Bị (BCD & Binary Packed)"
            else -> "Mẫu Thẻ Trắng (Blank Tag)"
        }
        _uiState.value = _uiState.value.copy(reverseHexInput = hex)
        addLog("Đã nạp preset: $presetName")
        runReverseAnalysis()
    }

    fun importCurrentTagToReverse() {
        val currentBlocks = _uiState.value.tag?.blocks
        if (currentBlocks.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Chưa có dữ liệu thẻ. Hãy bấm 'Đọc thẻ' trước.")
            return
        }
        val hex = currentBlocks.joinToString("") { it.hexValue }
        _uiState.value = _uiState.value.copy(reverseHexInput = hex)
        addLog("Đã trích xuất ${hex.length / 2} Bytes từ thẻ hiện tại sang công cụ Reverse Engineer.")
        runReverseAnalysis()
    }

    fun runReverseAnalysis() {
        val state = _uiState.value
        val report = SampleCardReverseEngineer.analyzePayload(
            rawHexInput = state.reverseHexInput,
            targetRef = state.reverseTargetRef,
            targetLot = state.reverseTargetLot,
            targetExp = state.reverseTargetExp,
            uidHex = state.tag?.rawUid ?: ""
        )
        _uiState.value = _uiState.value.copy(
            reverseReport = report,
            statusMessage = "Đã phân tích: ${report.summaryMessage}"
        )
        addLog("Phân tích thẻ mẫu: ${report.detectedFields.size} trường khớp. CRC16=0x${report.crc16Iso15693}")
    }

    fun transferReverseHexToRawWrite() {
        val hex = _uiState.value.reverseHexInput
        val cleanHex = hex.trim().replace(" ", "").replace("\n", "").uppercase()
        val hexBlocks = (0 until 28).map { i ->
            val start = i * 8
            val end = minOf(start + 8, cleanHex.length)
            if (start < cleanHex.length) cleanHex.substring(start, end) else "20202020"
        }
        val asciiText = IcodeSlixDataFormatter.parseBlocksToText(hexBlocks)
        val medical = IcodeSlixDataFormatter.parseMedicalPayload(asciiText)

        if (medical != null && medical.materialRef.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                materialRef = medical.materialRef,
                lot = medical.lot,
                exp = medical.exp,
                customText = cleanHex,
                isCustomMode = false,
                activeTab = 0, // Chuyển về Tab 0: Đọc - Ghi Thẻ
                statusMessage = "Đã nạp dữ liệu thẻ mẫu! Nhấn 'Ghi thẻ' để bắt đầu ghi."
            )
        } else {
            _uiState.value = _uiState.value.copy(
                customText = cleanHex,
                isCustomMode = true,
                activeTab = 0, // Chuyển về Tab 0: Đọc - Ghi Thẻ
                statusMessage = "Đã nạp chuỗi Hex thẻ mẫu! Nhấn 'Ghi thẻ' để bắt đầu ghi."
            )
        }
        addLog("Đã nạp dữ liệu sang Tab Đọc - Ghi. Sẵn sàng ghi vào thẻ mới!")
    }
}
