package jasition.matching.domain.order.event

import jasition.cqrs.Event
import jasition.cqrs.EventId
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.entry.EntryStatus
import jasition.matching.domain.client.Client
import jasition.matching.domain.client.ClientRequestId
import java.time.Instant

data class OrderCancelRejectedEvent(
    val eventId: EventId,
    val requestId: ClientRequestId,
    val whoRequested: Client,
    val bookId: BookId,
    val status: EntryStatus,
    val whenHappened: Instant,
    val rejectReason: OrderCancelRejectReason,
    val rejectText: String?
) : Event<BookId, Books> {
    override fun aggregateId(): BookId = bookId
    override fun eventId(): EventId = eventId
    override fun play(aggregate: Books): Books = aggregate.ofEventId(eventId)
}

enum class OrderCancelRejectReason {
    UNKNOWN_ORDER,
    UNKNOWN_SYMBOL,
    EXCHANGE_CLOSED,
    BROKER_EXCHANGE_OPTION,
    OTHER
}
