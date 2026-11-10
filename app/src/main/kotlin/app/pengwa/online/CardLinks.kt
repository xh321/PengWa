package app.pengwa.online

import java.util.UUID

internal class CardLinks(private val origin: String) {
    val configured: Boolean get() = origin.isNotEmpty()

    fun page(id: UUID) = "$origin/c/$id"

    fun api(id: UUID) = "$origin/api/cards/$id"

    fun idFrom(value: String): UUID? {
        if (!configured || !value.startsWith("$origin/c/")) return null
        val path = value.removePrefix("$origin/c/")
        val id = runCatching { UUID.fromString(path) }.getOrNull() ?: return null
        return id.takeIf { it.toString() == path }
    }
}
