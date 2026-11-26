package kr.co.cotton.vlrgg_mobile.benchmark

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Opt-in heap measurement for the response cache with the production heap flags. Each scenario runs
 * [ResponseCacheMemoryProbe] in a child JVM (`-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError`, as in the Dockerfile)
 * with a fixture transport; it never contacts VLR.GG. Skipped unless [REPORT_PATH_ENV] is set.
 */
class ResponseCacheMemoryBenchmarkTest {
    @Test
    fun `records heap peaks for large distinct pages, a full cache and slow consumers within a 384 MiB heap`() {
        val reportPath = System.getenv(REPORT_PATH_ENV)
        assumeTrue("$REPORT_PATH_ENV must be set; this benchmark is opt-in.", !reportPath.isNullOrBlank())
        val java = ProcessHandle.current().info().command().orElse("java")
        val classPath = System.getProperty("java.class.path")
        val report = buildString {
            appendLine("java=$java")
            appendLine("javaVersion=${System.getProperty("java.version")}")
            appendLine("jvmFlags=${JVM_FLAGS.joinToString(" ")}")
            // Optional comma-separated scenario names to run a subset.
            val only = System.getenv(SCENARIOS_ENV)?.split(',')?.map(String::trim)?.toSet()
            SCENARIOS.filter { only == null || it.name in only }.forEach { scenario ->
                appendLine()
                appendLine("[scenario ${scenario.name}]")
                append(runScenario(java, classPath, scenario))
            }
        }
        val output = Path.of(checkNotNull(reportPath))
        output.parent?.let(Files::createDirectories)
        Files.writeString(output, report)
    }

    private fun runScenario(java: String, classPath: String, scenario: Scenario): String {
        val pageBytes = scenario.pageBytes
        val result = Files.createTempFile("response-cache-memory", ".txt")
        val log = Files.createTempFile("response-cache-memory", ".log")
        try {
            val process = ProcessBuilder(
                listOf(java) + JVM_FLAGS + listOf(
                    "-cp", classPath, PROBE_MAIN, pageBytes.toString(), result.toString(),
                    scenario.cacheBytes.toString(), scenario.fetchConcurrency.toString(),
                ),
            ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
            val finished = process.waitFor(5, TimeUnit.MINUTES)
            if (!finished) process.destroyForcibly().waitFor()
            val output = Files.readString(log)
            val oom = output.contains("OutOfMemoryError")
            return when {
                finished && process.exitValue() == 0 -> Files.readString(result)
                oom -> "pageBytes=$pageBytes\ncacheMaxBytes=${scenario.cacheBytes}\n" +
                    "fetchConcurrencyAndKeys=${scenario.fetchConcurrency}\noom=true\nexitCode=${process.exitValue()}\n"
                else -> {
                    assertTrue(false, "probe failed without an OOM (finished=$finished):\n${output.takeLast(4_000)}")
                    error("unreachable")
                }
            }
        } finally {
            Files.deleteIfExists(result)
            Files.deleteIfExists(log)
        }
    }

    private data class Scenario(val name: String, val pageBytes: Int, val cacheBytes: Int = MIB_32, val fetchConcurrency: Int = 8)

    private companion object {
        const val MIB_32 = 32 * 1024 * 1024
        const val PAGE_REALISTIC = 1_200_000
        const val PAGE_WORST = 4 * 1024 * 1024 - 1024
        const val REPORT_PATH_ENV = "RESPONSE_CACHE_MEMORY_REPORT_PATH"
        const val SCENARIOS_ENV = "RESPONSE_CACHE_MEMORY_SCENARIOS"
        const val MAX_HEAP_ENV = "RESPONSE_CACHE_MEMORY_MAX_HEAP"
        const val PROBE_MAIN = "kr.co.cotton.vlrgg_mobile.benchmark.ResponseCacheMemoryProbeKt"
        /** Production heap flags; [MAX_HEAP_ENV] (for example `256m`) overrides `-Xmx` to find the OOM boundary. */
        val JVM_FLAGS = listOf("-Xms128m", "-Xmx${System.getenv(MAX_HEAP_ENV) ?: "384m"}", "-XX:+ExitOnOutOfMemoryError")

        /**
         * (a) realistic large pages, about the largest real page today; (b) pages at the 4 MiB upstream limit; both with
         * the production defaults, then sensitivity runs for the two candidate reductions (cache cap, fetch concurrency).
         */
        val SCENARIOS = listOf(
            Scenario("a-realistic-1.2MB", PAGE_REALISTIC),
            Scenario("b-worst-4MiB", PAGE_WORST),
            Scenario("a-cache16MiB", PAGE_REALISTIC, cacheBytes = MIB_32 / 2),
            Scenario("a-fetch4", PAGE_REALISTIC, fetchConcurrency = 4),
            Scenario("a-cache16MiB-fetch4", PAGE_REALISTIC, cacheBytes = MIB_32 / 2, fetchConcurrency = 4),
            Scenario("b-cache16MiB", PAGE_WORST, cacheBytes = MIB_32 / 2),
            Scenario("b-fetch4", PAGE_WORST, fetchConcurrency = 4),
            Scenario("b-cache16MiB-fetch4", PAGE_WORST, cacheBytes = MIB_32 / 2, fetchConcurrency = 4),
            // Baselines: the same traffic with the cache disabled.
            Scenario("a-cache-off", PAGE_REALISTIC, cacheBytes = 0),
            Scenario("b-cache-off", PAGE_WORST, cacheBytes = 0),
        )
    }
}
