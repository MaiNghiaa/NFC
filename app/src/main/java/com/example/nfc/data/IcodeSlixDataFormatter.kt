package com.example.nfc.data

import java.nio.charset.StandardCharsets

/**
 * Tiện ích mã hóa / giải mã dữ liệu cho thẻ RFID ISO 15693 (NXP ICODE SLIX).
 *
 * Thông số kỹ thuật ICODE SLIX:
 * - Tổng dung lượng User Memory: 112 Bytes (28 Blocks, từ Block 0 đến Block 27).
 * - Kích thước mỗi Block: 4 Bytes (32 bits).
 * - Ký tự đệm (Padding): 0x20 (Khoảng trắng ASCII ' ') cho block cuối nếu thiếu byte.
 */
object IcodeSlixDataFormatter {

    const val TOTAL_BLOCKS = 28
    const val BYTES_PER_BLOCK = 4
    const val MAX_USER_MEMORY_BYTES = TOTAL_BLOCKS * BYTES_PER_BLOCK // 112 Bytes
    const val PADDING_BYTE: Byte = 0x20 // ASCII space ' '

    /**
     * Chuyển đổi một chuỗi văn bản bất kỳ thành danh sách các chuỗi Hex (mỗi chuỗi đúng 8 ký tự Hex = 4 bytes)
     * sẵn sàng để ghi vào các Block từ 0 trở đi.
     *
     * @param rawText Chuỗi văn bản cần ghi vào thẻ (tối đa 112 bytes).
     * @return Danh sách các chuỗi Hex đại diện cho từng Block 4 bytes.
     * @throws IllegalArgumentException Nếu chuỗi dữ liệu vượt quá 112 bytes.
     */
    fun formatTextToBlocks(rawText: String): List<String> {
        val rawBytes = rawText.toByteArray(StandardCharsets.US_ASCII)
        require(rawBytes.size <= MAX_USER_MEMORY_BYTES) {
            "Dữ liệu vượt quá dung lượng User Memory (112 Bytes)! Hiện tại: ${rawBytes.size} Bytes"
        }

        if (rawBytes.isEmpty()) {
            return emptyList()
        }

        val resultBlocks = mutableListOf<String>()
        var offset = 0

        while (offset < rawBytes.size) {
            val remaining = rawBytes.size - offset
            val chunkSize = minOf(remaining, BYTES_PER_BLOCK)
            val chunk = ByteArray(BYTES_PER_BLOCK) { PADDING_BYTE }

            // Copy data vào chunk
            System.arraycopy(rawBytes, offset, chunk, 0, chunkSize)

            // Chuyển chunk 4 bytes thành chuỗi Hex 8 ký tự
            val hexString = bytesToHex(chunk)
            resultBlocks.add(hexString)

            offset += chunkSize
        }

        return resultBlocks
    }

    /**
     * Giải mã danh sách các khối Hex đọc từ thẻ (mỗi chuỗi Hex 8 ký tự) thành chuỗi ký tự ASCII,
     * tự động loại bỏ các byte khoảng trắng 0x20 (Padding) ở cuối.
     */
    fun parseBlocksToText(hexBlocks: List<String>): String {
        if (hexBlocks.isEmpty()) return ""

        val allBytes = mutableListOf<Byte>()
        for (hex in hexBlocks) {
            val cleanHex = hex.trim().replace(" ", "")
            if (cleanHex.isNotEmpty()) {
                val bytes = hexToBytes(cleanHex)
                allBytes.addAll(bytes.toList())
            }
        }

        if (allBytes.isEmpty()) return ""

        // Tìm vị trí kết thúc thực sự (bỏ qua trailing 0x20 và null byte 0x00)
        var lastValidIndex = allBytes.size - 1
        while (lastValidIndex >= 0 && (allBytes[lastValidIndex] == PADDING_BYTE || allBytes[lastValidIndex] == 0.toByte())) {
            lastValidIndex--
        }

        if (lastValidIndex < 0) return ""

        val trimmedBytes = allBytes.subList(0, lastValidIndex + 1).toByteArray()
        return String(trimmedBytes, StandardCharsets.US_ASCII)
    }

    /**
     * Chuyển đổi 1 Block Hex 8 ký tự sang dạng text ASCII hiển thị (thay thế ký tự không in được bằng '.')
     */
    fun hexBlockToReadableAscii(hexBlock: String): String {
        val clean = hexBlock.trim().replace(" ", "")
        if (clean.length < 2) return ""
        val bytes = hexToBytes(clean)
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c in ' '..'~') {
                sb.append(c)
            } else {
                sb.append('.')
            }
        }
        return sb.toString()
    }

    /**
     * Đóng gói thông tin vật tư y tế (Mã Ref, LOT, Hạn dùng EXP) thành chuỗi có định dạng chuẩn.
     * Ví dụ: "REF:09015051190|LOT:93077101|EXP:2027-02-28"
     */
    fun buildMedicalPayload(materialRef: String, lot: String, exp: String): String {
        return "REF:${materialRef.trim()}|LOT:${lot.trim()}|EXP:${exp.trim()}"
    }

    /**
     * Phân tích chuỗi dữ liệu thẻ y tế ra các trường riêng biệt.
     */
    fun parseMedicalPayload(rawString: String): MedicalMaterialData? {
        if (rawString.isBlank()) return null
        return try {
            var ref = ""
            var lot = ""
            var exp = ""

            val parts = rawString.split("|")
            for (part in parts) {
                val pair = part.split(":", limit = 2)
                if (pair.size == 2) {
                    when (pair[0].trim().uppercase()) {
                        "REF" -> ref = pair[1].trim()
                        "LOT" -> lot = pair[1].trim()
                        "EXP" -> exp = pair[1].trim()
                    }
                }
            }

            if (ref.isNotEmpty() || lot.isNotEmpty() || exp.isNotEmpty()) {
                MedicalMaterialData(
                    materialRef = ref,
                    lot = lot,
                    exp = exp,
                    rawPayload = rawString
                )
            } else {
                MedicalMaterialData(
                    materialRef = "",
                    lot = "",
                    exp = "",
                    rawPayload = rawString
                )
            }
        } catch (e: Exception) {
            MedicalMaterialData(
                materialRef = "",
                lot = "",
                exp = "",
                rawPayload = rawString
            )
        }
    }

    /**
     * Trạng thái dữ liệu của thẻ RFID
     */
    enum class TagDataStatus {
        EMPTY_TAG,          // Thẻ trắng (chưa từng ghi hoặc đã xóa sạch)
        VALID_MEDICAL_DATA, // Có dữ liệu vật tư y tế hợp lệ (bắt đầu bằng REF:, có LOT, EXP)
        CUSTOM_PAYLOAD,     // Có dữ liệu nhưng không theo cấu trúc chuẩn y tế
        UNKNOWN_DATA        // Dữ liệu lạ hoặc byte không đọc được
    }

    /**
     * Kiểm tra chính xác xem thẻ có dữ liệu hay không và dữ liệu thuộc loại nào.
     */
    fun evaluateTagStatus(blocks: List<MemoryBlock>): TagDataStatus {
        if (blocks.isEmpty()) return TagDataStatus.EMPTY_TAG

        // 1. Kiểm tra xem toàn bộ các block có phải là thẻ trắng không (chỉ chứa 0x20 hoặc 0x00 hoặc 0xFF)
        val isAllBlank = blocks.all { block ->
            val clean = block.hexValue.trim().uppercase()
            clean == "20202020" || clean == "00000000" || clean == "FFFFFFFF" || clean.isEmpty()
        }
        if (isAllBlank) {
            return TagDataStatus.EMPTY_TAG
        }

        // 2. Kiểm tra Magic Header ở Block 0 (Block 0 phải là "REF:" -> Hex "5245463A")
        val block0 = blocks.firstOrNull()?.hexValue?.trim()?.uppercase() ?: ""
        val hexList = blocks.map { it.hexValue }
        val decoded = parseBlocksToText(hexList)

        return if (block0 == "5245463A" || (decoded.contains("REF:") && decoded.contains("LOT:"))) {
            TagDataStatus.VALID_MEDICAL_DATA
        } else if (decoded.isNotBlank()) {
            TagDataStatus.CUSTOM_PAYLOAD
        } else {
            TagDataStatus.UNKNOWN_DATA
        }
    }

    /**
     * Chuyển mảng byte thành chuỗi Hex in hoa (ví dụ: [0x41, 0x42] -> "4142").
     */
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val octet = b.toInt() and 0xFF
            result.append(hexChars[octet shr 4])
            result.append(hexChars[octet and 0x0F])
        }
        return result.toString()
    }

    /**
     * Chuyển chuỗi Hex thành mảng byte.
     */
    fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Định dạng UID 8 Bytes Hex sang dạng hiển thị có dấu hai chấm: E0:04:01:53:23:8F:7F:40
     */
    fun formatPrettyUid(rawUid: String): String {
        val clean = rawUid.trim().replace(" ", "").replace(":", "").uppercase()
        if (clean.isEmpty()) return ""
        val sb = StringBuilder()
        for (i in clean.indices step 2) {
            if (i > 0) sb.append(":")
            if (i + 2 <= clean.length) {
                sb.append(clean.substring(i, i + 2))
            } else {
                sb.append(clean.substring(i))
            }
        }
        return sb.toString()
    }

    /**
     * Kiểm tra UID có phải là chip NXP ICODE SLIX chuẩn ISO 15693 không:
     * - Độ dài: 16 ký tự Hex (8 Bytes)
     * - Bắt đầu bằng: E0 04 01 (E0 = ISO 15693, 04 = NXP, 01 = ICODE SLIX)
     */
    fun isNxpIcodeSlix(rawUid: String): Boolean {
        val clean = rawUid.trim().replace(" ", "").replace(":", "").uppercase()
        return clean.length == 16 && clean.startsWith("E00401")
    }
}
