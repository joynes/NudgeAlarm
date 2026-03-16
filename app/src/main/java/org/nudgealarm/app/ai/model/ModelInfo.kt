package org.nudgealarm.app.ai.model

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val huggingFaceUrl: String,
    val filename: String,
    /** True if this model requires a HuggingFace access token to download. */
    val requiresHfToken: Boolean = false,
    /** URL of the HuggingFace model page where the user accepts the license. */
    val licenseUrl: String? = null
) {
    val sizeMb: Int get() = (sizeBytes / (1024 * 1024)).toInt()
}
