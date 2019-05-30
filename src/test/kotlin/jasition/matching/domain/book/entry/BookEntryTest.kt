package jasition.matching.domain.book.entry

import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.beLessThan
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import jasition.cqrs.EventId
import jasition.matching.domain.aBookEntry
import jasition.matching.domain.aBookId
import jasition.matching.domain.anEventId
import jasition.matching.domain.order.event.OrderCancelReason
import jasition.matching.domain.order.event.OrderCancelledEvent
import jasition.matching.domain.randomPrice
import jasition.matching.domain.trade.event.TradeSideEntry
import java.time.Instant
import kotlin.random.Random

internal class BookEntryTest : StringSpec({
    val entry = aBookEntry(
        sizes = EntrySizes(available = 23, traded = 2, cancelled = 0)
    )

    "Mutates to another BookEntry with given key" {
        val price = randomPrice()
        val whenSubmitted = Instant.now()
        val eventId = EventId(Random.nextLong(0, 100000))
        entry.withKey(price = price, whenSubmitted = whenSubmitted, eventId = eventId) shouldBe entry.copy(
            key = entry.key.copy(price = price, whenSubmitted = whenSubmitted, eventId = eventId)
        )
    }
    "Converts to TradeSideEntry" {
        entry.toTradeSideEntry() shouldBe TradeSideEntry(
            requestId = entry.requestId,
            whoRequested = entry.whoRequested,
            isQuote = entry.isQuote,
            entryType = entry.entryType,
            side = entry.side,
            sizes = EntrySizes(available = 23, traded = 2, cancelled = 0),
            price = entry.key.price,
            timeInForce = entry.timeInForce,
            whenSubmitted = entry.key.whenSubmitted,
            eventId = entry.key.eventId,
            status = EntryStatus.PARTIAL_FILL
        )
    }
    "Converts to OrderCancelledEvent" {
        val eventId = anEventId()
        val bookId = aBookId()
        val whenHappened = Instant.now()
        entry.toOrderCancelledEvent(eventId = eventId,
            bookId = bookId,
            whenHappened = whenHappened,
            reason = OrderCancelReason.CANCELLED_UPON_REQUEST) shouldBe OrderCancelledEvent(
            bookId = bookId,
            requestId = entry.requestId,
            whoRequested = entry.whoRequested,
            entryType = entry.entryType,
            side = entry.side,
            sizes = EntrySizes(available = 0, traded = 2, cancelled = 23),
            price = entry.key.price,
            timeInForce = entry.timeInForce,
            whenHappened = whenHappened,
            eventId = eventId,
            status = EntryStatus.CANCELLED,
            reason = OrderCancelReason.CANCELLED_UPON_REQUEST
        )
    }
    "Updates sizes and status when traded" {
        entry.traded(23) shouldBe entry.copy(
            sizes = EntrySizes(available = 0, traded = 25, cancelled = 0),
            status = EntryStatus.FILLED
        )
    }
    "Updates sizes and status when cancelled" {
        entry.cancelled() shouldBe entry.copy(
            sizes = EntrySizes(available = 0, traded = 2, cancelled = 23),
            status = EntryStatus.CANCELLED
        )
    }
})

internal class EarliestSubmittedTimeFirstTest : StringSpec({
    val entryKey = BookEntryKey(
        price = Price(10),
        whenSubmitted = Instant.now(),
        eventId = EventId(9)
    )

    "Evaluates earlier submitted time as before later" {
        EarliestSubmittedTimeFirst.compare(
            entryKey, entryKey.copy(whenSubmitted = entryKey.whenSubmitted.minusMillis(3))
        ) should beGreaterThan(0)
    }
    "Evaluates later submitted time as after earlier" {
        EarliestSubmittedTimeFirst.compare(
            entryKey, entryKey.copy(whenSubmitted = entryKey.whenSubmitted.plusMillis(3))
        ) should beLessThan(0)
    }
    "Evaluates same submitted time as the same" {
        EarliestSubmittedTimeFirst.compare(
            entryKey, entryKey.copy(price = Price(15), eventId = EventId(22))
        ) shouldBe 0
    }
})

internal class SmallestEventIdFirstTest : StringSpec({
    val entryKey = BookEntryKey(
        price = Price(10),
        whenSubmitted = Instant.now(),
        eventId = EventId(9)
    )

    "Evaluates smaller Event ID as before bigger" {
        SmallestEventIdFirst.compare(
            entryKey, entryKey.copy(eventId = EventId(8))
        ) should beGreaterThan(0)
    }
    "Evaluates bigger Event ID as after smaller" {
        SmallestEventIdFirst.compare(
            entryKey, entryKey.copy(eventId = EventId(10))
        ) should beLessThan(0)
    }
    "Evaluates same Event ID as the same" {
        SmallestEventIdFirst.compare(
            entryKey, entryKey.copy(price = Price(15), whenSubmitted = entryKey.whenSubmitted.plusMillis(3))
        ) shouldBe 0
    }
})

internal class HighestBuyOrLowestSellPriceFirstTest : StringSpec({
    val entryKey = BookEntryKey(
        price = Price(10),
        whenSubmitted = Instant.now(),
        eventId = EventId(9)
    )
    "Evaluates null BUY price as before non-null" {
        HighestBuyOrLowestSellPriceFirst(Side.BUY).compare(
            entryKey, entryKey.copy(price = null)
        ) should beGreaterThan(0)
    }
    "Evaluates higher BUY price as before lower" {
        HighestBuyOrLowestSellPriceFirst(Side.BUY).compare(
            entryKey, entryKey.copy(price = Price(11))
        ) should beGreaterThan(0)
    }
    "Evaluates lower BUY price as after higher" {
        HighestBuyOrLowestSellPriceFirst(Side.BUY).compare(
            entryKey, entryKey.copy(price = Price(9))
        ) should beLessThan(0)
    }
    "Evaluates same BUY prices as the same" {
        HighestBuyOrLowestSellPriceFirst(Side.BUY).compare(
            entryKey, entryKey.copy(eventId = EventId(8), whenSubmitted = entryKey.whenSubmitted.plusMillis(3))
        ) shouldBe 0
    }
    "Evaluates null SELL price as before non-null" {
        HighestBuyOrLowestSellPriceFirst(Side.SELL).compare(
            entryKey, entryKey.copy(price = null)
        ) should beGreaterThan(0)
    }
    "Evaluates lower SELL price as before higher" {
        HighestBuyOrLowestSellPriceFirst(Side.SELL).compare(
            entryKey, entryKey.copy(price = Price(9))
        ) should beGreaterThan(0)
    }
    "Evaluates higher SELL price as after lower" {
        HighestBuyOrLowestSellPriceFirst(Side.SELL).compare(
            entryKey, entryKey.copy(price = Price(11))
        ) should beLessThan(0)
    }
    "Evaluates same SELL prices as the same" {
        HighestBuyOrLowestSellPriceFirst(Side.SELL).compare(
            entryKey, entryKey.copy(eventId = EventId(8), whenSubmitted = entryKey.whenSubmitted.plusMillis(3))
        ) shouldBe 0
    }
})