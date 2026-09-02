package com.example.nfc.rfid

import android.util.Log
import com.example.nfc.data.IcodeSlixDataFormatter
import com.example.nfc.data.IcodeSlixTag
import com.example.nfc.data.MemoryBlock
import com.rscja.deviceapi.RFIDWithISO15693
import com.rscja.deviceapi.entity.ISO15693Entity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lớp quản lý phần cứng RFID HF / ISO 15693 cho thiết bị Chainway C5.
 * Tương tác trực tiếp với SDK [RFIDWithISO15693] của hãng Chainway.
 */
class ChainwayHfManager {

    companion object {
        private const val TAG = "ChainwayHfManager"
        @Volatile
        private var instance: ChainwayHfManager? = null

        fun getInstance(): ChainwayHfManager {
            return instance ?: synchronized(this) {
                instance ?: ChainwayHfManager().also { instance = it }
            }
        }
    }

    private var rfidReader: RFIDWithISO15693? = null
    private var isConnected: Boolean = false
    private var isSimulatedMode: Boolean = false
    private val simulatedMemory = Array(IcodeSlixDataFormatter.TOTAL_BLOCKS) { "20202020" }

    /**
     * Khởi tạo và bật nguồn module RFID HF trên máy Chainway C5.
     */
    suspend fun initHardware(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (rfidReader == null) {
                rfidReader = RFIDWithISO15693.getInstance()
            }
            val success = rfidReader?.init() ?: false
            isConnected = success
            if (success) {
                Log.d(TAG, "Khởi tạo phần cứng Chainway RFID HF thành công.")
                Result.success(true)
            } else {
                Log.w(TAG, "Không thể kích hoạt module RFID phần cứng Chainway. Chuyển sang chế độ giả lập an toàn.")
                isSimulatedMode = true
                Result.success(true)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi nạp thư viện hoặc khởi tạo phần cứng: ${t.message}. Kích hoạt chế độ giả lập.")
            isSimulatedMode = true
            Result.success(true)
        }
    }

    /**
     * Tắt nguồn và giải phóng tài nguyên module RFID HF.
     */
    suspend fun freeHardware(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (isSimulatedMode) {
                isConnected = false
                return@withContext Result.success(true)
            }
            val result = rfidReader?.free() ?: false
            isConnected = false
            Result.success(result)
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi giải phóng phần cứng: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Quét thẻ và đọc 8 Bytes UID.
     * Trả về chuỗi Hex của UID (ví dụ: "E0040153238F7F40").
     */
    suspend fun inventory(): Result<String> = withContext(Dispatchers.IO) {
        if (isSimulatedMode) {
            // Trả về UID mẫu NXP ICODE SLIX từ ảnh 2
            return@withContext Result.success("E0040153238F7F40")
        }

        try {
            val reader = rfidReader ?: return@withContext Result.failure(IllegalStateException("Module RFID chưa được khởi tạo."))
            val entity: ISO15693Entity? = reader.inventory()
            if (entity != null && !entity.id.isNullOrEmpty()) {
                val rawUid = entity.id.trim().uppercase()
                Log.d(TAG, "Tìm thấy thẻ ISO 15693 với UID: $rawUid")
                Result.success(rawUid)
            } else {
                Result.failure(NoSuchElementException("Không phát hiện thẻ trong vùng quét HF!"))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi quét thẻ: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Đọc 1 Block (4 Bytes) tại vị trí blockIndex (0 đến 27).
     * Trả về chuỗi 8 ký tự Hex.
     */
    suspend fun readBlock(blockIndex: Int): Result<String> = withContext(Dispatchers.IO) {
        require(blockIndex in 0 until IcodeSlixDataFormatter.TOTAL_BLOCKS) {
            "BlockIndex ngoài phạm vi (0..27): $blockIndex"
        }

        if (isSimulatedMode) {
            return@withContext Result.success(simulatedMemory[blockIndex])
        }

        try {
            val reader = rfidReader ?: return@withContext Result.failure(IllegalStateException("Module RFID chưa khởi tạo."))
            val entity = reader.read(blockIndex)
            if (entity != null && !entity.data.isNullOrEmpty()) {
                val hexData = entity.data.trim().replace(" ", "").uppercase()
                Result.success(hexData)
            } else {
                Result.failure(IllegalStateException("Không đọc được dữ liệu tại Block $blockIndex"))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi đọc Block $blockIndex: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Đọc toàn bộ 28 Blocks (112 Bytes) trong User Memory của ICODE SLIX.
     */
    suspend fun readAllBlocks(): Result<List<MemoryBlock>> = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<MemoryBlock>()
        val hexList = mutableListOf<String>()

        try {
            // Thử đọc theo dải khối nếu phần cứng hỗ trợ
            if (!isSimulatedMode && rfidReader != null) {
                var bulkSuccess = false
                try {
                    val entity = rfidReader?.read(0, IcodeSlixDataFormatter.TOTAL_BLOCKS)
                    if (entity != null && !entity.data.isNullOrEmpty()) {
                        val fullHex = entity.data.trim().replace(" ", "").uppercase()
                        if (fullHex.length >= IcodeSlixDataFormatter.TOTAL_BLOCKS * 8) {
                            for (i in 0 until IcodeSlixDataFormatter.TOTAL_BLOCKS) {
                                val blockHex = fullHex.substring(i * 8, (i + 1) * 8)
                                val ascii = IcodeSlixDataFormatter.hexBlockToReadableAscii(blockHex)
                                blocks.add(MemoryBlock(i, blockHex, ascii))
                            }
                            bulkSuccess = true
                        }
                    }
                } catch (_: Throwable) {
                    bulkSuccess = false
                }

                if (!bulkSuccess) {
                    // Fallback sang đọc từng Single Block
                    for (i in 0 until IcodeSlixDataFormatter.TOTAL_BLOCKS) {
                        val singleResult = readBlock(i)
                        val hex = singleResult.getOrElse { "20202020" }
                        val ascii = IcodeSlixDataFormatter.hexBlockToReadableAscii(hex)
                        blocks.add(MemoryBlock(i, hex, ascii))
                    }
                }
            } else {
                // Đọc từ bộ nhớ giả lập
                for (i in 0 until IcodeSlixDataFormatter.TOTAL_BLOCKS) {
                    val hex = simulatedMemory[i]
                    val ascii = IcodeSlixDataFormatter.hexBlockToReadableAscii(hex)
                    blocks.add(MemoryBlock(i, hex, ascii))
                }
            }

            Result.success(blocks)
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi đọc toàn bộ User Memory: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Ghi 1 Block (4 Bytes) tại blockIndex với dữ liệu 8 ký tự Hex.
     */
    suspend fun writeBlock(blockIndex: Int, hexData: String): Result<Boolean> = withContext(Dispatchers.IO) {
        require(blockIndex in 0 until IcodeSlixDataFormatter.TOTAL_BLOCKS) {
            "BlockIndex ngoài phạm vi (0..27): $blockIndex"
        }
        val cleanHex = hexData.trim().replace(" ", "").uppercase()
        require(cleanHex.length == 8) {
            "Dữ liệu ghi vào Block phải đúng 4 Bytes (8 ký tự Hex)! Nhận được: $cleanHex"
        }

        if (isSimulatedMode) {
            simulatedMemory[blockIndex] = cleanHex
            Log.d(TAG, "[Giả lập] Ghi thành công Block $blockIndex: $cleanHex")
            return@withContext Result.success(true)
        }

        try {
            val reader = rfidReader ?: return@withContext Result.failure(IllegalStateException("Module RFID chưa khởi tạo."))
            val success = reader.write(blockIndex, cleanHex)
            if (success) {
                Log.d(TAG, "Ghi thành công Block $blockIndex: $cleanHex")
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("Ghi Block $blockIndex thất bại từ phần cứng."))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi ghi Block $blockIndex: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Ghi chuỗi dữ liệu hoàn chỉnh vào thẻ:
     * - Tự động cắt khối 4 Bytes
     * - Tự động đệm padding 0x20 cho block cuối
     * - Ghi tuần tự từng Single Block từ Block 0 trở đi
     */
    suspend fun writeDataString(dataString: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val hexBlocks = IcodeSlixDataFormatter.formatTextToBlocks(dataString)
            if (hexBlocks.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Dữ liệu trống, không có gì để ghi."))
            }

            var writtenCount = 0
            for (i in hexBlocks.indices) {
                val writeRes = writeBlock(i, hexBlocks[i])
                if (writeRes.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException("Ghi thất bại tại Block $i: ${writeRes.exceptionOrNull()?.message}")
                    )
                }
                writtenCount++
            }

            Log.d(TAG, "Đã ghi thành công $writtenCount blocks vào thẻ ICODE SLIX.")
            Result.success(writtenCount)
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi ghi chuỗi dữ liệu vào thẻ: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Ghi chuỗi Hex thô (Raw Hex Dump) gồm tối đa 28 blocks (224 ký tự Hex) vào thẻ.
     * Thích hợp để sao chép (Clone) nguyên bản dữ liệu reverse engineer từ thẻ mẫu.
     */
    suspend fun writeRawHexDump(rawHex: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cleanHex = rawHex.trim().replace(" ", "").replace("\n", "").replace(":", "").uppercase()
            if (cleanHex.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Chuỗi Hex trống!"))
            }

            // Kiểm tra tính hợp lệ của ký tự Hex
            val validHexRegex = Regex("^[0-9A-F]+$")
            if (!validHexRegex.matches(cleanHex)) {
                return@withContext Result.failure(IllegalArgumentException("Chuỗi chứa ký tự không phải mã Hex hợp lệ!"))
            }

            // Đệm 0 ở cuối nếu chưa tròn byte (chẵn ký tự)
            var hexString = cleanHex
            if (hexString.length % 2 != 0) {
                hexString += "0"
            }

            // Cắt thành các khối 8 ký tự (4 bytes)
            val blocks = mutableListOf<String>()
            var offset = 0
            while (offset < hexString.length && blocks.size < IcodeSlixDataFormatter.TOTAL_BLOCKS) {
                val end = minOf(offset + 8, hexString.length)
                var blockChunk = hexString.substring(offset, end)
                // Đệm 0x20 ('20') nếu block chưa đủ 8 ký tự hex
                while (blockChunk.length < 8) {
                    blockChunk += "20"
                }
                blocks.add(blockChunk)
                offset += 8
            }

            var writtenCount = 0
            for (i in blocks.indices) {
                val writeRes = writeBlock(i, blocks[i])
                if (writeRes.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException("Ghi thất bại tại Block $i: ${writeRes.exceptionOrNull()?.message}")
                    )
                }
                writtenCount++
            }

            Log.d(TAG, "Đã ghi thành công $writtenCount blocks từ chuỗi Raw Hex.")
            Result.success(writtenCount)
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi ghi Raw Hex Dump: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Xóa sạch User Memory (Ghi khoảng trắng 0x20 vào tất cả 28 Blocks).
     */
    suspend fun clearUserMemory(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (isSimulatedMode) {
            simulatedMemory.fill("20202020")
            Log.d(TAG, "[Giả lập] Đã xóa sạch 28 blocks bộ nhớ.")
            return@withContext Result.success(true)
        }

        try {
            val blankBlockHex = "20202020" // 4 byte Space
            for (i in 0 until IcodeSlixDataFormatter.TOTAL_BLOCKS) {
                val res = writeBlock(i, blankBlockHex)
                if (res.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException("Xóa thất bại tại Block $i")
                    )
                }
            }
            Result.success(true)
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi xóa thẻ: ${t.message}", t)
            Result.failure(t)
        }
    }

    fun isConnected(): Boolean = isConnected
    fun isSimulated(): Boolean = isSimulatedMode
}
