package ca.pkay.rcloneexplorer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneErrorMapperTest {

    @Test
    fun detectsMissingSourceDirectory() {
        assertTrue(
            RcloneErrorMapper.isSourceMissing(
                "Local file system at /storage/emulated/0/DCIM/Videocaptures - error reading source root directory: directory not found"
            )
        )
        assertTrue(RcloneErrorMapper.isSourceMissing("directory not found"))
        assertFalse(RcloneErrorMapper.isSourceMissing("3 files differ"))
        assertFalse(RcloneErrorMapper.isSourceMissing(null))
    }

    @Test
    fun mapsAuthenticationToConnectionEditing() {
        assertEquals(
            RcloneErrorMapper.Action.EDIT_CONNECTION,
            RcloneErrorMapper.classify("NT_STATUS_LOGON_FAILURE: authentication failed")
        )
    }

    @Test
    fun mapsRoutesAndTimeoutsToNetworkAction() {
        assertEquals(
            RcloneErrorMapper.Action.CHECK_NETWORK,
            RcloneErrorMapper.classify("dial tcp: no route to host")
        )
        assertEquals(
            RcloneErrorMapper.Action.CHECK_NETWORK,
            RcloneErrorMapper.classify("i/o timeout")
        )
    }

    @Test
    fun mapsOfflineToRetry() {
        assertEquals(
            RcloneErrorMapper.Action.RETRY,
            RcloneErrorMapper.classify("No network connection")
        )
    }

    @Test
    fun mapsHashlessAndDifferencesWithoutConflatingThem() {
        assertEquals(
            RcloneErrorMapper.Action.NONE,
            RcloneErrorMapper.classify("No common hash found")
        )
        assertEquals(
            RcloneErrorMapper.Action.REPAIR,
            RcloneErrorMapper.classify("3 files differ")
        )
    }
}