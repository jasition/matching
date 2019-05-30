package jasition.matching.domain.scenario.trading

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.vavr.kotlin.list
import jasition.cqrs.EventId
import jasition.cqrs.commitOrThrow
import jasition.matching.domain.*
import jasition.matching.domain.book.entry.EntryType.LIMIT
import jasition.matching.domain.book.entry.EntryType.MARKET
import jasition.matching.domain.book.entry.Side.BUY
import jasition.matching.domain.book.entry.Side.SELL
import jasition.matching.domain.book.entry.TimeInForce.IMMEDIATE_OR_CANCEL

internal class `Order placed on and cancelled from empty book` : StringSpec({
    val bookId = aBookId()

    forall(
        row(BUY, MARKET, IMMEDIATE_OR_CANCEL, null),
        row(SELL, MARKET, IMMEDIATE_OR_CANCEL, null),
        row(BUY, LIMIT, IMMEDIATE_OR_CANCEL, randomPrice()),
        row(SELL, LIMIT, IMMEDIATE_OR_CANCEL, randomPrice())
    ) { side, entryType, timeInForce, price ->
        "Given an empty book, when a $side $entryType ${timeInForce.code} order is placed, then the order is cancelled" {
            val repo = aRepoWithABooks(bookId = bookId)
            val command = randomPlaceOrderCommand(
                bookId = bookId,
                side = side,
                entryType = entryType,
                timeInForce = timeInForce,
                price = price
            )

            val result = command.execute(repo.read(bookId)) commitOrThrow repo

            with(result) {
                events shouldBe list(
                    expectedOrderPlacedEvent(command, EventId(1)),
                    expectedOrderCancelledEvent(command, EventId(2))
                )
            }
            repo.read(bookId).let {
                with(command.side) {
                    sameSideBook(it).entries.size() shouldBe 0
                    oppositeSideBook(it).entries.size() shouldBe 0
                }
            }
        }
    }
})