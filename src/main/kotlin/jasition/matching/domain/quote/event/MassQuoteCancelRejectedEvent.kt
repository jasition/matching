package jasition.matching.domain.quote.event

import jasition.cqrs.Event
import jasition.cqrs.EventId
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.client.Client
import jasition.matching.domain.quote.QuoteRejectReason
import java.time.Instant

data class MassQuoteCancelRejectedEvent(
    val eventId: EventId,
    val whoRequested: Client,
    val bookId: BookId,
    val whenHappened: Instant,
    val rejectReason: QuoteRejectReason,
    val rejectText: String?
) : Event<BookId, Books> {
    override fun aggregateId(): BookId = bookId
    override fun eventId(): EventId = eventId
    override fun play(aggregate: Books): Books = aggregate.ofEventId(eventId)
}

