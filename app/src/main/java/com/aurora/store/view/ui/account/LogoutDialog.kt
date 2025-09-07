package com.aurora.store.view.ui.account

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aurora.store.R
import com.aurora.store.usecase.LogoutUseCase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class LogoutDialog : DialogFragment() {

    @Inject
    lateinit var logoutUseCase: LogoutUseCase

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_logout_confirmation_title)
            .setMessage(R.string.action_logout_confirmation_message)
            .setPositiveButton(getString(android.R.string.ok), null)
            .setNegativeButton(getString(android.R.string.cancel)) { _, _ -> dialog?.dismiss() }
            .create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? AlertDialog
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            logout()
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            runCatching {
                logoutUseCase.invoke()
            }.onSuccess {
                dialog?.dismiss()
                findNavController().navigate(
                    LogoutDialogDirections.actionLogoutDialogToSplashFragment()
                )
            }.onFailure {
                Timber.tag("LogoutDialog")
                    .e(it, "Failed to logout user")
                dialog?.dismiss()
            }
        }
    }
}
