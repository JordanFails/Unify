package me.jordanfails.unify.menu.anvil

/**
 * Action(s) to take after an anvil click.
 *
 * Prefer the factory methods on the companion for a clean call site:
 * ```
 * .onComplete { _, text ->
 *     if (text.isBlank()) AnvilResult.retry("Required")
 *     else AnvilResult.close()
 * }
 * ```
 */
sealed class AnvilResult {
    data object Close : AnvilResult()
    data object KeepOpen : AnvilResult()
    data class Retry(val text: String) : AnvilResult()
    data class Run(val block: () -> Unit) : AnvilResult()
    data class Composite(val actions: List<AnvilResult>) : AnvilResult()

    companion object {
        @JvmStatic
        fun close(): AnvilResult = Close

        @JvmStatic
        fun keepOpen(): AnvilResult = KeepOpen

        @JvmStatic
        fun retry(text: String): AnvilResult = Retry(text)

        @JvmStatic
        fun run(block: Runnable): AnvilResult = Run { block.run() }

        fun run(block: () -> Unit): AnvilResult = Run(block)

        @JvmStatic
        fun of(vararg results: AnvilResult): AnvilResult =
            if (results.size == 1) results[0] else Composite(results.toList())
    }
}
