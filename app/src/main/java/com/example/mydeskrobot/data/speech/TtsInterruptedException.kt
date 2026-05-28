package com.example.mydeskrobot.data.speech

import java.util.concurrent.CancellationException

/** TTS fermato dall'utente (barge-in) prima del termine naturale. */
class TtsInterruptedException : CancellationException("TTS interrupted by user")
