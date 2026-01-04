package com.httppal.model

/**
 * Represents a single entry in multipart/form-data
 * Can be either a text field or a file upload
 */
data class FormDataEntry(
    val key: String,
    val value: String,
    val isFile: Boolean = false,
    val contentType: String? = null,
    val fileName: String? = null  // Original filename for file uploads
) {
    companion object {
        /**
         * Create a text form field
         */
        fun text(key: String, value: String): FormDataEntry {
            return FormDataEntry(key = key, value = value, isFile = false)
        }
        
        /**
         * Create a file form field
         */
        fun file(key: String, filePath: String, contentType: String? = null): FormDataEntry {
            val file = java.io.File(filePath)
            return FormDataEntry(
                key = key,
                value = filePath,
                isFile = true,
                contentType = contentType ?: detectContentType(filePath),
                fileName = file.name
            )
        }
        
        /**
         * Detect content type from file extension
         */
        private fun detectContentType(filePath: String): String {
            val extension = filePath.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                "pdf" -> "application/pdf"
                "json" -> "application/json"
                "xml" -> "application/xml"
                "txt" -> "text/plain"
                "html", "htm" -> "text/html"
                "css" -> "text/css"
                "js" -> "application/javascript"
                "zip" -> "application/zip"
                "tar" -> "application/x-tar"
                "gz", "gzip" -> "application/gzip"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> "application/vnd.ms-excel"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "csv" -> "text/csv"
                "mp3" -> "audio/mpeg"
                "mp4" -> "video/mp4"
                "avi" -> "video/x-msvideo"
                else -> "application/octet-stream"
            }
        }
    }
    
    /**
     * Validate the entry
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (key.isBlank()) {
            errors.add("Form field key cannot be empty")
        }
        
        if (isFile) {
            if (value.isBlank()) {
                errors.add("File path cannot be empty for key: $key")
            } else {
                val file = java.io.File(value)
                if (!file.exists()) {
                    errors.add("File not found: $value")
                } else if (!file.canRead()) {
                    errors.add("Cannot read file: $value")
                }
            }
        }
        
        return errors
    }
}
