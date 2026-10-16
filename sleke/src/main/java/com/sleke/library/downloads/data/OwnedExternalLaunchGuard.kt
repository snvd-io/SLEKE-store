package com.sleke.library.downloads.data

import com.sleke.library.downloads.domain.DownloadOwner

/** Runs on Main, without suspension or disk access, at the external launch boundary. */
internal fun runOwnedExternalLaunch(
    owner: DownloadOwner,
    access: DownloadAccessSource,
    cancelled: () -> Boolean,
    launch: () -> Unit
): AccessDecision {
    val decision = if (cancelled()) AccessDecision.REVOKED else access.check(owner)
    if (decision == AccessDecision.ALLOWED) launch()
    return decision
}
