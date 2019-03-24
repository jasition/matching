package jasition.matching.domain

import jasition.cqrs.EventId
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.entry.*
import jasition.matching.domain.book.entry.EntryStatus.CANCELLED
import jasition.matching.domain.book.entry.EntryStatus.NEW
import jasition.matching.domain.client.ClientRequestId
import jasition.matching.domain.order.command.PlaceOrderCommand
import jasition.matching.domain.order.event.OrderCancelledByExchangeEvent
import jasition.matching.domain.order.event.OrderPlacedEvent
import jasition.matching.domain.quote.QuoteEntry
import jasition.matching.domain.quote.QuoteRejectReason
import jasition.matching.domain.quote.command.PlaceMassQuoteCommand
import jasition.matching.domain.quote.event.MassQuotePlacedEvent
import jasition.matching.domain.quote.event.MassQuoteRejectedEvent
import jasition.matching.domain.trade.event.TradeSideEntry

fun expectedBookEntry(
    event: OrderPlacedEvent,
    eventId: EventId = event.eventId,
    status: EntryStatus = event.status,
    sizes: EntrySizes = event.sizes
) = BookEntry(
    price = event.price,
    whenSubmitted = event.whenHappened,
    eventId = eventId,
    requestId = event.requestId,
    whoRequested = event.whoRequested,
    isQuote = false,
    entryType = event.entryType,
    side = event.side,
    timeInForce = event.timeInForce,
    sizes = sizes,
    status = status
)

fun expectedBookEntry(
    command: PlaceOrderCommand,
    eventId: EventId,
    sizes: EntrySizes = EntrySizes(available = command.size, traded = 0, cancelled = 0),
    status: EntryStatus = NEW
): BookEntry = with(command) {
    BookEntry(
        eventId = eventId,
        requestId = requestId,
        price = price,
        whenSubmitted = whenRequested,
        whoRequested = whoRequested,
        isQuote = false,
        entryType = entryType,
        side = side,
        timeInForce = timeInForce,
        sizes = sizes,
        status = status
    )
}

fun expectedBookEntry(
    command: PlaceMassQuoteCommand,
    eventId: EventId,
    entryIndex: Int,
    side: Side,
    sizes: EntrySizes = EntrySizes(side.sizeAtPrice(command.entries[entryIndex])?.size ?: 0),
    status: EntryStatus = NEW
): BookEntry =
    with(command) {
        BookEntry(
            price = side.sizeAtPrice(entries[entryIndex])?.price,
            whenSubmitted = whenRequested,
            eventId = eventId,
            requestId = ClientRequestId(
                current = entries[entryIndex].quoteEntryId,
                collectionId = entries[entryIndex].quoteSetId,
                parentId = quoteId
            ),
            whoRequested = whoRequested,
            isQuote = true,
            entryType = EntryType.LIMIT,
            side = side,
            timeInForce = timeInForce,
            sizes = sizes,
            status = status
        )
    }

fun expectedBookEntry(
    command: PlaceMassQuoteCommand,
    eventId: EventId,
    entry: QuoteEntry,
    side: Side,
    sizes: EntrySizes = EntrySizes(side.sizeAtPrice(entry)?.size ?: 0),
    status: EntryStatus = NEW
): BookEntry =
    with(command) {
        BookEntry(
            price = side.sizeAtPrice(entry)?.price,
            whenSubmitted = whenRequested,
            eventId = eventId,
            requestId = ClientRequestId(
                current = entry.quoteEntryId,
                collectionId = entry.quoteSetId,
                parentId = quoteId
            ),
            whoRequested = whoRequested,
            isQuote = true,
            entryType = EntryType.LIMIT,
            side = side,
            timeInForce = timeInForce,
            sizes = sizes,
            status = status
        )
    }

fun expectedBookEntry(
    event: MassQuotePlacedEvent,
    quoteEntry: QuoteEntry,
    side: Side,
    eventId: EventId = event.eventId,
    sizes: EntrySizes = EntrySizes(side.sizeAtPrice(quoteEntry)!!.size),
    status: EntryStatus = NEW
): BookEntry =
    BookEntry(
        price = side.sizeAtPrice(quoteEntry)?.price,
        whenSubmitted = event.whenHappened,
        eventId = eventId,
        requestId = expectedClientRequestId(event, quoteEntry),
        whoRequested = event.whoRequested,
        isQuote = true,
        entryType = EntryType.LIMIT,
        side = side,
        timeInForce = event.timeInForce,
        sizes = sizes,
        status = status
    )

fun expectedTradeSideEntry(
    event: MassQuotePlacedEvent,
    quoteEntry: QuoteEntry,
    side: Side,
    eventId: EventId = event.eventId,
    sizes: EntrySizes,
    status: EntryStatus
): TradeSideEntry {
    return TradeSideEntry(
        requestId = expectedClientRequestId(event, quoteEntry),
        whoRequested = event.whoRequested,
        isQuote = true,
        entryType = EntryType.LIMIT,
        sizes = sizes,
        side = side,
        price = side.sizeAtPrice(quoteEntry)?.price,
        timeInForce = event.timeInForce,
        whenSubmitted = event.whenHappened,
        eventId = eventId,
        status = status
    )
}

fun expectedClientRequestId(
    event: MassQuotePlacedEvent,
    quoteEntry: QuoteEntry
): ClientRequestId = ClientRequestId(
    current = quoteEntry.quoteEntryId,
    collectionId = quoteEntry.quoteSetId,
    parentId = event.quoteId
)

fun expectedTradeSideEntry(
    event: OrderPlacedEvent,
    eventId: EventId = event.eventId,
    sizes: EntrySizes,
    status: EntryStatus
): TradeSideEntry {
    return TradeSideEntry(
        requestId = event.requestId,
        whoRequested = event.whoRequested,
        isQuote = false,
        entryType = event.entryType,
        sizes = sizes,
        side = event.side,
        price = event.price,
        timeInForce = event.timeInForce,
        whenSubmitted = event.whenHappened,
        eventId = eventId,
        status = status
    )
}

fun expectedTradeSideEntry(
    bookEntry: BookEntry,
    eventId: EventId = bookEntry.key.eventId,
    sizes: EntrySizes = bookEntry.sizes,
    status: EntryStatus = bookEntry.status
): TradeSideEntry = with(bookEntry) {
    TradeSideEntry(
        requestId = requestId,
        whoRequested = whoRequested,
        isQuote = isQuote,
        entryType = entryType,
        sizes = sizes,
        side = side,
        price = key.price,
        timeInForce = timeInForce,
        whenSubmitted = key.whenSubmitted,
        eventId = eventId,
        status = status
    )
}

fun expectedOrderCancelledByExchangeEvent(
    entry: BookEntry,
    eventId: EventId,
    bookId: BookId,
    tradedSize: Int = entry.sizes.traded,
    cancelledSize: Int = entry.sizes.available + entry.sizes.cancelled
): OrderCancelledByExchangeEvent {
    return OrderCancelledByExchangeEvent(
        eventId = eventId,
        requestId = entry.requestId,
        whoRequested = entry.whoRequested,
        bookId = bookId,
        entryType = entry.entryType,
        side = entry.side,
        sizes = EntrySizes(
            available = 0,
            traded = tradedSize,
            cancelled = cancelledSize
        ),
        price = entry.key.price,
        timeInForce = entry.timeInForce,
        status = EntryStatus.CANCELLED,
        whenHappened = entry.key.whenSubmitted
    )
}

fun expectedOrderPlacedEvent(
    command: PlaceOrderCommand,
    eventId: EventId,
    sizes: EntrySizes = EntrySizes(
        available = command.size,
        traded = 0,
        cancelled = 0
    ),
    status: EntryStatus = NEW
): OrderPlacedEvent {
    with(command) {
        return OrderPlacedEvent(
            bookId = bookId,
            eventId = eventId,
            requestId = requestId,
            price = price,
            whenHappened = whenRequested,
            whoRequested = whoRequested,
            entryType = entryType,
            side = side,
            timeInForce = timeInForce,
            sizes = sizes,
            status = status
        )
    }
}

fun expectedOrderCancelledByExchangeEvent(
    command: PlaceOrderCommand,
    eventId: EventId,
    sizes: EntrySizes = EntrySizes(
        available = 0,
        traded = 0,
        cancelled = command.size
    )
): OrderCancelledByExchangeEvent = with(command) {
    OrderCancelledByExchangeEvent(
        bookId = bookId,
        eventId = eventId,
        requestId = requestId,
        whoRequested = whoRequested,
        entryType = entryType,
        side = side,
        sizes = sizes,
        price = price,
        timeInForce = timeInForce,
        status = EntryStatus.CANCELLED,
        whenHappened = whenRequested
    )
}

fun expectedMassQuotePlacedEvent(
    command: PlaceMassQuoteCommand,
    eventId: EventId
): MassQuotePlacedEvent = with(command) {
    MassQuotePlacedEvent(
        bookId = bookId,
        eventId = eventId,
        quoteId = quoteId,
        whenHappened = whenRequested,
        whoRequested = whoRequested,
        quoteModelType = quoteModelType,
        timeInForce = timeInForce,
        entries = entries
    )
}

fun expectedMassQuoteRejectedEvent(
    bookId: BookId,
    eventId: EventId,
    command: PlaceMassQuoteCommand,
    expectedQuoteRejectReason: QuoteRejectReason,
    expectedQuoteRejectText: String
): MassQuoteRejectedEvent = with(command) {
    MassQuoteRejectedEvent(
        bookId = bookId,
        eventId = eventId,
        quoteId = quoteId,
        whoRequested = whoRequested,
        quoteModelType = quoteModelType,
        timeInForce = timeInForce,
        entries = entries,
        whenHappened = whenRequested,
        rejectReason = expectedQuoteRejectReason,
        rejectText = expectedQuoteRejectText
    )
}

fun expectedCancelledBookEntry(entry: BookEntry): BookEntry {
    return entry.copy(
        status = CANCELLED,
        sizes = EntrySizes(
            available = 0,
            traded = entry.sizes.traded,
            cancelled = entry.sizes.cancelled + entry.sizes.available
        )
    )
}