package com.aurora.store.util

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

object FragmentThemeUtils {
    
    private var previousActionBarState: Boolean? = null
    
    fun Fragment.hideActionBar() {
        val supportActionBar = (activity as? AppCompatActivity)?.supportActionBar
        if (supportActionBar != null) {
            previousActionBarState = supportActionBar.isShowing
            supportActionBar.hide()
        }
    }
    
    fun Fragment.showActionBar() {
        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }
    
    fun Fragment.restoreActionBarState() {
        previousActionBarState?.let { wasShowing ->
            if (wasShowing) {
                showActionBar()
            } else {
                (activity as? AppCompatActivity)?.supportActionBar?.hide()
            }
        }
        previousActionBarState = null
    }
    
    fun Fragment.setActionBarVisibility(visible: Boolean) {
        if (visible) {
            showActionBar()
        } else {
            hideActionBar()
        }
    }
    
    fun Fragment.hideActionBarForThisFragmentOnly() {
        hideActionBar()
    }
    
    fun Fragment.restoreActionBarOnExit() {
        restoreActionBarState()
    }
} 