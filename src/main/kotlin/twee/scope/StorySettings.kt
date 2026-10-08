package twee.scope

import com.intellij.openapi.components.*
import com.intellij.openapi.util.ModificationTracker
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.PROJECT)
@State(name = "TweeStories", storages = [Storage("twee.xml")])
class StorySettings : PersistentStateComponent<StorySettings.Options>, ModificationTracker {
    data class Story(var id: String = "", var name: String = "", var roots: MutableList<String> = mutableListOf(),
                     var exclusions: MutableList<String> = mutableListOf(), var outputDirectory: String = "build",
                     var harlowe3WhenMissing: Boolean = false) {
        fun snapshot() = copy(roots = roots.toMutableList(), exclusions = exclusions.toMutableList())
        override fun toString() = name
    }
    data class Options(var stories: MutableList<Story> = mutableListOf(), var selectedStoryId: String = "") {
        fun snapshot() = copy(stories = stories.map { it.snapshot() }.toMutableList())
    }
    @Volatile private var options = Options()
    private val counter = AtomicLong()
    override fun getState() = options.snapshot()
    override fun loadState(state: Options) { options = state.snapshot(); counter.incrementAndGet() }
    override fun getModificationCount() = counter.get()
}

/** Never store machine-local installation locations in shared story settings. */
@Service(Service.Level.PROJECT)
@State(name = "TweeLocalTools", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class StoryLocalTools : PersistentStateComponent<StoryLocalTools.Options>, ModificationTracker {
    data class Options(var compilerPath: String = "", var formatDirectories: MutableList<String> = mutableListOf())
    @Volatile private var options = Options()
    private val counter = AtomicLong()
    override fun getState() = options.copy(formatDirectories = options.formatDirectories.toMutableList())
    override fun loadState(state: Options) { options = state.copy(formatDirectories = state.formatDirectories.toMutableList()); counter.incrementAndGet() }
    override fun getModificationCount() = counter.get()
}
