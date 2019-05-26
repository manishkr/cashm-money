package datamodel

import akka.actor.Actor
import akka.event.LoggingReceive
import utils.CurrencyCode

class KuberAccount extends Actor {

  import KuberAccount._

  var balance = Amount(0L, CurrencyCode.INR)

  def receive = LoggingReceive {
    case Deposit(amount) =>
      balance += amount
      sender ! Done

    case Withdraw(amount) if amount <= balance =>
      balance -= amount
      sender ! Done

    case _ => sender ! Failed
  }
}


object KuberAccount {

  case class Deposit(amount: Amount) {
    require(amount.value > 0)
  }

  case class Withdraw(amount: Amount) {
    require(amount.value > 0)
  }

  case object Done

  case object Failed

}
