package com.example.nfc.data

/**
 * Đại diện cho một Block trong bộ nhớ 112 Bytes của ICODE SLIX (4 Bytes/block).
 */
data class MemoryBlock(
    val blockIndex: Int,
    val hexValue: String = "20202020",
    val asciiValue: String = "    "
)

/**
 * Đại diện cho toàn bộ thông tin của thẻ NXP ICODE SLIX (ISO 15693).
 */
data class IcodeSlixTag(
    val rawUid: String,
    val prettyUid: String = IcodeSlixDataFormatter.formatPrettyUid(rawUid),
    val isSlixValid: Boolean = IcodeSlixDataFormatter.isNxpIcodeSlix(rawUid),
    val blocks: List<MemoryBlock> = (0 until IcodeSlixDataFormatter.TOTAL_BLOCKS).map {
        MemoryBlock(it)
    },
    val decodedText: String = "",
    val medicalData: MedicalMaterialData? = null,
    val dataStatus: IcodeSlixDataFormatter.TagDataStatus = IcodeSlixDataFormatter.evaluateTagStatus(blocks)
)

