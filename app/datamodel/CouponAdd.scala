package datamodel

import utils.CurrencyCode.CurrencyCode
import utils.{IdentityUtils, TransferStatus}

import scala.concurrent.{ExecutionContext, Future}

case class CouponAdd(payeeAccount : Account, amount : Amount, paymentReferenceId : String) {
  def save()(implicit ec: ExecutionContext): Future[TransferStatus.Value] =
    Future {
      val payeeTransId = IdentityUtils.generateTransactionId()
      payeeAccount.deposit(amount)
      Transaction(payeeAccount.userId, payeeTransId, paymentReferenceId, amount).save()
      TransferStatus.Success
    }
}
