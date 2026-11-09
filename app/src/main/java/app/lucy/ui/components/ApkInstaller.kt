package app.lucy.ui.components

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

sealed class InstallResult {
    data object PendingUserAction : InstallResult()
    data object Success : InstallResult()
    data class Failure(val message: String) : InstallResult()
}

object ApkInstaller {

    private const val ACTION_INSTALL_RESULT = "app.lucy.action.INSTALL_RESULT"

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(context: Context) {
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun install(context: Context, apk: File, onResult: (InstallResult) -> Unit = {}) {
        try {
            installWithPackageInstaller(context, apk, onResult)
        } catch (t: Throwable) {
            runCatching { openInstallerIntent(context, apk) }
                .onSuccess { onResult(InstallResult.Success) }
                .onFailure {
                    onResult(
                        InstallResult.Failure(
                            it.message ?: "Could not open the package installer"
                        )
                    )
                }
        }
    }

    private fun installWithPackageInstaller(
        context: Context,
        apk: File,
        onResult: (InstallResult) -> Unit
    ) {
        val appContext = context.applicationContext
        val installer = appContext.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        )
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("lucy-update.apk", 0, apk.length()).use { output ->
                    input.copyTo(output, 64 * 1024)
                    session.fsync(output)
                }
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) ?: -1
                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirm = getInstallIntent(intent)
                            if (confirm != null) {
                                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { appContext.startActivity(confirm) }
                                onResult(InstallResult.PendingUserAction)
                            } else {
                                runCatching { appContext.unregisterReceiver(this) }
                                onResult(InstallResult.Failure("Installer confirmation unavailable"))
                            }
                        }

                        PackageInstaller.STATUS_SUCCESS -> {
                            runCatching { appContext.unregisterReceiver(this) }
                            onResult(InstallResult.Success)
                        }

                        else -> {
                            runCatching { appContext.unregisterReceiver(this) }
                            val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                ?: "Installation failed (status $status)"
                            onResult(InstallResult.Failure(message))
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ACTION_INSTALL_RESULT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val resultIntent = Intent(ACTION_INSTALL_RESULT).setPackage(appContext.packageName)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            val pendingIntent = PendingIntent.getBroadcast(appContext, sessionId, resultIntent, flags)

            try {
                session.commit(pendingIntent.intentSender)
            } catch (t: Throwable) {
                runCatching { appContext.unregisterReceiver(receiver) }
                throw t
            }
        }
    }

    private fun getInstallIntent(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    private fun openInstallerIntent(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }
}
