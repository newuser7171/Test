package com.abhishek.zerodroid.features.ir.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.abhishek.zerodroid.features.ir.domain.FlipperIrParser
import com.abhishek.zerodroid.features.ir.domain.IrProtocol
import com.abhishek.zerodroid.features.ir.domain.IrRemoteButton
import com.abhishek.zerodroid.features.ir.domain.IrRemoteProfile
import com.abhishek.zerodroid.features.ir.domain.IrRemoteState
import com.abhishek.zerodroid.features.ir.domain.IrScreenTab
import com.abhishek.zerodroid.features.ir.domain.IrSignal
import com.abhishek.zerodroid.features.ir.domain.IrTransmitter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.abhishek.zerodroid.core.debug.DemoDataBus
import com.abhishek.zerodroid.core.debug.DemoData
import com.abhishek.zerodroid.core.debug.observeDemoRequests
import com.abhishek.zerodroid.features.ir.domain.IrRemoteDatabase

@HiltViewModel
class IrViewModel @Inject constructor(
    private val irTransmitter: IrTransmitter,
    private val demoBus: DemoDataBus
) : ViewModel() {

    private val _state = MutableStateFlow(IrRemoteState(isIrAvailable = irTransmitter.isAvailable))
    val state: StateFlow<IrRemoteState> = _state.asStateFlow()

    private var sending = false

    fun refreshHardware() {
        _state.value = _state.value.copy(isIrAvailable = irTransmitter.isAvailable)
    }

    fun requestUsbPermission() {
        irTransmitter.requestUsbPermission()
        refreshHardware()
    }

    private fun sendAsync(signal: IrSignal) {
        if (sending) return
        sending = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { irTransmitter.transmit(signal) }
                _state.value = _state.value.copy(lastTransmitResult = result, isIrAvailable = irTransmitter.isAvailable)
            } finally { sending = false }
        }
    }

    fun setActiveTab(tab: IrScreenTab) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    fun setProtocol(protocol: IrProtocol) {
        _state.value = _state.value.copy(selectedProtocol = protocol, frequency = protocol.defaultFrequency)
    }

    fun setFrequency(freq: Int) { _state.value = _state.value.copy(frequency = freq) }
    fun setCode(code: String) { _state.value = _state.value.copy(code = code) }

    fun transmit() {
        val s = _state.value
        val signal = IrSignal(protocol = s.selectedProtocol, frequency = s.frequency, code = s.code)
        sendAsync(signal)
    }

    fun transmitSignal(signal: IrSignal) {
        sendAsync(signal)
    }

    fun importFlipperFile(content: String) {
        val signals = FlipperIrParser.parse(content)
        _state.value = _state.value.copy(importedSignals = signals)
    }

    fun selectProfile(profile: IrRemoteProfile) {
        _state.value = _state.value.copy(selectedProfile = profile)
    }

    fun transmitRemoteButton(button: IrRemoteButton) {
        val signal = IrSignal(
            protocol = button.protocol, frequency = button.frequency,
            code = button.code.toString(16).uppercase(), name = button.label
        )
        sendAsync(signal)
    }

    init {
        observeDemoRequests(demoBus, DemoData.Routes.IR) { loadDemoData() }
    }

    /** Debug-only: replaces live state with [DemoData] so the populated UI can be verified without hardware. */
    private fun loadDemoData() {
        _state.value = _state.value.copy(
            isIrAvailable = true,
            selectedProfile = IrRemoteDatabase.profiles.first(),
            importedSignals = DemoData.irSignals,
            code = "20DF10EF"
        )
    }
}
