package com.streamflixvip.app.ui.vip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.RedeemResult
import com.streamflixvip.app.data.VipRepository
import com.streamflixvip.app.data.VipStatusHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VipUiState(
    val userId: String = "",
    val isVip: Boolean = false,
    val expiresAt: String? = null,
    val planLabel: String? = null,
    val isLoadingStatus: Boolean = true,
    val redeemCode: String = "",
    val isRedeeming: Boolean = false,
    val redeemMessage: String? = null,
    val redeemSuccess: Boolean = false,
    // Estado do pagamento
    val showPaymentSheet: Boolean = false,
    val paymentAmount: Double = 0.0,
    val paymentLabel: String = "",
    val paymentDuration: Int = 0,
)

class VipViewModel(
    private val userId: String,
    private val repository: VipRepository = VipRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(VipUiState())
    val uiState: StateFlow<VipUiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(userId = userId)
        refreshStatus()
    }

    fun startPayment(amount: Double, label: String, duration: Int) {
        _uiState.value = _uiState.value.copy(
            showPaymentSheet = true,
            paymentAmount = amount,
            paymentLabel = label,
            paymentDuration = duration
        )
    }

    fun dismissPayment() {
        _uiState.value = _uiState.value.copy(showPaymentSheet = false)
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStatus = true)
            val status = repository.getStatus(userId)
            _uiState.value = _uiState.value.copy(
                isVip = status.isVip,
                expiresAt = status.expiresAt,
                planLabel = status.planLabel,
                isLoadingStatus = false,
            )
            VipStatusHolder.update(status.isVip)
        }
    }

    fun onCodeChange(value: String) {
        _uiState.value = _uiState.value.copy(redeemCode = value, redeemMessage = null)
    }

    fun redeem() {
        val code = _uiState.value.redeemCode.trim()
        if (code.isEmpty()) {
            _uiState.value = _uiState.value.copy(redeemMessage = "Digite um código.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRedeeming = true, redeemMessage = null)
            when (val result = repository.redeemCode(code, userId)) {
                is RedeemResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isRedeeming = false,
                        isVip = true,
                        expiresAt = result.expiresAt,
                        planLabel = result.planLabel,
                        redeemMessage = "✅ VIP ativado com sucesso!",
                        redeemSuccess = true,
                        redeemCode = "",
                    )
                    VipStatusHolder.update(true)
                }
                is RedeemResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isRedeeming = false,
                        redeemMessage = "❌ ${result.message}",
                        redeemSuccess = false,
                    )
                }
            }
        }
    }
}
