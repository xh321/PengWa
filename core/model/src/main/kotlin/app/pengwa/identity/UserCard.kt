package app.pengwa.identity

/** Images are bounded JPEG/PNG thumbnails, embedded so received cards work offline. */
enum class BlockType { Text, Link, Social, Image }
data class CardBlock(val type: BlockType, val title: String = "", val text: String = "", val image: String = "", val url: String = "")
data class CardContent(val avatar: String = "", val blocks: List<CardBlock> = emptyList())

