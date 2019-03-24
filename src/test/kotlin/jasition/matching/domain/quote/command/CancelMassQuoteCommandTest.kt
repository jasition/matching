package jasition.matching.domain.quote.command

import arrow.core.Either
import io.kotlintest.matchers.beOfType
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.vavr.collection.List
import io.vavr.kotlin.list
import jasition.cqrs.EventId
import jasition.cqrs.Transaction
import jasition.matching.domain.*
import jasition.matching.domain.book.BookId
import jasition.matching.domain.book.Books
import jasition.matching.domain.book.BooksNotFoundException
import jasition.matching.domain.book.TradingStatus.SYSTEM_MAINTENANCE
import jasition.matching.domain.book.TradingStatuses
import jasition.matching.domain.book.entry.BookEntry
import jasition.matching.domain.book.entry.Side
import jasition.matching.domain.book.entry.SizeAtPrice
import jasition.matching.domain.book.entry.TimeInForce
import jasition.matching.domain.quote.QuoteModelType
import jasition.matching.domain.quote.QuoteRejectReason.*
import jasition.matching.domain.quote.event.MassQuoteCancelRejectedEvent
import jasition.matching.domain.quote.event.MassQuoteCancelledEvent
import java.time.Instant


internal class CancelMassQuoteCommandTest : StringSpec({
    val bookId = aBookId()
    val books = aBooks(bookId)
    val placeMassQuoteCommand = PlaceMassQuoteCommand(
        quoteId = "quote1",
        whoRequested = aFirmWithoutClient(),
        bookId = bookId,
        quoteModelType = QuoteModelType.QUOTE_ENTRY,
        timeInForce = TimeInForce.GOOD_TILL_CANCEL,
        entries = list(
            aQuoteEntry(
                bid = SizeAtPrice(size = randomSize(), price = randomPrice(from = 11, until = 12)),
                offer = SizeAtPrice(size = randomSize(), price = randomPrice(from = 13, until = 14))
            ),
            aQuoteEntry(
                bid = SizeAtPrice(size = randomSize(), price = randomPrice(from = 9, until = 10)),
                offer = SizeAtPrice(size = randomSize(), price = randomPrice(from = 15, until = 16))
            )
        ), whenRequested = Instant.now()
    )

    val command = CancelMassQuoteCommand(
        bookId = bookId,
        whoRequested = aFirmWithoutClient(),
        whenRequested = Instant.now()
    )

    "Exception if the books did not exist" {
        command.execute(null)
            .swap().toOption().orNull() should beOfType<BooksNotFoundException>()
    }

    "Reject if the symbol did not match" {
        command.copy(bookId = BookId("wrong book ID")).execute(books) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books.ofEventId(EventId(1)),
                events = list(
                    MassQuoteCancelRejectedEvent(
                        bookId = bookId,
                        eventId = EventId(1),
                        whoRequested = command.whoRequested,
                        whenHappened = command.whenRequested,
                        rejectReason = UNKNOWN_SYMBOL,
                        rejectText = "Unknown book ID : wrong book ID"
                    )
                )
            )
        )
    }

    "Reject if the trading status disallows" {
        command.execute(books.copy(tradingStatuses = TradingStatuses(SYSTEM_MAINTENANCE))) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books
                    .ofEventId(EventId(1))
                    .copy(tradingStatuses = TradingStatuses(SYSTEM_MAINTENANCE)),
                events = list(
                    MassQuoteCancelRejectedEvent(
                        bookId = bookId,
                        eventId = EventId(1),
                        whoRequested = command.whoRequested,
                        whenHappened = command.whenRequested,
                        rejectReason = EXCHANGE_CLOSED,
                        rejectText = "Placing mass quote is currently not allowed : SYSTEM_MAINTENANCE"
                    )
                )
            )
        )
    }

    "Reject if the no quote was found" {
        command.execute(books) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books.ofEventId(EventId(1)),
                events = list(
                    MassQuoteCancelRejectedEvent(
                        bookId = bookId,
                        eventId = EventId(1),
                        whoRequested = command.whoRequested,
                        whenHappened = command.whenRequested,
                        rejectReason = NO_QUOTE_FOUND,
                        rejectText = "No quote was found"
                    )
                )
            )
        )
    }

    "Existing quotes cancelled" {
        val existingBooks = aRepoWithABooks(
            bookId = bookId,
            commands = list(placeMassQuoteCommand)
        ).read(bookId)

        command.execute(existingBooks) shouldBe Either.right(
            Transaction<BookId, Books>(
                aggregate = books.copy(lastEventId = EventId(6)),
                events = list(
                    MassQuoteCancelledEvent(
                        bookId = bookId,
                        eventId = EventId(6),
                        whoRequested = command.whoRequested,
                        whenHappened = command.whenRequested,
                        entries = List.ofAll(
                            placeMassQuoteCommand.entries
                                .map {
                                    expectedBookEntry(
                                        command = placeMassQuoteCommand,
                                        eventId = EventId(1),
                                        side = Side.BUY,
                                        entry = it
                                    )
                                }
                                .map<BookEntry?>(::expectedCancelledBookEntry)
                                .appendAll(placeMassQuoteCommand.entries.map {
                                    expectedBookEntry(
                                        command = placeMassQuoteCommand,
                                        eventId = EventId(1),
                                        side = Side.SELL,
                                        entry = it
                                    )
                                }.map(::expectedCancelledBookEntry))
                        )
                    )
                )
            )
        )
    }

})