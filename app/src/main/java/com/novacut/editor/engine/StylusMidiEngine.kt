package com.novacut.editor.engine

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * S Pen pressure + Bluetooth MIDI jog/shuttle controller.
 *
 * Two distinct integrations bundled into one engine:
 *   * **Stylus** — exposes helpers to read `MotionEvent.TOOL_TYPE_STYLUS` pressure
 *     (0..1) for keyframe curve authoring. Clients call [stylusPressure].
 *   * **MIDI**  — binds to [MidiManager] and maps MIDI CC messages to jog,
 *     shuttle, transport (play/stop), and mark points. The default mapping
 *     targets the Contour ShuttleXpress / ShuttlePro protocol (CC 1 = shuttle,
 *     CC 2 = jog wheel relative).
 *
 * Events are delivered to the main thread via an internal Handler so the
 * ViewModel stays free of MIDI-thread concerns. Calling [setListener] with
 * `null` immediately stops event delivery.
 *
 * The engine holds a single connected device; calling [connectFirstAvailable]
 * a second time replaces the active connection. Always call [disconnect] when
 * you no longer need the integration — there is no automatic teardown.
 */
@Singleton
class StylusMidiEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    interface Listener {
        fun onJog(delta: Int) {}
        fun onShuttle(speed: Float) {}
        fun onTransport(action: Transport) {}
        fun onMark() {}
    }

    enum class Transport { PLAY, PAUSE, STOP, PREV, NEXT }

    @Volatile private var listener: Listener? = null
    @Volatile private var activeDevice: MidiDevice? = null
    private val handler = Handler(Looper.getMainLooper())

    fun setListener(l: Listener?) { listener = l }

    /** Returns pressure in 0..1 for stylus events, null otherwise. */
    fun stylusPressure(ev: MotionEvent): Float? {
        if (ev.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return null
        return ev.pressure.coerceIn(0f, 1f)
    }

    /** True when this pointer can deliver pressure-accurate drawing. */
    fun isStylus(ev: MotionEvent): Boolean =
        ev.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS

    /**
     * Connect to the first MIDI device that advertises at least one input
     * port. Returns `false` immediately if MIDI is unavailable or no device
     * is present. The actual open happens asynchronously — use [setListener]
     * before calling this so events reach the UI once the open completes.
     */
    fun connectFirstAvailable(): Boolean {
        val mm = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return false
        // `MidiManager.devices` is deprecated on API 33+ in favour of
        // getDevicesForTransport, but the replacement returns `Set<MidiDevice>`
        // whose members are already opened — a different lifecycle. The legacy
        // array API still works on every supported SDK (minSdk 26) and matches
        // our "scan then open" model; we keep it until we have a reason to
        // handle the two return shapes separately.
        @Suppress("DEPRECATION")
        val devices = mm.devices
        val first = devices.firstOrNull { it.inputPortCount > 0 } ?: return false
        mm.openDevice(first, { dev ->
            if (dev == null) {
                Log.w(TAG, "midi device open returned null")
                return@openDevice
            }
            // Replace any prior connection so we never leak two open handles.
            activeDevice?.let { old -> try { old.close() } catch (_: Exception) {} }
            activeDevice = dev
            val port = try { dev.openOutputPort(0) } catch (e: Exception) {
                Log.w(TAG, "openOutputPort failed", e); null
            }
            port?.connect(midiReceiver)
        }, handler)
        return true
    }

    /** Close the active MIDI device, if any. Safe to call from any thread. */
    fun disconnect() {
        val d = activeDevice ?: return
        activeDevice = null
        try { d.close() } catch (e: Exception) { Log.w(TAG, "midi close failed", e) }
    }

    private val midiReceiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
            if (msg == null || count < 3) return
            val status = msg[offset].toInt() and 0xF0
            if (status != 0xB0) return // CC messages only
            val cc = msg[offset + 1].toInt() and 0x7F
            val value = msg[offset + 2].toInt() and 0x7F
            val l = listener ?: return
            handler.post {
                when (cc) {
                    1 -> {
                        // Absolute shuttle: 64 = center, 0 = full reverse, 127 = full forward.
                        val speed = ((value - 64) / 63f).coerceIn(-1f, 1f)
                        if (abs(speed) < 0.02f) l.onShuttle(0f) else l.onShuttle(speed)
                    }
                    2 -> l.onJog(if (value < 64) 1 else -1)
                    64 -> if (value >= 64) l.onTransport(Transport.PLAY)
                    65 -> if (value >= 64) l.onTransport(Transport.STOP)
                    66 -> if (value >= 64) l.onTransport(Transport.PREV)
                    67 -> if (value >= 64) l.onTransport(Transport.NEXT)
                    68 -> if (value >= 64) l.onMark()
                }
            }
        }
    }

    companion object { private const val TAG = "StylusMidiEngine" }
}
