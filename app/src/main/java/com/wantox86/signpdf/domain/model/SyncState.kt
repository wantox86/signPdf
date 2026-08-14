package com.wantox86.signpdf.domain.model

import java.time.Instant

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Synced(val at: Instant) : SyncState()
    data class Failed(val message: String) : SyncState()
}
