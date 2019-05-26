package datamodel

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorRefFactory}
import akka.event.LoggingReceive
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future}

class KuberTransferMain(actorMaker: (ActorRefFactory, String) => ActorRef) extends Actor {
  private val actors = new mutable.HashMap[String, ActorRef]()
  private final implicit val DefaultTimeout: Timeout = Timeout(5, TimeUnit.SECONDS)
  implicit val ec: ExecutionContextExecutor = context.dispatcher
  def receive = LoggingReceive {
    case couonTransfer: CouponTransfer => transfer(couonTransfer)
  }

  def transfer(couonTransfer: CouponTransfer): Future[Any] = {
    val from = couonTransfer.senderAccount.userId
    val to = couonTransfer.payeeAccount.userId
    val actor = getActor(from, to)

    (actor ? couonTransfer) pipeTo sender
  }

  private def getActor(accountA: String, accountB: String) = {
    val actorId = s"${scala.math.Ordering.String.min(accountA, accountB)}-${scala.math.Ordering.String.max(accountA, accountB)}"
    //TODO: Check actor in context
    if (!actors.contains(actorId)) {
      val actor = actorMaker(context, actorId)
      actors += actorId -> actor
    }
    actors(actorId)
  }
}