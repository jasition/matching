package jasition.matching.domain.quote.event

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import jasition.matching.domain.aBookId
import jasition.matching.domain.aBooks
import jasition.matching.domain.aFirmWithClient
import jasition.matching.domain.anEventId
import jasition.matching.domain.quote.QuoteRejectReason
import java.time.Instant

internal class MassQuoteCancelRejectedEventPropertyTest : StringSpec({
    val eventId = anEventId()
    val bookId = aBookId()
    val event = MassQuoteCancelRejectedEvent(
        whoRequested = aFirmWithClient(),
        bookId = bookId,
        whenHappened = Instant.now(),
        eventId = eventId,
        rejectReason = QuoteRejectReason.NOT_AUTHORISED,
        rejectText = "Not authorised"
    )
    "Has Book ID as Aggregate ID" {
        event.aggregateId() shouldBe bookId
    }
    "Has Event ID as Event ID" {
        event.eventId() shouldBe eventId
    }
})

internal class MassQuoteCancelRejectedEventTest : StringSpec({
    val eventId = anEventId()
    val bookId = aBookId()
    val books = aBooks(bookId)
    val event = MassQuoteCancelRejectedEvent(
        whoRequested = aFirmWithClient(),
        bookId = bookId,
        whenHappened = Instant.now(),
        eventId = eventId,
        rejectReason = QuoteRejectReason.NOT_AUTHORISED,
        rejectText = "Not authorised"
    )
    val actual = event.play(books)

    "The books only have the last event ID updated" {
        actual shouldBe books.copy(lastEventId = eventId)
    }
})