package app.bertopia.domain.store

object PackVersions {
    fun compare(first: String, second: String): Int {
        val (firstCore, firstPrerelease) = split(first)
        val (secondCore, secondPrerelease) = split(second)
        val coreComparison = compareIdentifiers(firstCore, secondCore, missingSortsFirst = false)
        if (coreComparison != 0) return coreComparison
        return when {
            firstPrerelease == null && secondPrerelease == null -> 0
            firstPrerelease == null -> 1
            secondPrerelease == null -> -1
            else -> compareIdentifiers(firstPrerelease, secondPrerelease, missingSortsFirst = true)
        }
    }

    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    private fun split(version: String): Pair<List<String>, List<String>?> {
        val trimmed = version.trim().removePrefix("v").substringBefore('+')
        val core = trimmed.substringBefore('-').split('.')
        val prerelease = if ('-' in trimmed) trimmed.substringAfter('-').split('.') else null
        return core to prerelease
    }

    private fun compareIdentifiers(first: List<String>, second: List<String>, missingSortsFirst: Boolean): Int {
        for (index in 0 until maxOf(first.size, second.size)) {
            val left = first.getOrNull(index)
            val right = second.getOrNull(index)
            if (left == null || right == null) {
                val leftValue = left ?: if (missingSortsFirst) return -1 else "0"
                val rightValue = right ?: if (missingSortsFirst) return 1 else "0"
                return compareIdentifier(leftValue, rightValue)
            }
            val comparison = compareIdentifier(left, right)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun compareIdentifier(left: String, right: String): Int {
        val leftNumber = left.toLongOrNull()
        val rightNumber = right.toLongOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right)
        }
    }
}
