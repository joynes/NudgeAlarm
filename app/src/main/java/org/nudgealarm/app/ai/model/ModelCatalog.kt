package org.nudgealarm.app.ai.model

/**
 * Catalog of models compatible with MediaPipe LLM Inference (Tasks GenAI).
 *
 * IMPORTANT: MediaPipe requires models in its own binary format (.bin / TFLite-based).
 * Standard GGUF or safetensors files will NOT work.
 *
 * Gemma models are gated on HuggingFace — you must accept the license at
 * https://huggingface.co/google/gemma-2b-it-cpu-int4 and provide your HF access token
 * in the download screen.
 */
object ModelCatalog {
    val models = listOf(
        ModelInfo(
            id = "gemma3_1b",
            name = "Gemma 3 1B Instruct (INT4)",
            description = "Google's Gemma 3 1B, INT4 quantized for MediaPipe. ~0.6 GB.",
            sizeBytes = 650_000_000L,
            huggingFaceUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            filename = "gemma3-1b-it-int4.task",
            requiresHfToken = true,
            licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT"
        ),
        ModelInfo(
            id = "gemma2_2b",
            name = "Gemma 2 2B Instruct (Q8)",
            description = "Google's Gemma 2 2B, Q8 quantized for MediaPipe. ~2.5 GB.",
            sizeBytes = 2_500_000_000L,
            huggingFaceUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/gemma2_q8_multi-prefill-seq_ekv1280.task",
            filename = "gemma2-2b-it-q8.task",
            requiresHfToken = true,
            licenseUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT"
        )
    )

    fun findById(id: String): ModelInfo? = models.find { it.id == id }
}
