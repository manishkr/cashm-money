package datamodel

import databases.RedisDBHandler
import utils.{IdentityUtils, TransferStatus}

import scala.concurrent.{ExecutionContext, Future}

case class CouponTransfer(senderAccount : Account, payeeAccount : Account, amount : Amount) {
  def transfer()(implicit ec: ExecutionContext): Future[TransferStatus.Value] = {
    val result = AvailableBalance.transfer(senderAccount.userId, payeeAccount.userId, amount)
    result.map {
      case TransferStatus.Success =>
        Future {
          val senderTransId = IdentityUtils.generateTransactionId()
          val payeeTransId = IdentityUtils.generateTransactionId()
          val senderTrans = Transaction(senderAccount.userId, senderTransId, payeeTransId, Amount(amount.value * -1, amount.currencyCode))
          val payeeTrans = Transaction(payeeAccount.userId, payeeTransId, senderTransId, amount)
          senderTrans.save()
          payeeTrans.save()

          CouponTransfer.postProcess(senderTrans, senderAccount)
          CouponTransfer.postProcess(payeeTrans, payeeAccount)
        }
    }
    result
  }
}

object CouponTransfer{
  val collectionName = "CouponTransactionMap"
  def postProcess(transaction : Transaction, account: Account): Unit ={
    RedisDBHandler.set(collectionName, transaction.transactionId, account.mobile)
  }

  def get(transactionId: String): Future[Option[String]] = {
    RedisDBHandler.get[String](collectionName, transactionId)
  }
}