package com.example.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobile.container
import com.example.mobile.network.ProfilePhotoStore
import com.example.mobile.repository.AuthRepository
import com.example.mobile.util.normalizePhone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditProfileUiState(
    val userId: Long = 0L,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val photoPath: String? = null,
    val photoVersion: Int = 0,
    val isSaving: Boolean = false,
    val isUpdatingPhoto: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val profilePhotoStore: ProfilePhotoStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private fun initialState(): EditProfileUiState {
        val user = authRepository.session.value?.user
        val userId = user?.id ?: 0L
        return EditProfileUiState(
            userId = userId,
            name = user?.name.orEmpty(),
            email = user?.email.orEmpty(),
            phone = user?.phone.orEmpty(),
            photoPath = if (userId > 0L) profilePhotoStore.getPhotoPath(userId) else null,
            photoVersion = profilePhotoStore.photoUpdates.value
        )
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, error = null, message = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null, message = null) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, error = null, message = null) }

    fun onPhotoPicked(uriString: String) {
        val userId = _uiState.value.userId
        if (userId <= 0L || _uiState.value.isUpdatingPhoto) return
        _uiState.update { it.copy(isUpdatingPhoto = true, error = null, message = null) }
        viewModelScope.launch {
            val savedPath = withContext(ioDispatcher) {
                profilePhotoStore.savePhotoFromUri(userId, uriString)
            }
            if (savedPath != null) {
                _uiState.update {
                    it.copy(
                        photoPath = savedPath,
                        photoVersion = it.photoVersion + 1,
                        isUpdatingPhoto = false,
                        message = "Foto atualizada neste aparelho!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isUpdatingPhoto = false,
                        error = "Não foi possível carregar a imagem selecionada."
                    )
                }
            }
        }
    }

    fun removePhoto() {
        val userId = _uiState.value.userId
        if (userId <= 0L) return
        profilePhotoStore.removePhoto(userId)
        _uiState.update {
            it.copy(
                photoPath = null,
                photoVersion = it.photoVersion + 1,
                message = "Foto removida."
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val cleanName = state.name.trim()
        val cleanEmail = state.email.trim().lowercase()
        val normalizedPhone = normalizePhone(state.phone)

        val validationError = when {
            cleanName.isEmpty() -> "Informe seu nome."
            !cleanEmail.contains("@") || !cleanEmail.contains(".") -> "Informe um e-mail válido."
            normalizedPhone.isNotEmpty() && !normalizedPhone.matches(Regex("""^\+55\d{10,11}$""")) ->
                "Telefone deve ter DDD + 10 ou 11 dígitos."
            else -> null
        }

        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            authRepository.updateProfile(
                name = cleanName,
                email = cleanEmail,
                phone = normalizedPhone.ifEmpty { null }
            )
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            name = updated.name,
                            email = updated.email,
                            phone = updated.phone.orEmpty(),
                            isSaving = false,
                            message = "Perfil atualizado com sucesso!"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = container()
                EditProfileViewModel(c.authRepository, c.profilePhotoStore)
            }
        }
    }
}
