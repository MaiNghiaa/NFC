package com.example.nfc

import com.example.nfc.data.BlockCategory
import com.example.nfc.data.FieldEncoding
import com.example.nfc.data.SampleCardReverseEngineer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleCardReverseEngineerTest {

    @Test
    fun testStringToBcdConversion() {
        // "09015051190" (11 digits, odd) -> pad left zero -> "009015051190" -> 6 bytes
        val bcd1 = SampleCardReverseEngineer.stringToBcd("09015051190", padLeftZero = true)
        assertEquals(6, bcd1.size)
        assertEquals("009015051190", com.example.nfc.data.IcodeSlixDataFormatter.bytesToHex(bcd1))

        // "93077101" (8 digits, even) -> 4 bytes: 93 07 71 01
        val bcd2 = SampleCardReverseEngineer.stringToBcd("93077101", padLeftZero = false)
        assertEquals(4, bcd2.size)
        assertEquals("93077101", com.example.nfc.data.IcodeSlixDataFormatter.bytesToHex(bcd2))
    }

    @Test
    fun testIntTo4BytesEndianness() {
        val value = 93077101L // Hex: 0x058C3E6D -> Bytes: [05, 8C, 3E, 6D]
        val be = SampleCardReverseEngineer.intTo4Bytes(value, isBigEndian = true)
        assertEquals("058C3E6D", com.example.nfc.data.IcodeSlixDataFormatter.bytesToHex(be))

        val le = SampleCardReverseEngineer.intTo4Bytes(value, isBigEndian = false)
        assertEquals("6D3E8C05", com.example.nfc.data.IcodeSlixDataFormatter.bytesToHex(le))
    }

    @Test
    fun testCrc16Iso15693() {
        val testData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val crc = SampleCardReverseEngineer.calculateCrc16Iso15693(testData)
        assertTrue(crc in 0..0xFFFF)
    }

    @Test
    fun testAnalyzeRocheAsciiPreset() {
        val asciiHex = SampleCardReverseEngineer.Presets.ROCHE_SYPHILIS_ASCII_HEX
        val report = SampleCardReverseEngineer.analyzePayload(
            rawHexInput = asciiHex,
            targetRef = "09015051190",
            targetLot = "93077101",
            targetExp = "2027-02-28"
        )

        assertEquals(112, report.totalBytes)
        // Phải phát hiện được cả 3 trường dạng ASCII
        val refMatch = report.detectedFields.find { it.fieldName == "Mã vật tư (REF)" }
        val lotMatch = report.detectedFields.find { it.fieldName == "Số LOT" }
        val expMatch = report.detectedFields.find { it.fieldName == "Hạn dùng (EXP)" }

        assertNotNull(refMatch)
        assertEquals(FieldEncoding.ASCII_TEXT, refMatch?.encoding)
        assertNotNull(lotMatch)
        assertEquals(FieldEncoding.ASCII_TEXT, lotMatch?.encoding)
        assertNotNull(expMatch)
        assertEquals(FieldEncoding.ASCII_TEXT, expMatch?.encoding)

        // Kiểm tra block categories
        assertTrue(report.blockCategories.contains(BlockCategory.MATERIAL_REF))
        assertTrue(report.blockCategories.contains(BlockCategory.LOT_NUMBER))
        assertTrue(report.blockCategories.contains(BlockCategory.EXPIRATION))
    }

    @Test
    fun testAnalyzeRocheBinaryBcdPreset() {
        val bcdHex = SampleCardReverseEngineer.Presets.ROCHE_BINARY_BCD_HEX
        val report = SampleCardReverseEngineer.analyzePayload(
            rawHexInput = bcdHex,
            targetRef = "09015051", // BCD part
            targetLot = "93077101",
            targetExp = "20270228"
        )

        val lotMatch = report.detectedFields.find { it.fieldName == "Số LOT" }
        val expMatch = report.detectedFields.find { it.fieldName == "Hạn dùng (EXP)" }

        assertNotNull(lotMatch)
        assertEquals(FieldEncoding.BCD_NUMERIC, lotMatch?.encoding)
        assertEquals(3, lotMatch?.startBlock) // Block 3 chứa LOT BCD: 93 07 71 01

        assertNotNull(expMatch)
        assertEquals(FieldEncoding.DATE_BCD, expMatch?.encoding)
        assertEquals(4, expMatch?.startBlock) // Block 4 chứa EXP BCD: 20 27 02 28
    }

    @Test
    fun testBlankCardDetection() {
        val blankHex = SampleCardReverseEngineer.Presets.BLANK_TAG_HEX
        val report = SampleCardReverseEngineer.analyzePayload(
            rawHexInput = blankHex,
            targetRef = "09015051190",
            targetLot = "93077101",
            targetExp = "2027-02-28"
        )

        assertTrue(report.detectedFields.isEmpty())
        assertTrue(report.blockCategories.all { it == BlockCategory.BLANK_SPACE })
    }
}
