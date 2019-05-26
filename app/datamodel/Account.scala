package datamodel

import scala.concurrent.{ExecutionContext, Future}

case class Account(userId : String, mobile: String) {
  def withdraw(amount: Amount)(implicit ec: ExecutionContext): Future[Boolean] = {
    //TODO : This is just for API, change it to actor model
    {
      for {
        balance <- AvailableBalance.getBalance(userId)
      } yield {
        if (amount.value > 0 && balance.balance > amount.value) {
          AvailableBalance(balance.balance - amount.value, amount.currencyCode).save(userId)
        } else {
          Future {
            false
          }
        }
      }
    }.flatten
  }

  def deposit(amount: Amount)(implicit ec: ExecutionContext): Future[Boolean] = {
    //TODO : This is just for API, change it to actor model
    {
      for {
        balance <- AvailableBalance.getBalance(userId)
      } yield {
        if(amount.value > 0) {
          AvailableBalance(balance.balance + amount.value, amount.currencyCode).save(userId)
        }else{
          Future(false)
        }
      }
    }.flatten
  }
}
