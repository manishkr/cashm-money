package datamodel

import databases.MongoDBHandler
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.{JsObject, Json}
import reportprocessor.ReportHandler

import scala.concurrent.{ExecutionContext, Future}

case class Transaction(userId : String, transactionId: String, referenceId: String, amount: Amount) {
  def save()(implicit ec: ExecutionContext): Future[Boolean] = {
    val document = Document("userId" -> userId, "transactionId" -> transactionId, "referenceId" -> referenceId, "amount" -> amount.value,
      "currencyCode" -> amount.currencyCode.toString)
    Future{ReportHandler.handle(this)}
    MongoDBHandler.insertOne(Transaction.collectionName, document).map(_ => true)
  }
}

object Transaction{
  val collectionName = "Transaction"

  def createIndex()(implicit ec: ExecutionContext): Unit = MongoDBHandler.createIndex(Transaction.collectionName, Document("userId" -> 1))

  def get(userId: String, size: Int, page: Int)(implicit ec: ExecutionContext): Future[Seq[JsObject]] = {
    MongoDBHandler.find(Transaction.collectionName, Document("userId" -> userId), size, page)
      .map(_.map(doc => jsonify(doc)))
      .map(doc => Future.sequence(doc))
      .flatten
  }

  def getTotalCount(userId : String)(implicit ec: ExecutionContext): Future[Long] = {
    MongoDBHandler.count(Transaction.collectionName, Document("userId" -> userId))
  }

  private def jsonify(document: Document)(implicit ec: ExecutionContext) = {
    CouponTransfer.get(document.getString("referenceId")).map {
      _.getOrElse("00000000")
    }.map {mobile =>
      Json.obj("transactionId" -> document.getString("transactionId"), "referenceId" -> document.getString("referenceId"), "amount" -> document.getLong("amount").toLong,
        "currencyCode" -> document.getString("currencyCode"), "otherPartyMobile" -> mobile, "otherPartyName" -> "", "creationTime" -> document.getLong("creationTime").toLong)
    }
  }
}