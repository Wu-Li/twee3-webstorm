package twee.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.PROJECT)
@State(name = "TweeChecks", storages = [Storage("twee.xml")])
class TweeCheckSettings : PersistentStateComponent<TweeCheckSettings.Options> {
    data class Options(
        var spaceAfterStartToken: Boolean = true,
        var ifid: Boolean = true,
        var format: Boolean = true,
        var formatVersion: Boolean = true
    )
    @Volatile private var options = Options()
    override fun getState() = options.copy()
    override fun loadState(state: Options) { options = state.copy() }
}
