package ca.pkay.rcloneexplorer.workmanager

enum class SyncOperation {
    TRANSFER,
    VERIFY,
    VERIFY_DEEP,
    REPAIR,
    REPAIR_DEEP
}