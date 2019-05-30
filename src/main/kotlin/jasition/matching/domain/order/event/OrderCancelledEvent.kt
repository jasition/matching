package jasition.matching.domain.order.event

import jasition.cqrs.Event
import jasition.cqrs.EventId
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.entry.*
import jasition.matching.domain.book.verifyEventId
import jasition.matching.domain.client.Client
import jasition.matching.domain.client.ClientRequestId
import jasition.matching.domain.client.requestLinksToOriginal
import java.time.Instant
import java.util.function.Predicate

data class OrderCancelledEvent(
    val eventId: EventId,
    val requestId: ClientRequestId,
    val whoRequested: Client,
    val bookId: BookId,
    val entryType: EntryType,
    val side: Side,
    val sizes: EntrySizes,
    val price: Price?,
    val timeInForce: TimeInForce,
    val status: EntryStatus,
    val whenHappened: Instant,
    val reason: OrderCancelReason
) : Event<BookId, Books> {
    override fun aggregateId(): BookId = bookId
    override fun eventId(): EventId = eventId
    override fun play(aggregate: Books): Books =
        aggregate.removeBookEntries(
            eventId = aggregate verifyEventId eventId,
            side = side,
            predicate = Predicate {
                it.whoRequested == whoRequested
                        && requestLinksToOriginal(original = it.requestId, new = requestId)
            })
}

enum class OrderCancelReason {
    CANCELLED_UPON_REQUEST,
    CANCELLED_BY_EXCHANGE
}