package jasition.matching.domain.order.event

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import jasition.cqrs.EventId
import jasition.matching.domain.*
import jasition.matching.domain.book.entry.EntryStatus
import java.time.Instant

internal class OrderCancelRejectedEventPropertyTest : StringSpec({
    val eventId = anEventId()
    val bookId = aBookId()
    val event = OrderCancelRejectedEvent(
        requestId = aClientRequestId(),
        whoRequested = aFirmWithClient(),
        bookId = bookId,
        whenHappened = Instant.now(),
        eventId = eventId,
        status = EntryStatus.REJECTED,
        rejectReason = OrderCancelRejectReason.BROKER_EXCHANGE_OPTION,
        rejectText = "Not allowed"
    )
    "Has Book ID as Aggregate ID" {
        event.aggregateId() shouldBe bookId
    }
    "Has Event ID as Event ID" {
        event.eventId() shouldBe eventId
    }
})

internal class OrderCancelRejectedEventTest : StringSpec({
    val eventId = EventId(1)
    val bookId = aBookId()
    val books = aBooks(bookId)
    val event = OrderCancelRejectedEvent(
        requestId = aClientRequestId(),
        whoRequested = aFirmWithClient(),
        bookId = bookId,
        whenHappened = Instant.now(),
        eventId = eventId,
        status = EntryStatus.REJECTED,
        rejectReason = OrderCancelRejectReason.BROKER_EXCHANGE_OPTION,
        rejectText = "Not allowed"
    )

    val actual = event.play(books)

    "The books only have the last event ID updated" {
        actual shouldBe books.copy(lastEventId = eventId)
    }
})