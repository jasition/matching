package jasition.matching.domain.order.event

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import jasition.cqrs.EventId
import jasition.matching.domain.*
import jasition.matching.domain.book.entry.EntryStatus
import jasition.matching.domain.book.entry.EntryType
import jasition.matching.domain.book.entry.Side
import jasition.matching.domain.book.entry.TimeInForce
import jasition.matching.domain.order.event.OrderCancelReason.CANCELLED_BY_EXCHANGE
import jasition.matching.domain.order.event.OrderCancelReason.CANCELLED_UPON_REQUEST
import java.time.Instant

internal class OrderCancelledEventPropertyTest : StringSpec({
    val eventId = anEventId()
    val bookId = aBookId()
    val event = OrderCancelledEvent(
        requestId = aClientRequestId(),
        whoRequested = aFirmWithClient(),
        bookId = bookId,
        entryType = EntryType.LIMIT,
        side = Side.BUY,
        price = aPrice(),
        timeInForce = TimeInForce.GOOD_TILL_CANCEL,
        whenHappened = Instant.now(),
        eventId = eventId,
        sizes = anEntrySizes(),
        status = EntryStatus.CANCELLED,
        reason = CANCELLED_BY_EXCHANGE
    )
    "Has Book ID as Aggregate ID" {
        event.aggregateId() shouldBe bookId
    }
    "Has Event ID as Event ID" {
        event.eventId() shouldBe eventId
    }
})

internal class OrderCancelledByExchangeEventTest : StringSpec({
    val bookId = aBookId()
    val originalRequestId = aClientRequestId()
    val whoRequested = aFirmWithClient()

    forall(
        row("by Exchange", originalRequestId, CANCELLED_BY_EXCHANGE),
        row("upon Request", anotherClientRequestId().copy(original = originalRequestId.current), CANCELLED_UPON_REQUEST)
    )  { context, requestId, orderCancelReason ->
        val entry = aBookEntry(
            eventId = EventId(1),
            whoRequested = whoRequested,
            requestId = requestId
        )

        val entryOfOtherFirmButSameRequestId = entry
            .withKey(eventId = EventId(1))
            .copy(whoRequested = whoRequested.copy(firmId = "something else"))

        val entryOfSameFirmClientButDifferentRequestId = entry
            .withKey(eventId = EventId(2))
            .copy(requestId = requestId.copy(current = "something else"))

        val entryOfDifferentFirmClientAndRequestId = entry
            .withKey(eventId = EventId(3))
            .copy(
                whoRequested = whoRequested.copy(firmId = "something else"),
                requestId = requestId.copy(current = "something else")
            )
        val books = aBooks(bookId)
            .addBookEntry(entry)
            .addBookEntry(entryOfOtherFirmButSameRequestId)
            .addBookEntry(entryOfSameFirmClientButDifferentRequestId)
            .addBookEntry(entryOfDifferentFirmClientAndRequestId)
        val event = OrderCancelledEvent(
            requestId = requestId,
            whoRequested = whoRequested,
            bookId = bookId,
            entryType = EntryType.LIMIT,
            side = Side.BUY,
            price = aPrice(),
            timeInForce = TimeInForce.GOOD_TILL_CANCEL,
            whenHappened = Instant.now(),
            eventId = EventId(4),
            sizes = anEntrySizes(),
            status = EntryStatus.CANCELLED,
            reason = orderCancelReason
        )

        val actual = event.play(books)

        "Cancelled $context: The opposite-side book is not affected" {
            actual.sellLimitBook.entries.size() shouldBe 0
        }
        "Cancelled $context: The entry removed from the same-side book" {
            actual.buyLimitBook.entries.containsValue(entry) shouldBe false
        }
        "Cancelled $context: Entries of different firm client not affected" {
            actual.buyLimitBook.entries.containsValue(entryOfOtherFirmButSameRequestId) shouldBe true
        }
        "Cancelled $context: Entries of different request ID not affected" {
            actual.buyLimitBook.entries.containsValue(entryOfSameFirmClientButDifferentRequestId) shouldBe true
        }
        "Cancelled $context: Entries of different request ID and different firm client not affected" {
            actual.buyLimitBook.entries.containsValue(entryOfDifferentFirmClientAndRequestId) shouldBe true
        }
    }
})
