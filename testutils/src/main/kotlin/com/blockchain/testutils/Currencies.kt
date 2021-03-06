package com.blockchain.testutils

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import java.math.BigDecimal
import java.math.BigInteger

fun Number.gbp() = FiatValue.fromMajor("GBP", numberToBigDecimal())

fun Number.usd() = FiatValue.fromMajor("USD", numberToBigDecimal())

fun Number.cad() = FiatValue.fromMajor("CAD", numberToBigDecimal())

private fun Number.numberToBigDecimal(): BigDecimal =
    when (this) {
        is BigDecimal -> this
        is Double -> toBigDecimal()
        is Int -> toBigDecimal()
        is Long -> toBigDecimal()
        else -> throw NotImplementedError(this.javaClass.name)
    }

private fun Number.numberToBigInteger(): BigInteger =
    when (this) {
        is BigInteger -> this
        is Int -> toBigInteger()
        is Long -> toBigInteger()
        else -> throw NotImplementedError(this.javaClass.name)
    }

fun Number.bitcoin() = CryptoValue.fromMajor(CryptoCurrency.BTC, numberToBigDecimal())
fun Number.satoshi() = CryptoValue.fromMinor(CryptoCurrency.BTC, numberToBigInteger())
fun Number.ether() = CryptoValue.fromMajor(CryptoCurrency.ETHER, numberToBigDecimal())
fun Number.wei() = CryptoValue.fromMinor(CryptoCurrency.ETHER, numberToBigDecimal())
fun Number.gwei() = CryptoValue.fromMinor(CryptoCurrency.ETHER, numberToBigDecimal() * 1000000000.toBigDecimal())
fun Number.bitcoinCash() = CryptoValue.fromMajor(CryptoCurrency.BCH, numberToBigDecimal())
fun Number.satoshiCash() = CryptoValue.fromMinor(CryptoCurrency.BCH, numberToBigDecimal())
fun Number.lumens() = CryptoValue.fromMajor(CryptoCurrency.XLM, numberToBigDecimal())
fun Number.stroops() = CryptoValue.fromMinor(CryptoCurrency.XLM, numberToBigInteger())
fun Number.usdPax() = CryptoValue.fromMajor(CryptoCurrency.PAX, numberToBigDecimal())
fun Number.usdt() = CryptoValue.fromMajor(CryptoCurrency.USDT, numberToBigDecimal())
