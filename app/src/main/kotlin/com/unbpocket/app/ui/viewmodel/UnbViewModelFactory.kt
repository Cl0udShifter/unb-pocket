package com.unbpocket.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.unbpocket.core.model.contract.UnbPocketCore

class UnbViewModelFactory(
    private val core: UnbPocketCore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(core) as T
        modelClass.isAssignableFrom(RuViewModel::class.java) -> RuViewModel(core) as T
        modelClass.isAssignableFrom(TurmasViewModel::class.java) -> TurmasViewModel(core) as T
        modelClass.isAssignableFrom(DiscenteViewModel::class.java) -> DiscenteViewModel(core) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
