package dev.tricked.solidverdant.util

import javax.inject.Inject

interface Clock { fun nowMs(): Long }

class SystemClock @Inject constructor() : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
