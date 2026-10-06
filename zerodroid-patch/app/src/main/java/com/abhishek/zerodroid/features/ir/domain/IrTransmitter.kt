package com.abhishek.zerodroid.features.ir.domain

import android.hardware.ConsumerIrManager

class IrTransmitter(
    private val irManager: ConsumerIrManager?,
    private val usb: ElkSmartUsbTransport
) {
    val isAvailable: Boolean
        get() = irManager?.hasIrEmitter() == true || usb.isAvailable

    fun requestUsbPermission() = usb.requestPermission()

    fun transmit(signal: IrSignal): TransmitResult {

        val pattern = if (signal.protocol == IrProtocol.RAW) {
            signal.rawPattern ?: return TransmitResult.Error("No raw pattern provided")
        } else {
            IrProtocolEncoder.encode(signal.protocol, signal.code)
                ?: return TransmitResult.Error("Invalid code for ${signal.protocol.displayName}")
        }

        if (signal.frequency !in 10000..100000 || pattern.isEmpty() || pattern.size > 4096 ||
            pattern.any { it <= 0 || it > 1000000 } || pattern.sumOf { it.toLong() } > 2000000L) {
            return TransmitResult.Error("Invalid or oversized IR signal")
        }
        if (irManager?.hasIrEmitter() != true) return usb.transmit(signal.frequency, pattern)
        val manager = irManager ?: return TransmitResult.Error("IR service unavailable")
        return try {
            val ranges = manager.carrierFrequencies
            val inRange = ranges?.any { signal.frequency in it.minFrequency..it.maxFrequency } ?: true
            if (!inRange) {
                return TransmitResult.Error("Frequency ${signal.frequency}Hz not supported by hardware")
            }
            manager.transmit(signal.frequency, pattern)
            TransmitResult.Success
        } catch (e: Exception) {
            TransmitResult.Error("Transmit failed: ${e.message}")
        }
    }
}
