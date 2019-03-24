package jasition.matching.domain.book

import jasition.matching.domain.order.command.PlaceOrderCommand
import jasition.matching.domain.quote.command.CancelMassQuoteCommand
import jasition.matching.domain.quote.command.PlaceMassQuoteCommand

data class TradingStatuses(
    val default: TradingStatus,
    val scheduled: TradingStatus? = null,
    val fastMarket: TradingStatus? = null,
    val manual: TradingStatus? = null
) {
    fun effectiveStatus(): TradingStatus = manual ?: fastMarket ?: scheduled ?: default
}

enum class TradingStatus {
    OPEN_FOR_TRADING {
        override fun allows(command: PlaceMassQuoteCommand): Boolean = true
        override fun allows(command: PlaceOrderCommand): Boolean = true
        override fun allows(command: CancelMassQuoteCommand): Boolean = true
    },
    HALTED {
        override fun allows(command: PlaceMassQuoteCommand): Boolean = false
        override fun allows(command: PlaceOrderCommand): Boolean = false
        override fun allows(command: CancelMassQuoteCommand): Boolean = true
    },
    NOT_AVAILABLE_FOR_TRADING {
        override fun allows(command: PlaceMassQuoteCommand): Boolean = false
        override fun allows(command: PlaceOrderCommand): Boolean = false
        override fun allows(command: CancelMassQuoteCommand): Boolean = true
    },
    PRE_OPEN {
        override fun allows(command: PlaceMassQuoteCommand): Boolean = true
        override fun allows(command: PlaceOrderCommand): Boolean = false
        override fun allows(command: CancelMassQuoteCommand): Boolean = true
    },
    SYSTEM_MAINTENANCE {
        override fun allows(command: PlaceOrderCommand): Boolean = false
        override fun allows(command: PlaceMassQuoteCommand): Boolean = false
        override fun allows(command: CancelMassQuoteCommand): Boolean = false
    };

    abstract fun allows(command: PlaceOrderCommand): Boolean
    abstract fun allows(command: PlaceMassQuoteCommand): Boolean
    abstract fun allows(command: CancelMassQuoteCommand): Boolean
}