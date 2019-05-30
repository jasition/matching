package jasition.matching.domain.client

data class ClientRequestId(
    val current: String,
    val original: String? = null,
    val collectionId: String? = null,
    val parentId: String? = null
)

fun requestLinksToOriginal(original: ClientRequestId, new: ClientRequestId): Boolean =
    original.current == new.current || original.current == new.original
