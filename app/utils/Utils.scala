package utils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object Utils {
  def futureToList[T](seqFurure: Seq[Future[T]], defaultValue: T)(implicit ec: ExecutionContext): Future[Seq[T]] = {
    val listOfFutureTrys: Seq[Future[Try[T]]] = seqFurure.map(Utils.futureToFutureTry(_))
    val futureListOfTrys: Future[Seq[Try[T]]] = Future.sequence(listOfFutureTrys)
    futureListOfTrys.map {
      _.map {
        case Success(json) => json
        case Failure(_) => defaultValue
      }
    }
  }

  def futureToFutureTry[T](f: Future[T])(implicit ec: ExecutionContext): Future[Try[T]] = f.map(Success(_)).recover({ case e => Failure(e) })
}
