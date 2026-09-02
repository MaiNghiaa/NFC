package com.example.nfc

import com.example.nfc.data.IcodeSlixDataFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcodeSlixDataFormatterTest {

    @Test
    fun testChunkingAndPadding() {
        // Chuỗi 5 ký tự: "HELLO" (5 bytes ASCII)
        // Block 0: 'H','E','L','L' -> 48 45 4C 4C
        // Block 1: 'O', ' ', ' ', ' ' -> 4F 20 20 20 (Đệm 3 byte 0x20)
        val text = "HELLO"
        val blocks = IcodeSlixDataFormatter.formatTextToBlocks(text)

        assertEquals(2, blocks.size)
        assertEquals("48454C4C", blocks[0])
        assertEquals("4F202020", blocks[1])

        // Giải mã ngược lại
        val decoded = IcodeSlixDataFormatter.parseBlocksToText(blocks)
        assertEquals("HELLO", decoded)
    }

    @Test
    fun testExactMultipleOfFour() {
        // Chuỗi 8 ký tự: "TEST1234" (đúng 2 blocks, không cần padding)
        val text = "TEST1234"
        val blocks = IcodeSlixDataFormatter.formatTextToBlocks(text)

        assertEquals(2, blocks.size)
        val decoded = IcodeSlixDataFormatter.parseBlocksToText(blocks)
        assertEquals("TEST1234", decoded)
    }

    @Test
    fun testRocheSyphilisPayload() {
        // Dữ liệu mẫu từ ảnh 1
        val payload = IcodeSlixDataFormatter.buildMedicalPayload(
            materialRef = "09015051190",
            lot = "93077101",
            exp = "2027-02-28"
        )
        // "REF:09015051190|LOT:93077101|EXP:2027-02-28" -> 44 bytes = 11 blocks exactly
        val blocks = IcodeSlixDataFormatter.formatTextToBlocks(payload)
        assertTrue(blocks.size <= 28) // Phải nằm trong giới hạn 28 blocks

        val decoded = IcodeSlixDataFormatter.parseBlocksToText(blocks)
        assertEquals(payload, decoded)

        val parsed = IcodeSlixDataFormatter.parseMedicalPayload(decoded)
        assertEquals("09015051190", parsed?.materialRef)
        assertEquals("93077101", parsed?.lot)
        assertEquals("2027-02-28", parsed?.exp)
    }

    @Test
    fun testUidValidation() {
        // UID mẫu từ ảnh 2: 0xE0040153238F7F40
        val sampleUid = "E0040153238F7F40"
        assertTrue(IcodeSlixDataFormatter.isNxpIcodeSlix(sampleUid))
        assertEquals("E0:04:01:53:23:8F:7F:40", IcodeSlixDataFormatter.formatPrettyUid(sampleUid))

        // UID không hợp lệ (ví dụ chip khác hoặc ISO khác)
        assertFalse(IcodeSlixDataFormatter.isNxpIcodeSlix("E0020153238F7F40")) // Không phải NXP (04)
        assertFalse(IcodeSlixDataFormatter.isNxpIcodeSlix("12345678")) // Sai độ dài
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPayloadExceeds112Bytes() {
        // Tạo chuỗi 113 ký tự (> 112 bytes)
        val longPayload = "A".repeat(113)
        IcodeSlixDataFormatter.formatTextToBlocks(longPayload)
    }

    @Test
    fun testEvaluateTagStatus() {
        // 1. Thẻ trắng (toàn 0x20 hoặc 0x00)
        val blankBlocks = (0 until 28).map {
            com.example.nfc.data.MemoryBlock(it, "20202020", "    ")
        }
        assertEquals(IcodeSlixDataFormatter.TagDataStatus.EMPTY_TAG, IcodeSlixDataFormatter.evaluateTagStatus(blankBlocks))

        val zeroBlocks = (0 until 28).map {
            com.example.nfc.data.MemoryBlock(it, "00000000", "....")
        }
        assertEquals(IcodeSlixDataFormatter.TagDataStatus.EMPTY_TAG, IcodeSlixDataFormatter.evaluateTagStatus(zeroBlocks))

        // 2. Thẻ có dữ liệu y tế (bắt đầu bằng REF:)
        val payload = "REF:09015051190|LOT:93077101|EXP:2027-02-28"
        val hexBlocks = IcodeSlixDataFormatter.formatTextToBlocks(payload)
        val medicalBlocks = (0 until 28).map { i ->
            if (i < hexBlocks.size) com.example.nfc.data.MemoryBlock(i, hexBlocks[i])
            else com.example.nfc.data.MemoryBlock(i, "20202020")
        }
        assertEquals(IcodeSlixDataFormatter.TagDataStatus.VALID_MEDICAL_DATA, IcodeSlixDataFormatter.evaluateTagStatus(medicalBlocks))

        // 3. Thẻ có dữ liệu chuỗi tự do
        val customText = "HELLO WORLD"
        val customHex = IcodeSlixDataFormatter.formatTextToBlocks(customText)
        val customBlocks = (0 until 28).map { i ->
            if (i < customHex.size) com.example.nfc.data.MemoryBlock(i, customHex[i])
            else com.example.nfc.data.MemoryBlock(i, "20202020")
        }
        assertEquals(IcodeSlixDataFormatter.TagDataStatus.CUSTOM_PAYLOAD, IcodeSlixDataFormatter.evaluateTagStatus(customBlocks))
    }
}

