package jasition.matching.domain.book.entry

import jasition.cqrs.EventId
import jasition.matching.domain.book.BookId
import jasition.matching.domain.client.Client
import jasition.matching.domain.client.ClientRequestId
import jasition.matching.domain.order.event.OrderCancelReason
import jasition.matching.domain.order.event.OrderCancelledEvent
import jasition.matching.domain.trade.event.TradeSideEntry
import java.time.Instant

data class BookEntry(
    val key: BookEntryKey,
    val requestId: ClientRequestId,
    val whoRequested: Client,
    val isQuote: Boolean,
    val entryType: EntryType,
    val side: Side,
    val timeInForce: TimeInForce,
    val sizes: EntrySizes,
    val status: EntryStatus
) {
    constructor(
        price: Price?,
        whenSubmitted: Instant,
        eventId: EventId,
        requestId: ClientRequestId,
        whoRequested: Client,
        isQuote: Boolean,
        entryType: EntryType,
        side: Side,
        timeInForce: TimeInForce,
        sizes: EntrySizes,
        status: EntryStatus
    ) : this(
        key = BookEntryKey(price = price, whenSubmitted = whenSubmitted, eventId = eventId),
        requestId = requestId,
        whoRequested = whoRequested,
        isQuote = isQuote,
        entryType = entryType,
        side = side,
        timeInForce = timeInForce,
        sizes = sizes,
        status = status
    )

    fun toTradeSideEntry(eventId: EventId = key.eventId): TradeSideEntry = TradeSideEntry(
        requestId = requestId,
        whoRequested = whoRequested,
        isQuote = isQuote,
        entryType = entryType,
        side = side,
        sizes = sizes,
        price = key.price,
        timeInForce = timeInForce,
        whenSubmitted = key.whenSubmitted,
        eventId = eventId,
        status = status.traded(sizes)
    )

    fun toOrderCancelledEvent(
        eventId: EventId,
        bookId: BookId,
        whenHappened: Instant = key.whenSubmitted,
        reason : OrderCancelReason = OrderCancelReason.CANCELLED_BY_EXCHANGE
    ): OrderCancelledEvent = OrderCancelledEvent(
        eventId = eventId,
        requestId = requestId,
        whoRequested = whoRequested,
        bookId = bookId,
        entryType = entryType,
        side = side,
        sizes = sizes.cancelled(),
        price = key.price,
        timeInForce = timeInForce,
        status = EntryStatus.CANCELLED,
        whenHappened = whenHappened,
        reason = reason
    )

    fun traded(tradeSize: Int): BookEntry {
        val newSizes = sizes.traded(tradeSize)

        return copy(
            sizes = newSizes,
            status = status.traded(newSizes)
        )
    }

    fun cancelled(): BookEntry = copy(
        sizes = sizes.cancelled(),
        status = EntryStatus.CANCELLED
    )

    fun withKey(
        price: Price? = key.price,
        whenSubmitted: Instant = key.whenSubmitted,
        eventId: EventId = key.eventId
    ): BookEntry =
        copy(
            key = key.copy(
                price = price,
                whenSubmitted = whenSubmitted,
                eventId = eventId
            )
        )
}

data class BookEntryKey(
    val price: Price?,
    val whenSubmitted: Instant,
    val eventId: EventId
)

object EarliestSubmittedTimeFirst : Comparator<BookEntryKey> {
    override fun compare(o1: BookEntryKey, o2: BookEntryKey): Int = o1.whenSubmitted.compareTo(o2.whenSubmitted)
}

object SmallestEventIdFirst : Comparator<BookEntryKey> {
    override fun compare(o1: BookEntryKey, o2: BookEntryKey): Int = o1.eventId.compareTo(o2.eventId)
}

class HighestBuyOrLowestSellPriceFirst(val side: Side) : Comparator<BookEntryKey> {
    private val priceComparator = nullsFirst(PriceComparator(side.comparatorMultiplier()))

    override fun compare(o1: BookEntryKey, o2: BookEntryKey): Int =
        priceComparator.compare(o1.price, o2.price)
}

