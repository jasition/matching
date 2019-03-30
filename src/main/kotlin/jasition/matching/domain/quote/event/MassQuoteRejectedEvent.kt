package jasition.matching.domain.quote.event

import io.vavr.collection.Seq
import jasition.cqrs.Event
import jasition.cqrs.EventId
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.entry.TimeInForce
import jasition.matching.domain.book.verifyEventId
import jasition.matching.domain.client.Client
import jasition.matching.domain.quote.QuoteEntry
import jasition.matching.domain.quote.QuoteModelType
import jasition.matching.domain.quote.QuoteRejectReason
import jasition.matching.domain.quote.cancelExistingQuotes
import java.time.Instant

data class MassQuoteRejectedEvent(
    val eventId: EventId,
    val quoteId: String,
    val whoRequested: Client,
    val bookId: BookId,
    val quoteModelType: QuoteModelType,
    val timeInForce: TimeInForce,
    val entries: Seq<QuoteEntry>,
    val whenHappened: Instant,
    val rejectReason: QuoteRejectReason,
    val rejectText: String?
) : Event<BookId, Books> {
    override fun aggregateId(): BookId = bookId
    override fun eventId(): EventId = eventId
    override fun play(aggregate: Books): Books {
        val books = aggregate.copy(lastEventId = aggregate verifyEventId eventId)

        val cancelledEvent = cancelExistingQuotes(
            books = books,
            eventId = eventId,
            whoRequested = whoRequested,
            whenHappened = whenHappened)

        //TODO: Replace by Elvis operator when its code coverage can be accurately measured
        return if (cancelledEvent != null) {
            cancelledEvent.play(books)
        } else books
    }
}

