package datamodel

import akka.util.Timeout
import com.redis.serialization.Format
import databases.RedisDBHandler
import play.api.libs.json
import play.api.libs.json.Json
import utils.CurrencyCode.CurrencyCode
import utils.{CurrencyCode, TransferStatus, Utils}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

case class AvailableBalance(balance: Long, currencyCode: CurrencyCode){
  implicit val customFormat: Format[AvailableBalance] =
    new Format[AvailableBalance] {
      def read(str: String): AvailableBalance = {
        val list = str.split('|').toList
        val balance = list.head.toLong
        val currencyCode = list.tail.asInstanceOf[CurrencyCode]

        AvailableBalance(balance, currencyCode)
      }

      def write(availableBalance: AvailableBalance): String = {
        s"${availableBalance.balance}|${availableBalance.currencyCode}"
      }
    }

  def save(userId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
     RedisDBHandler.set(AvailableBalance.collectionName, userId, this)
  }
}

object AvailableBalance {
  private val collectionName = "AvailableBalance"
  val zeroBalance = AvailableBalance(0L, CurrencyCode.INR)
  implicit val customFormat: Format[AvailableBalance] =
    new Format[AvailableBalance] {
      def read(str: String): AvailableBalance = {
        val list = str.split('|').toList
        val balance = list.head.toLong
        //println(list.tail.head)
        val currencyCode = CurrencyCode.withName(list.tail.head)

        AvailableBalance(balance, currencyCode)
      }

      def write(availableBalance: AvailableBalance): String = {
        s"${availableBalance.balance}|${availableBalance.currencyCode}"
      }
    }

  def getBalance(userId: String)(implicit ec: ExecutionContext): Future[AvailableBalance] = {
    RedisDBHandler.get[AvailableBalance](collectionName, userId).map(_.getOrElse(zeroBalance))
  }

  def transfer(senderUserId: String, payeeUserId: String, amount: Amount)(implicit ec: ExecutionContext): Future[TransferStatus.Value] = {
    implicit val timeout: Timeout = 5 seconds

    if (senderUserId == payeeUserId) {
      return Future(TransferStatus.SameAccount)
    }
    if (amount.value <= 0L) {
      return Future(TransferStatus.NegtaitveAmount)
    }

    val senderKey = RedisDBHandler.getCompositeKey(collectionName, senderUserId)
    val payeeKey = RedisDBHandler.getCompositeKey(collectionName, payeeUserId)
    val result = for {
      availableBalance <- AvailableBalance.getBalance(senderUserId)
      payeeBalance <- AvailableBalance.getBalance(payeeUserId)
    } yield {
      if (availableBalance.balance >= amount.value && availableBalance.currencyCode == payeeBalance.currencyCode && availableBalance.currencyCode == amount.currencyCode) {

        RedisDBHandler.getClient.watch(senderKey, payeeKey) //TODO: How to unwatch it
        RedisDBHandler.getClient.withTransaction { c =>
          c.set(senderKey, AvailableBalance(availableBalance.balance - amount.value, availableBalance.currencyCode))
          c.set(payeeKey, AvailableBalance(payeeBalance.balance + amount.value, payeeBalance.currencyCode))
        }.map { result =>
          val list = result.asInstanceOf[List[Boolean]].toVector
          if (list(0)) {
            list(0) == list(1) match {
              case true => TransferStatus.Success;
              case false =>
                println("[Danger] Something terriably wrong")
                TransferStatus.Failed
            }
          } else {
            TransferStatus.Failed
          }
        }
      } else {
        Future(TransferStatus.InsufficentFund)
      }
    } fallbackTo {
      Future(TransferStatus.Failed)
    }

    val r = result.flatten

    val res = Await.result(r, 2 seconds)

    Future(res)
  }

  def getBalanceAsJson(userId: String)(implicit ec: ExecutionContext): Future[json.JsObject] = {
    getBalance(userId).map { data => Json.obj("balance" -> data.balance, "currency_code" -> data.currencyCode)
    }
  }

  def getTotal()(implicit ec: ExecutionContext): Future[BigInt] = {
    var total = BigInt(0)
    RedisDBHandler.getAllKeysIn(AvailableBalance.collectionName)
      .map {
        _.map { key =>
          RedisDBHandler.getValue[AvailableBalance](key).map(_.getOrElse(zeroBalance))
        }
      }
      .map { future => Utils.futureToList[AvailableBalance](future, zeroBalance) }
      .flatten
      .map(_.foldLeft(BigInt(0))(_ + _.balance))
  }
}