package jasition.matching.domain.quote.command

import arrow.core.Either
import io.vavr.collection.List
import io.vavr.collection.Seq
import io.vavr.kotlin.list
import jasition.cqrs.*
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.BooksNotFoundException
import jasition.matching.domain.book.entry.SizeAtPrice
import jasition.matching.domain.book.entry.TimeInForce
import jasition.matching.domain.client.Client
import jasition.matching.domain.quote.QuoteEntry
import jasition.matching.domain.quote.QuoteModelType
import jasition.matching.domain.quote.QuoteRejectReason
import jasition.matching.domain.quote.QuoteRejectReason.*
import jasition.matching.domain.quote.cancelExistingQuotes
import jasition.matching.domain.quote.event.MassQuotePlacedEvent
import jasition.matching.domain.quote.event.MassQuoteRejectedEvent
import jasition.matching.domain.trade.matchAndFinalise
import jasition.monad.appendIfNotNullOrBlank
import jasition.monad.ifNotEqualsThenUse
import java.time.Instant
import java.util.function.BiFunction

data class PlaceMassQuoteCommand(
    val quoteId: String,
    val whoRequested: Client,
    val bookId: BookId,
    val quoteModelType: QuoteModelType = QuoteModelType.QUOTE_ENTRY,
    val timeInForce: TimeInForce = TimeInForce.GOOD_TILL_CANCEL,
    val entries: Seq<QuoteEntry>,
    val whenRequested: Instant
) : Command<BookId, Books> {
    private val validation = CompleteValidation(list(
        SymbolMustMatch,
        TradingStatusAllows,
        SizesAreCorrect,
        NoCrossedPrices
    ), BiFunction { left, right ->
        right.copy(
            rejectReason = ifNotEqualsThenUse(left.rejectReason, right.rejectReason, OTHER),
            rejectText = appendIfNotNullOrBlank(left.rejectText, right.rejectText, "; ")
        )
    })

    override fun execute(aggregate: Books?): Either<Exception, Transaction<BookId, Books>> {
        if (aggregate == null) return Either.left(BooksNotFoundException("Books $bookId not found"))

        val cancelledEvent = cancelExistingQuotes(
            books = aggregate,
            eventId = aggregate.lastEventId,
            whoRequested = whoRequested,
            whenHappened = whenRequested
        )

        //TODO: Replace by Elvis operator when its code coverage can be accurately measured
        val events =
            if (cancelledEvent != null)
                List.of<Event<BookId, Books>>(cancelledEvent)
            else List.empty()

        ///TODO: Replace by Elvis operator when its code coverage can be accurately measured
        val cancelledBooks =
            if (cancelledEvent != null)
                cancelledEvent.play(aggregate)
            else aggregate

        validation.validate(this, cancelledBooks)?.run {
            return Either.right(Transaction(play(cancelledBooks), events.append(this)))
        }

        val placedEvent = toPlacedEvent(cancelledBooks)

        return Either.right(placedEvent.toBookEntries().fold(
            Transaction(placedEvent.play(cancelledBooks), events.append(placedEvent))
        ) { txn, entry ->
            txn append matchAndFinalise(entry, txn.aggregate)
        })
    }

    private fun lowestSellPrice(): Long? =
        entries.map { it.offer }
            .filterNotNull()
            .map { it.price.value }
            .min()

    private fun highestBuyPrice(): Long? =
        entries.map { it.bid }
            .filterNotNull()
            .map { it.price.value }
            .max()

    private fun findNonPositiveSize(quoteEntry: QuoteEntry): Int? =
        findNonPositiveSize(quoteEntry.bid) ?: findNonPositiveSize(quoteEntry.offer)

    private fun findNonPositiveSize(sizeAtPrice: SizeAtPrice?): Int? =
        sizeAtPrice?.let { if (it.size <= 0) it.size else null }

    private fun toRejectedEvent(
        books: Books,
        quoteRejectReason: QuoteRejectReason,
        quoteRejectText: String
    ): MassQuoteRejectedEvent = MassQuoteRejectedEvent(
        eventId = books.lastEventId.inc(),
        bookId = books.bookId,
        quoteId = quoteId,
        whoRequested = whoRequested,
        quoteModelType = quoteModelType,
        timeInForce = timeInForce,
        entries = entries,
        whenHappened = whenRequested,
        rejectReason = quoteRejectReason,
        rejectText = quoteRejectText
    )

    private fun toPlacedEvent(books: Books): MassQuotePlacedEvent = MassQuotePlacedEvent(
        eventId = books.lastEventId.inc(),
        bookId = books.bookId,
        quoteId = quoteId,
        whoRequested = whoRequested,
        quoteModelType = quoteModelType,
        timeInForce = timeInForce,
        entries = entries,
        whenHappened = whenRequested
    )

    object SymbolMustMatch : Validation<BookId, Books, PlaceMassQuoteCommand, MassQuoteRejectedEvent> {
        override fun validate(command: PlaceMassQuoteCommand, aggregate: Books): MassQuoteRejectedEvent? =
            if (command.bookId != aggregate.bookId)
                command.toRejectedEvent(
                    books = aggregate,
                    quoteRejectReason = UNKNOWN_SYMBOL,
                    quoteRejectText = "Unknown book ID : ${command.bookId.bookId}"
                )
            else null
    }

    object TradingStatusAllows : Validation<BookId, Books, PlaceMassQuoteCommand, MassQuoteRejectedEvent> {
        override fun validate(command: PlaceMassQuoteCommand, aggregate: Books): MassQuoteRejectedEvent? =
            if (!aggregate.tradingStatuses.effectiveStatus().allows(command))
                command.toRejectedEvent(
                    books = aggregate,
                    quoteRejectReason = EXCHANGE_CLOSED,
                    quoteRejectText = "Placing mass quote is currently not allowed : ${aggregate.tradingStatuses.effectiveStatus()}"
                )
            else null
    }

    object NoCrossedPrices : Validation<BookId, Books, PlaceMassQuoteCommand, MassQuoteRejectedEvent> {
        override fun validate(command: PlaceMassQuoteCommand, aggregate: Books): MassQuoteRejectedEvent? {
            val lowestSellPrice = command.lowestSellPrice()
            val highestBuyPrice = command.highestBuyPrice()
            return if (lowestSellPrice ?: Long.MAX_VALUE <= highestBuyPrice ?: Long.MIN_VALUE)
                command.toRejectedEvent(
                    books = aggregate,
                    quoteRejectReason = INVALID_BID_ASK_SPREAD,
                    quoteRejectText = "Quote prices must not cross within a mass quote: lowestSellPrice=$lowestSellPrice, highestBuyPrice=$highestBuyPrice"
                )
            else null
        }
    }

    object SizesAreCorrect : Validation<BookId, Books, PlaceMassQuoteCommand, MassQuoteRejectedEvent> {
        override fun validate(command: PlaceMassQuoteCommand, aggregate: Books): MassQuoteRejectedEvent? =
            command.entries.map { command.findNonPositiveSize(it) }
                .filterNotNull()
                .min()?.let {
                    command.toRejectedEvent(
                        books = aggregate,
                        quoteRejectReason = INVALID_QUANTITY,
                        quoteRejectText = "Quote sizes must be positive : $it"
                    )
                }
    }
}
