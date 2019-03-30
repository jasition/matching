package jasition.matching.domain.book

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.mockk.mockk
import jasition.matching.domain.book.TradingStatus.*
import jasition.matching.domain.order.command.PlaceOrderCommand
import jasition.matching.domain.quote.command.CancelMassQuoteCommand
import jasition.matching.domain.quote.command.PlaceMassQuoteCommand

internal class TradingStatusesTest : StringSpec({
    "Prioritises manual status over fast market"{
        TradingStatuses(
            default = NOT_AVAILABLE_FOR_TRADING,
            scheduled = PRE_OPEN,
            fastMarket = HALTED,
            manual = OPEN_FOR_TRADING
        ).effectiveStatus() shouldBe OPEN_FOR_TRADING
    }
    "Prioritises fast market status over scheduled"{
        TradingStatuses(
            default = NOT_AVAILABLE_FOR_TRADING,
            scheduled = PRE_OPEN,
            fastMarket = HALTED
        ).effectiveStatus() shouldBe HALTED
    }
    "Prioritises scheduled status over default"{
        TradingStatuses(
            default = NOT_AVAILABLE_FOR_TRADING,
            scheduled = PRE_OPEN
        ).effectiveStatus() shouldBe PRE_OPEN
    }
    "Uses default status when all else is absent"{
        TradingStatuses(
            default = NOT_AVAILABLE_FOR_TRADING
        ).effectiveStatus() shouldBe NOT_AVAILABLE_FOR_TRADING
    }
})

internal class TradingStatusTest : StringSpec({
    forall(
        row(OPEN_FOR_TRADING, true),
        row(HALTED, false),
        row(NOT_AVAILABLE_FOR_TRADING, false),
        row(PRE_OPEN, false),
        row(SYSTEM_MAINTENANCE, false)
    ) { tradingStatus, allowed ->
        "$tradingStatus ${if (allowed) "" else "dis"}allows PlaceOrderCommand"{
            tradingStatus.allows(mockk<PlaceOrderCommand>()) shouldBe allowed
        }
    }
    forall(
        row(OPEN_FOR_TRADING, true),
        row(HALTED, false),
        row(NOT_AVAILABLE_FOR_TRADING, false),
        row(PRE_OPEN, true),
        row(SYSTEM_MAINTENANCE, false)
    ) { tradingStatus, allowed ->
        "$tradingStatus ${if (allowed) "" else "dis"}allows PlaceMassQuoteCommand"{
            tradingStatus.allows(mockk<PlaceMassQuoteCommand>()) shouldBe allowed
        }
    }
    forall(
        row(OPEN_FOR_TRADING, true),
        row(HALTED, true),
        row(NOT_AVAILABLE_FOR_TRADING, true),
        row(PRE_OPEN, true),
        row(SYSTEM_MAINTENANCE, false)
    ) { tradingStatus, allowed ->
        "$tradingStatus ${if (allowed) "" else "dis"}allows CancelMassQuoteCommand"{
            tradingStatus.allows(mockk<CancelMassQuoteCommand>()) shouldBe allowed
        }
    }
})