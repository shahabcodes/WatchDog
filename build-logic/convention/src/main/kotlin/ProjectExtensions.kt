import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Shortcut to the `libs` version catalog from inside a convention plugin.
 *
 * Must be `internal`: a public `Project.libs` would leak onto the build-script
 * classpath of consuming modules and shadow Gradle's generated type-safe `libs`
 * accessor, breaking every `libs.androidx.*` reference in their build.gradle.kts.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
