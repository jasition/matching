package jasition.matching.domain.quote.event

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.vavr.kotlin.list
import jasition.cqrs.EventId
import jasition.matching.domain.*
import jasition.matching.domain.book.entry.Price
import jasition.matching.domain.book.entry.Side
import jasition.matching.domain.book.entry.SizeAtPrice
import jasition.matching.domain.book.entry.TimeInForce
import jasition.matching.domain.quote.QuoteModelType
import jasition.matching.domain.quote.QuoteRejectReason.DUPLICATE_QUOTE
import jasition.matching.domain.quote.QuoteRejectReason.EXCHANGE_CLOSED
import java.time.Instant


internal class MassQuoteRejectedEventPropertyTest : StringSpec({
    val bookId = aBookId()
    val eventId = EventId(1)
    val event = MassQuoteRejectedEvent(
        bookId = bookId,
        eventId = eventId,
        whenHappened = Instant.now(),
        quoteId = randomId(),
        whoRequested = anotherFirmWithoutClient(),
        quoteModelType = QuoteModelType.QUOTE_ENTRY,
        timeInForce = TimeInForce.GOOD_TILL_CANCEL,
        entries = list(
            aQuoteEntry(
                bid = SizeAtPrice(size = 4, price = Price(9)),
                offer = SizeAtPrice(size = 4, price = Price(10))
            ), aQuoteEntry(
                bid = SizeAtPrice(size = 5, price = Price(8)),
                offer = SizeAtPrice(size = 5, price = Price(11))
            )
        ),
        rejectReason = DUPLICATE_QUOTE,
        rejectText = "for some reasons"
    )
    "Has Book ID as Aggregate ID" {
        event.aggregateId() shouldBe bookId
    }
    "Has Event ID as Event ID" {
        event.eventId() shouldBe eventId
    }
})


internal class `Given a mass quote is rejected by an empty book` : StringSpec({
    val books = aBooks(aBookId())
    val event = MassQuoteRejectedEvent(
        bookId = books.bookId,
        eventId = EventId(1),
        whenHappened = Instant.now(),
        quoteId = randomId(),
        whoRequested = anotherFirmWithoutClient(),
        quoteModelType = QuoteModelType.QUOTE_ENTRY,
        timeInForce = TimeInForce.GOOD_TILL_CANCEL,
        entries = list(
            aQuoteEntry(
                bid = SizeAtPrice(size = 4, price = Price(9)),
                offer = SizeAtPrice(size = 4, price = Price(10))
            ), aQuoteEntry(
                bid = SizeAtPrice(size = 5, price = Price(8)),
                offer = SizeAtPrice(size = 5, price = Price(11))
            )
        ),
        rejectReason = EXCHANGE_CLOSED,
        rejectText = "Exchange closed"
    )
    val result = event.play(books)

    "Then the BUY book is still empty" {
        result.buyLimitBook.entries.size() shouldBe 0
    }
    "Then the SELL book is still empty" {
        result.sellLimitBook.entries.size() shouldBe 0
    }
})

internal class MassQuoteRejectedEventTest : StringSpec({
    val existingEntry = aBookEntry(
        eventId = EventId(0),
        side = Side.SELL,
        price = Price(Long.MAX_VALUE),
        whoRequested = aFirmWithoutClient(),
        isQuote = false
    )
    val removedEntry = aBookEntry(
        eventId = EventId(0),
        side = Side.SELL,
        whoRequested = aFirmWithoutClient(),
        isQuote = true
    )
    val books = aBooks(aBookId()).addBookEntry(existingEntry).addBookEntry(
        removedEntry
    )
    val event = MassQuoteRejectedEvent(
        bookId = books.bookId,
        eventId = EventId(1),
        whenHappened = Instant.now(),
        quoteId = randomId(),
        whoRequested = aFirmWithoutClient(),
        quoteModelType = QuoteModelType.QUOTE_ENTRY,
        timeInForce = TimeInForce.GOOD_TILL_CANCEL,
        entries = list(
            aQuoteEntry(
                bid = SizeAtPrice(size = 4, price = Price(9)),
                offer = SizeAtPrice(size = 4, price = Price(10))
            ), aQuoteEntry(
                bid = SizeAtPrice(size = 5, price = Price(8)),
                offer = SizeAtPrice(size = 5, price = Price(11))
            )
        ),
        rejectReason = EXCHANGE_CLOSED,
        rejectText = "Exchange closed"
    )
    val result = event.play(books)

    "The BUY book is still empty" {
        result.buyLimitBook.entries.size() shouldBe 0
    }
    "All the previous order remains in the SELL book" {
        result.sellLimitBook.entries.values() shouldBe list(existingEntry)
    }
})
