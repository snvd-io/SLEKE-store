package com.sleke.library.ui

sealed interface SimpleAppUiState {
  object NotDownloaded : SimpleAppUiState
  data class Downloading(val progress: Int) : SimpleAppUiState

  /**
   * Download finished, but not yet installed.
   * @param apkUri content:// URI to pass to Intent
   * @param packageName the real package name you extracted
   */
  data class Downloaded(
    val apkUri: String,
    val packageName: String
  ) : SimpleAppUiState

  data object Installed : SimpleAppUiState
  data class Error(val message: String) : SimpleAppUiState
}