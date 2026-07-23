package ca.pkay.rcloneexplorer.workmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RcloneDiagnosticLineTest {

    @Test
    fun ignoresVerboseStartupConfiguration() {
        assertNull(RcloneDiagnosticLine.extract("2026/07/23 DEBUG : Setting --transfers from environment"))
    }

    @Test
    fun keepsRawRcloneErrors() {
        val line = "2026/07/23 ERROR : Failed to copy: network is unreachable"

        assertEquals(line, RcloneDiagnosticLine.extract(line))
    }
}