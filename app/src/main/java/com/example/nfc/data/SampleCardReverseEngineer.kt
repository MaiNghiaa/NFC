package com.example.nfc.data

import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Loại mã hóa phát hiện được trong chuỗi dữ liệu thẻ mẫu.
 */
enum class FieldEncoding {
    ASCII_TEXT,          // Chuỗi ký tự ASCII thông thường (ví dụ '0','9','0'...)
    BCD_NUMERIC,         // Binary Coded Decimal (nén 2 chữ số vào 1 byte, ví dụ 0x09 0x01)
    INTEGER_BIG_ENDIAN,  // Số nguyên 32-bit Big Endian (MSB đầu tiên)
    INTEGER_LITTLE_ENDIAN,// Số nguyên 32-bit Little Endian (LSB đầu tiên)
    DATE_BCD,            // Ngày tháng dạng BCD (YYYYMMDD hoặc YYMMDD)
    CHECKSUM_CRC,        // Mã kiểm tra toàn vẹn (CRC16/CRC32/XOR)
    UNKNOWN
}

/**
 * Phân loại nội dung của từng Block bộ nhớ (cho bản đồ màu 28 Blocks).
 */
enum class BlockCategory {
    MATERIAL_REF,   // Chứa mã vật tư (Màu Xanh Lam)
    LOT_NUMBER,     // Chứa số LOT (Màu Xanh Lục)
    EXPIRATION,     // Chứa hạn sử dụng (Màu Cam)
    COUNTER_TESTS,  // Chứa số lượt test / bộ đếm (Màu Tím)
    CHECKSUM,       // Chứa mã kiểm tra CRC/Hash (Màu Đỏ đô)
    CUSTOM_DATA,    // Dữ liệu khác (Màu Vàng nâu)
    BLANK_SPACE     // Block trống / Khoảng trắng 0x20 (Màu Xám)
}

/**
 * Một mục phát hiện được sau khi quét phân tích.
 */
data class DetectedField(
    val fieldName: String,         // "Mã vật tư (REF)", "Số LOT", "Hạn dùng (EXP)"...
    val searchValue: String,       // Giá trị tìm kiếm ban đầu (ví dụ "09015051190")
    val encoding: FieldEncoding,   // Loại mã hóa phát hiện
    val startByte: Int,            // Vị trí Byte bắt đầu (0..111)
    val lengthBytes: Int,          // Số lượng Byte
    val startBlock: Int = startByte / 4,
    val endBlock: Int = (startByte + lengthBytes - 1) / 4,
    val matchedHex: String,        // Chuỗi Hex thực tế khớp trong thẻ
    val description: String        // Mô tả giải thích chi tiết
)

/**
 * Báo cáo kết quả phân tích chuỗi thẻ mẫu (Reverse Engineering Report).
 */
data class ReverseEngineerReport(
    val rawHex: String,
    val totalBytes: Int,
    val detectedFields: List<DetectedField>,
    val blockCategories: List<BlockCategory>,
    val crc16Iso15693: String,
    val crc32Hex: String,
    val uidMatchFound: Boolean,
    val summaryMessage: String
)

/**
 * Công cụ phân tích và dịch ngược cấu trúc dữ liệu trên thẻ mẫu RFID ICODE SLIX (ISO 15693).
 */
object SampleCardReverseEngineer {

    /**
     * Chuyển chuỗi số dạng chuỗi (ví dụ "09015051190") thành mảng byte BCD (Binary Coded Decimal).
     * Nếu chuỗi có độ dài lẻ, có thể đệm 0 ở đầu hoặc đệm 0xF ở cuối theo chuẩn viễn thông/smartcard.
     */
    fun stringToBcd(numericString: String, padLeftZero: Boolean = true): ByteArray {
        val clean = numericString.filter { it.isDigit() }
        if (clean.isEmpty()) return ByteArray(0)

        val padded = if (clean.length % 2 != 0) {
            if (padLeftZero) "0$clean" else "${clean}F"
        } else {
            clean
        }

        val result = ByteArray(padded.length / 2)
        for (i in 0 until padded.length step 2) {
            val high = Character.digit(padded[i], 16)
            val low = Character.digit(padded[i + 1], 16)
            result[i / 2] = ((high shl 4) or (low and 0x0F)).toByte()
        }
        return result
    }

    /**
     * Chuyển số nguyên Long sang mảng byte 4 Bytes (32-bit):
     * @param isBigEndian true = Big-Endian (MSB trước), false = Little-Endian (LSB trước).
     */
    fun intTo4Bytes(value: Long, isBigEndian: Boolean): ByteArray {
        val bytes = ByteArray(4)
        if (isBigEndian) {
            bytes[0] = ((value shr 24) and 0xFF).toByte()
            bytes[1] = ((value shr 16) and 0xFF).toByte()
            bytes[2] = ((value shr 8) and 0xFF).toByte()
            bytes[3] = (value and 0xFF).toByte()
        } else {
            bytes[0] = (value and 0xFF).toByte()
            bytes[1] = ((value shr 8) and 0xFF).toByte()
            bytes[2] = ((value shr 16) and 0xFF).toByte()
            bytes[3] = ((value shr 24) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Tìm vị trí xuất hiện của mảng byte con (needle) trong mảng byte cha (haystack).
     * Trả về chỉ số byte bắt đầu, hoặc -1 nếu không tìm thấy.
     */
    fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.isEmpty() || needle.size > haystack.size) return -1
        for (i in 0..haystack.size - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    /**
     * Tính mã CRC-16 theo chuẩn ISO/IEC 15693 (Đa thức 0x8408 - CCITT đảo, Giá trị khởi tạo 0xFFFF, Đảo kết quả).
     */
    fun calculateCrc16Iso15693(data: ByteArray): Int {
        var crc = 0xFFFF
        val polynomial = 0x8408

        for (b in data) {
            var currentByte = b.toInt() and 0xFF
            for (i in 0 until 8) {
                if (((crc xor currentByte) and 0x0001) != 0) {
                    crc = (crc shr 1) xor polynomial
                } else {
                    crc = crc shr 1
                }
                currentByte = currentByte shr 1
            }
        }
        return (crc xor 0xFFFF) and 0xFFFF
    }

    /**
     * Tính mã CRC-32 tiêu chuẩn.
     */
    fun calculateCrc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    /**
     * Phân tích toàn diện chuỗi thẻ mẫu và đối chiếu với các giá trị nhãn (REF, LOT, EXP).
     */
    fun analyzePayload(
        rawHexInput: String,
        targetRef: String,
        targetLot: String,
        targetExp: String,
        uidHex: String = ""
    ): ReverseEngineerReport {
        val cleanHex = rawHexInput.trim().replace(" ", "").replace("\n", "").replace(":", "").uppercase()
        val rawBytes = IcodeSlixDataFormatter.hexToBytes(cleanHex)
        val totalBytes = rawBytes.size
        val detectedFields = mutableListOf<DetectedField>()

        // 1. Quét tìm mã REF
        val cleanRef = targetRef.trim()
        if (cleanRef.isNotEmpty()) {
            // 1.1 ASCII Match
            val refAscii = cleanRef.toByteArray(StandardCharsets.US_ASCII)
            val idxAscii = indexOfBytes(rawBytes, refAscii)
            if (idxAscii >= 0) {
                detectedFields.add(
                    DetectedField(
                        fieldName = "Mã vật tư (REF)",
                        searchValue = cleanRef,
                        encoding = FieldEncoding.ASCII_TEXT,
                        startByte = idxAscii,
                        lengthBytes = refAscii.size,
                        matchedHex = IcodeSlixDataFormatter.bytesToHex(refAscii),
                        description = "Tìm thấy dạng ký tự ASCII rõ ràng từ Byte $idxAscii (Block ${idxAscii / 4})"
                    )
                )
            }

            // 1.2 BCD Match (nén 2 số/byte)
            val refBcd = stringToBcd(cleanRef, padLeftZero = true)
            val idxBcd = indexOfBytes(rawBytes, refBcd)
            if (idxBcd >= 0 && idxBcd != idxAscii) {
                detectedFields.add(
                    DetectedField(
                        fieldName = "Mã vật tư (REF)",
                        searchValue = cleanRef,
                        encoding = FieldEncoding.BCD_NUMERIC,
                        startByte = idxBcd,
                        lengthBytes = refBcd.size,
                        matchedHex = IcodeSlixDataFormatter.bytesToHex(refBcd),
                        description = "Khớp dạng nén BCD (${refBcd.size} bytes) tại Byte $idxBcd (Block ${idxBcd / 4})"
                    )
                )
            }
        }

        // 2. Quét tìm số LOT
        val cleanLot = targetLot.trim()
        if (cleanLot.isNotEmpty()) {
            // 2.1 ASCII Match
            val lotAscii = cleanLot.toByteArray(StandardCharsets.US_ASCII)
            val idxLotAscii = indexOfBytes(rawBytes, lotAscii)
            if (idxLotAscii >= 0) {
                detectedFields.add(
                    DetectedField(
                        fieldName = "Số LOT",
                        searchValue = cleanLot,
                        encoding = FieldEncoding.ASCII_TEXT,
                        startByte = idxLotAscii,
                        lengthBytes = lotAscii.size,
                        matchedHex = IcodeSlixDataFormatter.bytesToHex(lotAscii),
                        description = "Tìm thấy dạng ASCII từ Byte $idxLotAscii (Block ${idxLotAscii / 4})"
                    )
                )
            }

            // 2.2 BCD Match
            val lotBcd = stringToBcd(cleanLot, padLeftZero = false)
            val idxLotBcd = indexOfBytes(rawBytes, lotBcd)
            if (idxLotBcd >= 0 && idxLotBcd != idxLotAscii) {
                detectedFields.add(
                    DetectedField(
                        fieldName = "Số LOT",
                        searchValue = cleanLot,
                        encoding = FieldEncoding.BCD_NUMERIC,
                        startByte = idxLotBcd,
                        lengthBytes = lotBcd.size,
                        matchedHex = IcodeSlixDataFormatter.bytesToHex(lotBcd),
                        description = "Khớp dạng nén BCD (${lotBcd.size} bytes) tại Byte $idxLotBcd (Block ${idxLotBcd / 4})"
                    )
                )
            }

            // 2.3 Integer 32-bit Match (Big-Endian & Little-Endian)
            val lotLong = cleanLot.filter { it.isDigit() }.toLongOrNull()
            if (lotLong != null) {
                val beBytes = intTo4Bytes(lotLong, isBigEndian = true)
                val idxBe = indexOfBytes(rawBytes, beBytes)
                if (idxBe >= 0) {
                    detectedFields.add(
                        DetectedField(
                            fieldName = "Số LOT",
                            searchValue = cleanLot,
                            encoding = FieldEncoding.INTEGER_BIG_ENDIAN,
                            startByte = idxBe,
                            lengthBytes = 4,
                            matchedHex = IcodeSlixDataFormatter.bytesToHex(beBytes),
                            description = "Khớp số nguyên 32-bit Big-Endian (0x${IcodeSlixDataFormatter.bytesToHex(beBytes)}) tại Block ${idxBe / 4}"
                        )
                    )
                }

                val leBytes = intTo4Bytes(lotLong, isBigEndian = false)
                val idxLe = indexOfBytes(rawBytes, leBytes)
                if (idxLe >= 0 && idxLe != idxBe) {
                    detectedFields.add(
                        DetectedField(
                            fieldName = "Số LOT",
                            searchValue = cleanLot,
                            encoding = FieldEncoding.INTEGER_LITTLE_ENDIAN,
                            startByte = idxLe,
                            lengthBytes = 4,
                            matchedHex = IcodeSlixDataFormatter.bytesToHex(leBytes),
                            description = "Khớp số nguyên 32-bit Little-Endian (0x${IcodeSlixDataFormatter.bytesToHex(leBytes)}) tại Block ${idxLe / 4}"
                        )
                    )
                }
            }
        }

        // 3. Quét tìm Hạn sử dụng (EXP)
        val cleanExp = targetExp.trim()
        if (cleanExp.isNotEmpty()) {
            // 3.1 ASCII Match (2027-02-28 hoặc 20270228)
            val expAscii = cleanExp.toByteArray(StandardCharsets.US_ASCII)
            val idxExpAscii = indexOfBytes(rawBytes, expAscii)
            if (idxExpAscii >= 0) {
                detectedFields.add(
                    DetectedField(
                        fieldName = "Hạn dùng (EXP)",
                        searchValue = cleanExp,
                        encoding = FieldEncoding.ASCII_TEXT,
                        startByte = idxExpAscii,
                        lengthBytes = expAscii.size,
                        matchedHex = IcodeSlixDataFormatter.bytesToHex(expAscii),
                        description = "Tìm thấy dạng chuỗi ASCII ngày tháng tại Byte $idxExpAscii"
                    )
                )
            }

            // 3.2 BCD Date Match (ví dụ 20270228 -> 4 bytes 0x20 0x27 0x02 0x28 hoặc 270228 -> 3 bytes)
            val digitsOnly = cleanExp.filter { it.isDigit() }
            if (digitsOnly.length in 6..8) {
                val bcdDate = stringToBcd(digitsOnly, padLeftZero = false)
                val idxDateBcd = indexOfBytes(rawBytes, bcdDate)
                if (idxDateBcd >= 0 && idxDateBcd != idxExpAscii) {
                    detectedFields.add(
                        DetectedField(
                            fieldName = "Hạn dùng (EXP)",
                            searchValue = cleanExp,
                            encoding = FieldEncoding.DATE_BCD,
                            startByte = idxDateBcd,
                            lengthBytes = bcdDate.size,
                            matchedHex = IcodeSlixDataFormatter.bytesToHex(bcdDate),
                            description = "Khớp ngày dạng BCD (${IcodeSlixDataFormatter.bytesToHex(bcdDate)}) tại Byte $idxDateBcd (Block ${idxDateBcd / 4})"
                        )
                    )
                }
            }
        }

        // 4. Kiểm tra mã UID có được nhúng vào bộ nhớ không
        var uidMatch = false
        val cleanUid = uidHex.trim().replace(" ", "").replace(":", "").uppercase()
        if (cleanUid.length == 16) {
            val uidBytes = IcodeSlixDataFormatter.hexToBytes(cleanUid)
            val idxUid = indexOfBytes(rawBytes, uidBytes)
            if (idxUid >= 0) {
                uidMatch = true
                detectedFields.add(
                    DetectedField(
                        fieldName = "Mã UID Chip",
                        searchValue = cleanUid,
                        encoding = FieldEncoding.ASCII_TEXT,
                        startByte = idxUid,
                        lengthBytes = 8,
                        matchedHex = cleanUid,
                        description = "Phát hiện 8-Byte UID được nhúng trực tiếp tại Block ${idxUid / 4} (Cơ chế chống nhân bản)"
                    )
                )
            }
        }

        // 5. Tính toán Checksum CRC16 & CRC32 của payload (bỏ qua các block cuối nếu cần)
        val crc16Val = if (rawBytes.isNotEmpty()) calculateCrc16Iso15693(rawBytes) else 0
        val crc16Hex = "%04X".format(crc16Val)
        val crc32Val = if (rawBytes.isNotEmpty()) calculateCrc32(rawBytes) else 0L
        val crc32Hex = "%08X".format(crc32Val)

        // 6. Xây dựng bản đồ phân loại 28 Blocks (Memory Map)
        val blockCategories = Array(IcodeSlixDataFormatter.TOTAL_BLOCKS) { BlockCategory.BLANK_SPACE }

        for (b in 0 until IcodeSlixDataFormatter.TOTAL_BLOCKS) {
            val startB = b * 4
            val endB = startB + 3
            if (endB < rawBytes.size) {
                val blockBytes = rawBytes.sliceArray(startB..endB)
                val hexStr = IcodeSlixDataFormatter.bytesToHex(blockBytes)
                if (hexStr == "20202020" || hexStr == "00000000" || hexStr == "FFFFFFFF") {
                    blockCategories[b] = BlockCategory.BLANK_SPACE
                } else {
                    blockCategories[b] = BlockCategory.CUSTOM_DATA
                }
            }
        }

        // Gán phân loại theo các trường đã phát hiện
        for (field in detectedFields) {
            val category = when (field.fieldName) {
                "Mã vật tư (REF)" -> BlockCategory.MATERIAL_REF
                "Số LOT" -> BlockCategory.LOT_NUMBER
                "Hạn dùng (EXP)" -> BlockCategory.EXPIRATION
                "Mã UID Chip" -> BlockCategory.CHECKSUM
                else -> BlockCategory.CUSTOM_DATA
            }
            for (blk in field.startBlock..minOf(field.endBlock, IcodeSlixDataFormatter.TOTAL_BLOCKS - 1)) {
                blockCategories[blk] = category
            }
        }

        val summary = if (detectedFields.isNotEmpty()) {
            "Phát hiện thành công ${detectedFields.size} trường dữ liệu khớp từ thẻ mẫu!"
        } else {
            "Chưa nhận diện được cấu trúc tương ứng với nhãn. Có thể thẻ sử dụng chuẩn mã hóa độc quyền hoặc thẻ trống."
        }

        return ReverseEngineerReport(
            rawHex = cleanHex,
            totalBytes = totalBytes,
            detectedFields = detectedFields,
            blockCategories = blockCategories.toList(),
            crc16Iso15693 = crc16Hex,
            crc32Hex = crc32Hex,
            uidMatchFound = uidMatch,
            summaryMessage = summary
        )
    }

    /**
     * Dữ liệu thẻ mẫu thực tế nạp sẵn (Presets).
     */
    object Presets {
        /**
         * Mẫu 1: Dạng cấu trúc chuỗi ASCII mở rộng (REF:09015051190|LOT:93077101|EXP:2027-02-28).
         * Gồm 11 Blocks có dữ liệu, 17 Blocks đệm 0x20.
         */
        val ROCHE_SYPHILIS_ASCII_HEX: String by lazy {
            val payload = "REF:09015051190|LOT:93077101|EXP:2027-02-28"
            val blocks = IcodeSlixDataFormatter.formatTextToBlocks(payload)
            val fullBlocks = (0 until 28).map { i ->
                if (i < blocks.size) blocks[i] else "20202020"
            }
            fullBlocks.joinToString("")
        }

        /**
         * Mẫu 2: Dạng cấu trúc Binary / BCD chuẩn thiết bị y tế (Roche / Abbott style):
         * - Block 0-2 (12 Bytes): Mã REF dạng BCD ("090150511900") -> 09 01 50 51 19 00 ...
         * - Block 3 (4 Bytes): Số LOT dạng BCD ("93077101") -> 93 07 71 01
         * - Block 4 (4 Bytes): Hạn dùng dạng BCD ("20270228") -> 20 27 02 28
         * - Block 5 (4 Bytes): Số lượt test còn lại (100 tests = 0x00000064)
         * - Block 6..26: Các tham số hiệu chuẩn máy
         * - Block 27 (4 Bytes): Checksum CRC16 + Lock byte
         */
        val ROCHE_BINARY_BCD_HEX: String by lazy {
            val sb = StringBuilder()
            // Block 0: BCD REF part 1: 09 01 50 51
            sb.append("09015051")
            // Block 1: BCD REF part 2: 19 00 00 00
            sb.append("19000000")
            // Block 2: Reagent Cassette Type: A1 B2 00 01
            sb.append("A1B20001")
            // Block 3: BCD LOT: 93 07 71 01
            sb.append("93077101")
            // Block 4: BCD EXP: 20 27 02 28
            sb.append("20270228")
            // Block 5: Tests Remaining (100 tests = 0x00000064)
            sb.append("00000064")
            // Block 6..26: Calibration & Parameter Blocks
            for (i in 6..26) {
                sb.append("00000000")
            }
            // Block 27: CRC16 (ví dụ A4F2) + Status 0100
            sb.append("A4F20100")
            sb.toString()
        }

        /**
         * Mẫu 3: Thẻ trắng sạch (28 Blocks toàn khoảng trắng 0x20202020)
         */
        val BLANK_TAG_HEX: String = "20202020".repeat(28)
    }
}
