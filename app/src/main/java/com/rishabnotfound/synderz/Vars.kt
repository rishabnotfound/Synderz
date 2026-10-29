package com.rishabnotfound.synderz

import java.time.Instant
import java.util.UUID
import kotlin.random.Random

val VAR_REGEX = Regex("""\{\{\s*([^{}]+?)\s*\}\}""")

private val dynamic: Map<String, () -> String> = mapOf(
    "\$guid" to { UUID.randomUUID().toString() },
    "\$randomUUID" to { UUID.randomUUID().toString() },
    "\$timestamp" to { (System.currentTimeMillis() / 1000).toString() },
    "\$isoTimestamp" to { Instant.now().toString() },
    "\$randomInt" to { Random.nextInt(0, 1001).toString() },
    "\$randomBoolean" to { Random.nextBoolean().toString() },
)

fun isResolvable(name: String, vars: Map<String, String>) = name in vars || name in dynamic

/** Replaces {{name}} with its value; unknown names are left untouched (Postman behaviour). */
fun resolve(s: String, vars: Map<String, String>): String =
    if (!s.contains("{{")) s
    else VAR_REGEX.replace(s) { m -> val n = m.groupValues[1]; vars[n] ?: dynamic[n]?.invoke() ?: m.value }

/** Postman precedence: environment > collection > globals. */
fun scope(data: AppData, req: Req): Map<String, String> {
    fun List<KV>.m() = filter { it.enabled && it.key.isNotBlank() }.associate { it.key to it.value }
    val col = data.collections.find { it.id == req.collectionId }?.variables.orEmpty()
    val env = data.envs.find { it.id == data.activeEnvId }?.vars.orEmpty()
    return data.globals.m() + col.m() + env.m()
}
