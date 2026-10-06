ELKSMART USB IR transmission patch

Adapter: VID 045C / PID 0195. Adds USB discovery, permission request, endpoint discovery, subtype handshake and bulk transmission. Existing built-in emitter route remains preferred. USB operations run on Dispatchers.IO, concurrent presses are guarded, and connections close after each send.

Open IR Remote, connect the adapter, tap Allow ELKSMART USB access, approve Android dialog, then select a remote and send a button. Learning is not implemented in this patch.

Validation: git diff --check passed. Android build could not run because services.gradle.org is unreachable in the execution environment. No APK or hardware test completed. This source patch is unverified until compiled and tested with the adapter.

Protocol formatter adapted from iodn/android-ir-blaster; see THIRD_PARTY_IR.md and LICENSE-USB-IR-GPL-3.0.
