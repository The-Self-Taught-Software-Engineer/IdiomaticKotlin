@file:Suppress("unused")

package codes.jakob.tstse.example.idiomatic.result

import codes.jakob.tstse.example.common.*
import codes.jakob.tstse.example.idiomatic.scopefunctions.DeveloperRepository
import mu.KLogger
import mu.KotlinLogging
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.Year

class PaycheckService(
    private val humanResourcesClient: HumanResourcesClient,
    private val paycheckRepository: PaycheckRepository,
    private val developerRepository: DeveloperRepository,
    private val clock: Clock,
) {
    /**
     * Returns an ordered list of [Paycheck]; sorted lexicographically by the [Name] of the [Developer].
     * Additionally, each paycheck, successfully generated or not, is persisted into the database.
     */
    fun generateAndPersistPaychecks(developerType: DeveloperType, year: Year, month: Month): List<Paycheck> {
        val today: LocalDate = LocalDate.now(clock)
        val period: LocalDateRange = determinePeriod(year, month)

        return developerRepository.findDevelopersByType(developerType)
            .filterNot { paycheckRepository.exists(it, period.range) }
            .mapNotNull { generateAndPersistPaycheck(today, period, it) }
            .sortedBy { it.developer.name }
            .also { logger.info { "Generated and persisted paychecks for ${it.count()} developers" } }

//        val (successfulPaychecks: List<Paycheck>, failedPaychecks: List<Developer>) =
//            developerRepository.findDevelopersByType(developerType)
//                .asSequence()
//                .filterNot { paycheckRepository.exists(it, period.range) }
//                .map { dev ->
//                    dev to Paycheck.Builder().apply {
//                        date = today
//                        period = period
//                        developer = dev
//                    }
//                }
//                .map { (dev, paycheckBuilder) ->
//                    try {
//                        dev to paycheckBuilder.apply {
//                            hourlyRate = humanResourcesClient.getHourlyRate(dev)
//                        }
//                    } catch (exception: HumanResourcesClient.HumanResourcesException) {
//                        logger.error(exception) {
//                            "Generation of ${Paycheck::class.simpleName} for '$dev' in period '$period' failed"
//                        }
//                        dev to null
//                    }
//                }.map { (dev, paycheckBuilder) ->
//                    try {
//                        dev to paycheckBuilder?.apply {
//                            hoursWorked = humanResourcesClient.getHoursWorked(dev, period.range)
//                        }
//                    } catch (exception: HumanResourcesClient.HumanResourcesException) {
//                        logger.error(exception) { "Generation of paycheck for '$dev' in period '$period' failed; persisting this" }
//                        dev to null
//                    }
//                }
//                .map { (dev, paycheckBuilder) ->
//                    try {
//                        dev to paycheckBuilder?.build()
//                    } catch (exception: IllegalStateException) {
//                        logger.error(exception) { "Generation of paycheck for '$dev' in period '$period' failed; persisting this" }
//                        dev to null
//                    }
//                }
//                .partition { (dev, paycheckBuilder) ->
//                    paycheckBuilder != null
//                }
//                .let { (success, failure) ->
//                    success.map { it.second!! } to failure.map { it.first }
//                }
//
//        successfulPaychecks.forEach { paycheck ->
//            paycheckRepository.save(paycheck)
//                .also { logger.info { "Generated and persisted paycheck for '${paycheck.developer}' in period '$period'" } }
//        }
//        failedPaychecks.forEach { dev ->
//            paycheckRepository.saveFailed(dev, period.range)
//        }
//
//        return successfulPaychecks
//            .sortedBy { it.developer.name }
//            .also { logger.info { "Generated and persisted paychecks for ${it.count()} developers" } }
    }

    private fun generateAndPersistPaycheck(
        today: LocalDate,
        paycheckPeriod: LocalDateRange,
        developer: Developer
    ): Paycheck? {
        val paycheckBuilder = Paycheck.Builder().apply {
            this.date = today
            this.period = paycheckPeriod
            this.developer = developer
        }
        return Result.success(paycheckBuilder)
            .mapCatching { paycheck ->
                paycheck.apply {
                    hourlyRate = humanResourcesClient.getHourlyRate(developer)
                }
            }
            .mapCatching { paycheck ->
                paycheck.apply {
                    hoursWorked = humanResourcesClient.getHoursWorked(developer, paycheckPeriod.range)
                }
            }
            .mapCatching { paycheck ->
                paycheck.build()
            }
            .onSuccess { paycheck ->
                paycheckRepository.save(paycheck)
                logger.info { "Generated and persisted paycheck for '$developer' in period '$paycheckPeriod'" }
            }
            .onFailure { exception ->
                logger.error(exception) { "Generation of paycheck for '$developer' in period '$paycheckPeriod' failed; persisting this" }
                paycheckRepository.saveFailed(developer, paycheckPeriod.range)
            }
            .getOrNull()
    }

    private fun determinePeriod(year: Year, month: Month): LocalDateRange {
        val firstDayInPeriod: LocalDate = LocalDate.of(year.value, month, 1)
        val lastDayInPeriod: LocalDate = firstDayInPeriod.withDayOfMonth(firstDayInPeriod.lengthOfMonth())
        return LocalDateRange(firstDayInPeriod.rangeTo(lastDayInPeriod))
    }

    companion object {
        private val logger: KLogger = KotlinLogging.logger {}
    }
}
