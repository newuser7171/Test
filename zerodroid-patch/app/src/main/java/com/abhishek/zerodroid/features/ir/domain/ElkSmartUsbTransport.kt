package com.abhishek.zerodroid.features.ir.domain

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock

class ElkSmartUsbTransport(private val context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val lock = java.util.concurrent.locks.ReentrantLock()

    private fun device(): UsbDevice? = manager?.deviceList?.values?.firstOrNull {
        it.vendorId == 0x045c && it.productId == 0x0195
    }

    val isAvailable: Boolean get() = runCatching { device() != null }.getOrDefault(false)

    fun requestPermission() {
        try {
            val usb = manager ?: return
            val device = device() ?: return
            if (usb.hasPermission(device)) return
            val intent = Intent(context.packageName + ".USB_IR_PERMISSION").setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, 195, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            usb.requestPermission(device, pending)
        } catch (_: Exception) { }
    }

    fun transmit(frequency: Int, pattern: IntArray): TransmitResult {
        if (!lock.tryLock()) return TransmitResult.Error("USB IR is busy")
        try {
            val usb = manager ?: return TransmitResult.Error("USB service unavailable")
            val device = device() ?: return TransmitResult.Error("ELKSMART disconnected")
            if (!usb.hasPermission(device)) return TransmitResult.Error("Tap Allow ELKSMART USB access first")
            val iface = (0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull { candidate ->
                val endpoints = (0 until candidate.endpointCount).map { candidate.getEndpoint(it) }
                endpoints.any { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT } &&
                    endpoints.any { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
            } ?: return TransmitResult.Error("Required USB bulk endpoints missing")
            val endpoints = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
            val input = endpoints.first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
            val output = endpoints.first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
            val connection = usb.openDevice(device) ?: return TransmitResult.Error("Cannot open USB adapter")
            var claimed = false
            try {
                claimed = connection.claimInterface(iface, true)
                if (!claimed) return TransmitResult.Error("USB interface is in use by another app")
                val formatter = ElkSmartUsbProtocolFormatter()
                if (!formatter.openHandshake(connection, input, output, SystemClock.uptimeMillis() + 1000)) {
                    return TransmitResult.Error("ELKSMART identification failed")
                }
                val deadline = SystemClock.uptimeMillis() + 5000
                for (frame in formatter.encode(frequency, pattern)) {
                    if (SystemClock.uptimeMillis() >= deadline) return TransmitResult.Error("USB transmission timed out")
                    if (connection.bulkTransfer(output, frame, frame.size, 400) != frame.size) {
                        return TransmitResult.Error("Incomplete USB transfer; reconnect adapter before retrying")
                    }
                    SystemClock.sleep(formatter.interFrameDelayMs)
                }
                SystemClock.sleep(formatter.postTransmitDelayMs(pattern))
                formatter.drainAfterTransmit(connection, input)
                return TransmitResult.Success
            } finally {
                try { if (claimed) connection.releaseInterface(iface) } finally { connection.close() }
            }
        } catch (e: Exception) {
            return TransmitResult.Error("USB IR failed: ${e.message}")
        } finally { lock.unlock() }
    }
}
