package com.example.nfc.data

/**
 * Đại diện cho dữ liệu vật tư y tế (như mẫu cassette Syphilis của Roche).
 */
data class MedicalMaterialData(
    val materialRef: String,
    val lot: String,
    val exp: String,
    val rawPayload: String = ""
) {
    companion object {
        val SAMPLE_ROCHE = MedicalMaterialData(
            materialRef = "09015051190",
            lot = "93077101",
            exp = "2027-02-28",
            rawPayload = "REF:09015051190|LOT:93077101|EXP:2027-02-28"
        )
    }
}
