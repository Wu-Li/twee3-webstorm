package twee.testing

import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ThrowableRunnable
import java.nio.file.Path

/** Story roots are relative to a real project directory, not the light fixture's temp:// VFS. */
abstract class StoryProjectTestCase : BasePlatformTestCase() {
    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        // Match the platform's heavy fixture lifecycle: initialize the application before EDT setup.
        TestApplicationManager.getInstance()
        super.runBare(testRunnable)
    }

    override fun createMyFixture(): CodeInsightTestFixture {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val directory = factory.createTempDirTestFixture()
        val root = Path.of(directory.tempDirPath)
        val builder = factory.createFixtureBuilder(javaClass.name + "." + name, root, true)
        builder.addModule(EmptyModuleFixtureBuilder::class.java).addSourceContentRoot(root.toString())
        return factory.createCodeInsightFixture(builder.fixture, directory)
    }

    override fun setUp() {
        super.setUp()
        assertEquals(Path.of(myFixture.tempDirPath).toAbsolutePath().normalize(),
            Path.of(project.basePath!!).toAbsolutePath().normalize())
        assertEquals("file", myFixture.tempDirFixture.findOrCreateDir("").fileSystem.protocol)
    }
}
