# Change log

* **2019-03-30 - Cancel Quote**
* **2019-03-07 - Added Market Order**
* **2019-03-05 - Major CQRS-ES refactor to get rid of recursion and business logic when handling events. Also this has speed up recovery by 10 times from my experiment**
* **2019-01-21 - Added Immediate-or-cancel time-in-force**
* **2019-01-20 - Added Micro-benchmarking**
* **2019-01-09 - Added Code Coverage check**
* **2019-01-06 - Added Mass Quote**
    * Mass Quote contains at least one level of bid and offer prices and sizes
    * Allow single-sided quoting
    * Support quote-entry model (full replacement)
    * Rejection of mass quote will cancel existing quotes
    * Prevent market makers matching each other
    * Allow matching between orders and quotes
    * Remove low-level or internal-state events
* **2018-12-29 - Supported the minimum features**
    * Order Type : Limit
    * Time-in-force : Good-till-cancel
    * Trading status : Open for trading, not available for trading, pre-open, halt
    * Four levels of trading status (from highest to lowest priority : Manual, fast market, schedule, default)
    * Aggressor takes better execution price
    * Partial and full fill
    * Wash trade avoidance
    * Multiple trades for one aggressor
    * Reject orders of negative size
    * Reject orders when effective trading status disallows 
    * Recover book state from re-playing primary events
* **2018-12-16 - Created the project**