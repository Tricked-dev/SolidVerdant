package dev.tricked.solidverdant

import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** API 37 activity kept separate so older Android releases never link handoff classes. */
@RequiresApi(37)
@AndroidEntryPoint
class HandoffMainActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.isLoggedIn.collect { isLoggedIn ->
                    setHandoffEnabled(isLoggedIn, null)
                }
            }
        }
    }

    override fun onHandoffActivityDataRequested(
        handoffRequestInfo: HandoffActivityDataRequestInfo
    ): HandoffActivityData {
        val extras = PersistableBundle().apply {
            authViewModel.uiState.value.currentMembership?.organizationId?.let {
                putString(EXTRA_HANDOFF_ORGANIZATION_ID, it)
            }
        }
        val builder = HandoffActivityData.Builder(
            ComponentName(this, HandoffMainActivity::class.java)
        ).setExtras(extras)
        authViewModel.configState.value.endpoint
            .takeIf { it.startsWith("https://") }
            ?.let { builder.setFallbackUri(Uri.parse(it)) }
        return builder.build()
    }
}
