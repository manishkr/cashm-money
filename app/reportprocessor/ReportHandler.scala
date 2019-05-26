package reportprocessor
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.{Calendar, Date, TimeZone}

import databases.{MongoDBHandler, MongoDBIndex}
import datamodel.{CouponTransfer, Transaction}
import org.mongodb.scala.Completed
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.{JsObject, Json}
import utils.Utils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object ReportHandler extends MongoDBIndex {
  val collectionName = "TransactionReport"

  def createIndex()(implicit ec: ExecutionContext): Unit = {
    MongoDBHandler.createIndex(Transaction.collectionName, Document("userId" -> 1, "time" -> 1))
  }

  //TODO : Change it more generic reporting, this is temp code
  def handle(transaction: Transaction)(implicit ec: ExecutionContext): Future[Future[Completed]] = {
    val date = new Date(Instant.now.getEpochSecond * 1000)
    val format = new SimpleDateFormat("dd/MM/yyyy/HH/mm/ss")
    format.setTimeZone(TimeZone.getTimeZone("Asia/Calcutta"))
    val formatted = format.format(date)
    val timeSeq = formatted.split("/")
    assert(timeSeq.length == 6)
    val timeDoc = Document("day" -> timeSeq(0).toInt, "month" -> timeSeq(1).toInt, "year" -> timeSeq(2).toInt, "hour" -> timeSeq(3).toInt,
      "minute" -> timeSeq(4).toInt, "second" -> timeSeq(5).toInt)

    CouponTransfer.get(transaction.referenceId).map {
      _.getOrElse("00000000")
    }.map { mobile => {
      val doc = Document("time" -> timeDoc, "userId" -> transaction.userId, "transactionId" -> transaction.transactionId, "amount" -> transaction.amount.value,
        "currencyCode" -> transaction.amount.currencyCode.toString, "otherName" -> "test", "otherMobile" -> mobile)
      MongoDBHandler.insertOne(collectionName, doc)
    }
    }
  }

  def getHourly(userId: String, date: String, startHour: Int, endHour: Int)(implicit ec: ExecutionContext): Future[Seq[JsObject]] = {
    val timeSeq = date.split("-")
    //TODO : add format check
    val day = timeSeq(0).toInt
    val month = timeSeq(1).toInt
    val year = timeSeq(2).toInt

    val seq = for (t <- startHour to endHour) yield t
    val seqFurure: Seq[Future[JsObject]] = seq.map { t =>
      val doc = Document("time.day" -> day, "time.month" -> month, "time.year" -> year, "time.hour" -> t, "userId" -> userId)
      val amounts = MongoDBHandler.find(collectionName, doc).map(_.map(_.getLong("amount")))
      for {
        inPayment <- amounts.map(_.filter(_.longValue() > 0).foldLeft(0L)(_ + _))
        outPayment <- amounts.map(_.filter(_.longValue() < 0).foldLeft(0L)(_ + _))
      } yield {
        Json.obj("date" -> date, "hour" -> t, "kuber_credit" -> inPayment.toString, "kuber_debit" -> outPayment.toString)
      }
    }.map(_.recover {
      case NonFatal(e) => Json.obj()
    })

    val listOfFutureTrys: Seq[Future[Try[JsObject]]] = seqFurure.map(Utils.futureToFutureTry(_))
    val futureListOfTrys: Future[Seq[Try[JsObject]]] = Future.sequence(listOfFutureTrys)
    futureListOfTrys.map {
      _.map {
        case Success(json) => json
        case Failure(_) => Json.obj()
      }
    }
  }

  def getDaily(userId: String, startDate: String, endDate: String)(implicit ec: ExecutionContext): Future[Seq[JsObject]] = {
    //TODO : add format check
    val dateFormat = new SimpleDateFormat("dd-MM-yyyy")
    val start = dateFormat.parse(startDate)
    val end = dateFormat.parse(endDate)
    val cal = Calendar.getInstance
    cal.setTime(end)
    cal.add(Calendar.DATE, 1)
    val endPlus = cal.getTime
    var date = start
    var seqFurure = Seq[Future[JsObject]]()
    println(endPlus)

    while (date.before(endPlus)) {
      val time = dateFormat.format(date)
      val timeSeq = time.split("-")
      val day = timeSeq(0).toInt
      val month = timeSeq(1).toInt
      val year = timeSeq(2).toInt

      val doc = Document("time.day" -> day, "time.month" -> month, "time.year" -> year, "userId" -> userId)
      val amounts = MongoDBHandler.find(collectionName, doc).map(_.map(_.getLong("amount")))
      val future = for {
        inPayment <- amounts.map(_.filter(_.longValue() > 0).foldLeft(0L)(_ + _))
        outPayment <- amounts.map(_.filter(_.longValue() < 0).foldLeft(0L)(_ + _))
      } yield {
        Json.obj("date" -> time, "kuber_credit" -> inPayment.toString, "kuber_debit" -> outPayment.toString)
      }

      seqFurure = seqFurure ++: Seq(future)

      val calIns = Calendar.getInstance
      calIns.setTime(date)
      calIns.add(Calendar.DATE, 1)
      date = calIns.getTime
    }

    val listOfFutureTrys: Seq[Future[Try[JsObject]]] = seqFurure.map(Utils.futureToFutureTry(_))
    val futureListOfTrys: Future[Seq[Try[JsObject]]] = Future.sequence(listOfFutureTrys)
    futureListOfTrys.map {
      _.map {
        case Success(json) => json
        case Failure(_) => Json.obj()
      }
    }
  }

  def getMonthly(userId: String, startMonth: String, endMonth: String)(implicit ec: ExecutionContext): Future[Seq[JsObject]] = {
    //TODO : add format check
    val dateFormat = new SimpleDateFormat("MM-yyyy")
    val start = dateFormat.parse(startMonth)
    val end = dateFormat.parse(endMonth)
    val cal = Calendar.getInstance
    cal.setTime(end)
    cal.add(Calendar.MONTH, 1)
    val endPlus = cal.getTime
    var date = start
    var seqFurure = Seq[Future[JsObject]]()
    println(endPlus)

    while (date.before(endPlus)) {
      val time = dateFormat.format(date)
      val timeSeq = time.split("-")
      val month = timeSeq(0).toInt
      val year = timeSeq(1).toInt

      val doc = Document("time.month" -> month, "time.year" -> year, "userId" -> userId)
      val amounts = MongoDBHandler.find(collectionName, doc).map(_.map(_.getLong("amount")))
      val future = for {
        inPayment <- amounts.map(_.filter(_.longValue() > 0).foldLeft(0L)(_ + _))
        outPayment <- amounts.map(_.filter(_.longValue() < 0).foldLeft(0L)(_ + _))
      } yield {
        Json.obj("month" -> time, "kuber_credit" -> inPayment.toString, "kuber_debit" -> outPayment.toString)
      }

      seqFurure = seqFurure ++: Seq(future)

      val calIns = Calendar.getInstance
      calIns.setTime(date)
      calIns.add(Calendar.MONTH, 1)
      date = calIns.getTime
    }

    val listOfFutureTrys: Seq[Future[Try[JsObject]]] = seqFurure.map(Utils.futureToFutureTry(_))
    val futureListOfTrys: Future[Seq[Try[JsObject]]] = Future.sequence(listOfFutureTrys)
    futureListOfTrys.map {
      _.map {
        case Success(json) => json
        case Failure(_) => Json.obj()
      }
    }
  }

  def getAllTime(userId: String)(implicit ec: ExecutionContext): Future[JsObject] = {
    val doc = Document("userId" -> userId)
    val amounts = MongoDBHandler.find(collectionName, doc).map(_.map(_.getLong("amount")))
    for {
      inPayment <- amounts.map(_.filter(_.longValue() > 0).foldLeft(0L)(_ + _))
      outPayment <- amounts.map(_.filter(_.longValue() < 0).foldLeft(0L)(_ + _))
    } yield {
      Json.obj("kuber_credit" -> inPayment.toString, "kuber_debit" -> outPayment.toString)
    }
  }

  def getUniqueUserCount(userId: String)(implicit ec: ExecutionContext): Future[Int] = {
    val doc = Document("userId" -> userId)
    MongoDBHandler.find(collectionName, doc).map(_.map(_.getString("mobile")))
      .map(_.distinct.length)
  }
}
