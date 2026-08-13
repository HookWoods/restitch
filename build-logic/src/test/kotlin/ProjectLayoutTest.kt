import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class ProjectLayoutTest {
    @Test
    fun allLibraryModulesAreIncluded() {
        val settings = File("../settings.gradle.kts").readText()
        assertThat(settings).contains(
            ":modules:aggregation-api",
            ":modules:aggregation-json-spi",
            ":modules:aggregation-core",
            ":modules:aggregation-spring-boot3-autoconfigure",
            ":modules:aggregation-spring-boot3-starter",
            ":modules:aggregation-spring-boot4-autoconfigure",
            ":modules:aggregation-spring-boot4-starter",
        )
    }
}
