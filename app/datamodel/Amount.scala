package datamodel

import org.mongodb.scala.bson.collection.immutable.Document
import utils.CurrencyCode.CurrencyCode

case class Amount(value: Long, currencyCode: CurrencyCode) {
  def toDocument = Document("value" -> value, "currencyCode" -> currencyCode.id)

  def +(that: Amount): Amount = this.currencyCode == that.currencyCode match {
    case true => Amount(this.value + that.value, this.currencyCode)
    case false => assert(false)
      Amount(this.value + that.value, this.currencyCode)
  }

  def -(that: Amount): Amount = this.currencyCode == that.currencyCode match {
    case true => Amount(this.value - that.value, this.currencyCode)
    case false => assert(false)
      Amount(this.value - that.value, this.currencyCode)
  }

  def <=(that: Amount): Boolean = this.currencyCode == that.currencyCode match {
    case true => this.value <= that.value
    case false => false
  }
}