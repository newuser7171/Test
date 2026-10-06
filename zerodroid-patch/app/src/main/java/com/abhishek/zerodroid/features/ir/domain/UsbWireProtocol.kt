package com.abhishek.zerodroid.features.ir.domain

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log

interface UsbWireProtocol {
  val name: String
  val strictHandshake: Boolean
  val wantsBackgroundReader: Boolean
  val interFrameDelayMs: Long

  fun openHandshake(connection: UsbDeviceConnection, inEndpoint: UsbEndpoint, outEndpoint: UsbEndpoint, deadlineMs: Long = Long.MAX_VALUE): Boolean
  fun encode(frequencyHz: Int, patternUs: IntArray): List<ByteArray>
  fun postTransmitDelayMs(patternUs: IntArray): Long
  fun drainAfterTransmit(connection: UsbDeviceConnection, inEndpoint: UsbEndpoint) {}
}
