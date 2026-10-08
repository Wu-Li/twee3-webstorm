package twee.inspections

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import java.util.concurrent.atomic.AtomicLong

/** Notification lifecycle is confined to EDT. Repeated equivalent results do not re-alert. */
@Service(Service.Level.PROJECT)
class StoryDataNotifications(private val project: Project) : Disposable {
    private data class Entry(val stamp: Long, val messages: List<String>, val notifications: List<Notification>)
    private val entries = mutableMapOf<VirtualFile, Entry>()
    private val generation = AtomicLong()
    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it is VFileDeleteEvent }) {
                    val deleted = entries.keys.filter { !it.isValid }
                    deleted.forEach { entries.remove(it)?.notifications?.forEach { notice -> notice.expire() } }
                }
            }
        })
    }
    internal fun update(file: VirtualFile, messages: List<String>, stamp: Long) {
        val epoch = generation.get()
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || epoch != generation.get()) return@invokeLater
            val previous = entries[file]
            if (!file.isValid) { entries.remove(file)?.notifications?.forEach { it.expire() }; return@invokeLater }
            if (previous != null && previous.stamp > stamp) return@invokeLater
            if (previous?.messages == messages) {
                entries[file] = previous.copy(stamp = stamp); return@invokeLater
            }
            previous?.notifications?.forEach { it.expire() }
            val notices = messages.map {
                NotificationGroupManager.getInstance().getNotificationGroup("Twee StoryData")
                    .createNotification("StoryData: ${file.name}", it, NotificationType.ERROR).also { notice -> notice.notify(project) }
            }
            entries[file] = Entry(stamp, messages.toList(), notices)
        }
    }
    fun refreshSettings() {
        generation.incrementAndGet()
        entries.values.flatMap { it.notifications }.forEach { it.expire() }
        entries.clear()
    }
    internal fun activeMessages(file: VirtualFile): List<String> = entries[file]?.messages ?: emptyList()
    internal fun activeNotifications(file: VirtualFile): List<Notification> = entries[file]?.notifications ?: emptyList()
    override fun dispose() { refreshSettings() }
}
