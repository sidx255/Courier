package ca.pkay.rcloneexplorer.workmanager

internal object RcloneDiagnosticLine {
    fun extract(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val normalized = trimmed.lowercase()
        return trimmed.takeIf {
            normalized.contains(" error ") ||
                    normalized.contains("error:") ||
                    normalized.contains("fatal") ||
                    normalized.contains("critical") ||
                    normalized.contains("failed")
        }
    }
}