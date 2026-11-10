// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2025-2026 InstallerX Revived contributors
package com.rosan.installer.framework.privileged.provider

import android.content.ComponentName
import android.os.SystemProperties
import com.rosan.installer.domain.device.provider.DeviceCapabilityProvider
import com.rosan.installer.domain.privileged.provider.AppOpsProvider
import com.rosan.installer.domain.privileged.provider.ShellExecutionProvider
import com.rosan.installer.domain.settings.model.config.Authorizer
import com.rosan.installer.domain.settings.model.config.ConfigModel
import com.rosan.installer.domain.settings.model.config.InstallMode
import com.rosan.installer.framework.privileged.core.execution.authorization.getSpecialAuth
import com.rosan.installer.framework.privileged.core.execution.dispatcher.useDirectPrivileged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class AppOpsProviderImpl(
    private val capabilityProvider: DeviceCapabilityProvider,
    private val shellExecutionProvider: ShellExecutionProvider,
) : AppOpsProvider {
    private companion object {
        const val FOCUS_WHITELIST_PROPERTY = "persist.sys.feature.xms.switcher"
    }

    override suspend fun setDefaultInstaller(
        authorizer: Authorizer,
        customizeAuthorizer: String,
        component: ComponentName,
        lock: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            useDirectPrivileged(
                isSystemApp = capabilityProvider.isSystemApp,
                authorizer = authorizer,
                customizeAuthorizer = customizeAuthorizer,
                special = getSpecialAuth(authorizer),
            ) { privileged ->
                privileged.setDefaultInstaller(component, lock)
            }
        }
    }

    override suspend fun setAdbVerifyEnabled(authorizer: Authorizer, customizeAuthorizer: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            useDirectPrivileged(
                isSystemApp = capabilityProvider.isSystemApp,
                authorizer = authorizer,
                customizeAuthorizer = customizeAuthorizer,
            ) { privileged ->
                try {
                    privileged.setAdbVerify(enabled)
                    Timber.i("Successfully requested to set ADB verify to $enabled.")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to set ADB verify")
                    throw e
                }
            }
        }
    }

    override suspend fun setPackageNetworkingEnabled(authorizer: Authorizer, uid: Int, enabled: Boolean) {
        if (authorizer != Authorizer.Shizuku) throw IllegalStateException("Unsupported authorizer: $authorizer")
        withContext(Dispatchers.IO) {
            useDirectPrivileged(
                isSystemApp = capabilityProvider.isSystemApp,
                authorizer = authorizer,
            ) { privileged ->
                try {
                    privileged.setPackageNetworkingEnabled(uid, enabled)
                    Timber.i("Network ${if (enabled) "RESTORED" else "BLOCKED"} for UID: $uid")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to set package networking via dedicated IPC method")
                    throw e
                }
            }
        }
    }

    override fun isFocusNotificationWhitelistRemoved(): Boolean = SystemProperties.getBoolean(FOCUS_WHITELIST_PROPERTY, false)

    override suspend fun setFocusNotificationWhitelistRemoved(removed: Boolean) {
        withContext(Dispatchers.IO) {
            val value = if (removed) "true" else "false"
            val config = ConfigModel(
                description = "",
                authorizer = Authorizer.Root,
                customizeAuthorizer = "",
                installMode = InstallMode.Dialog,
                installer = null,
            )
            val output = shellExecutionProvider.executeCommandArray(
                config,
                arrayOf("setprop", FOCUS_WHITELIST_PROPERTY, value),
            )
            check(!output.startsWith("Exit Code")) {
                "Failed to set $FOCUS_WHITELIST_PROPERTY=$value: $output"
            }
            check(SystemProperties.getBoolean(FOCUS_WHITELIST_PROPERTY, false) == removed) {
                "Property $FOCUS_WHITELIST_PROPERTY did not update to $value"
            }
            Timber.i("Focus notification whitelist removed=%s", removed)
        }
    }

    override suspend fun prepareUnknownSourceAppOp(
        authorizer: Authorizer,
        customizeAuthorizer: String,
        uid: Int,
        packageName: String,
    ): Int? = withContext(Dispatchers.IO) {
        var mode: Int? = null
        useDirectPrivileged(
            isSystemApp = capabilityProvider.isSystemApp,
            authorizer = authorizer,
            customizeAuthorizer = customizeAuthorizer,
            special = getSpecialAuth(authorizer),
        ) { privileged ->
            mode = privileged.prepareUnknownSourceAppOp(uid, packageName)
        }
        mode
    }
}
