package com.eaglepoint.task136

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.eaglepoint.task136.db.DesktopDatabaseFactory
import com.eaglepoint.task136.di.initKoinIfNeeded
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.MeetingWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderFinanceViewModel
import com.eaglepoint.task136.shared.viewmodel.OrderWorkflowViewModel
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import org.koin.core.context.GlobalContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

fun main() = application {
    val passphrase = System.getenv("TASK136_DB_PASSPHRASE")?.encodeToByteArray() ?: desktopPassphrase()
    val database = DesktopDatabaseFactory().create("task136-desktop.db", passphrase)
    initKoinIfNeeded(database)
    val vm: ResourceListViewModel = GlobalContext.get().get()
    val authVm: AuthViewModel = GlobalContext.get().get()
    val orderVm: OrderWorkflowViewModel = GlobalContext.get().get()
    val meetingVm: MeetingWorkflowViewModel = GlobalContext.get().get()
    val financeVm: OrderFinanceViewModel = GlobalContext.get().get()
    Window(onCloseRequest = ::exitApplication, title = "Task-136") {
        App(vm, authVm, orderVm, meetingVm, financeVm)
    }
}

private fun desktopPassphrase(): ByteArray {
    val file = Path.of(System.getProperty("user.home"), ".task136-passphrase")
    if (Files.exists(file)) return Files.readString(file).trim().encodeToByteArray()

    val generated = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    Files.writeString(file, generated)
    return generated.encodeToByteArray()
}
