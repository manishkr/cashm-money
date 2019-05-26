package datamodel

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.util.Timeout
import utils.TransferStatus

import scala.concurrent.{ExecutionContextExecutor, Future}
class KuberTransfer extends Actor {
  implicit val ec: ExecutionContextExecutor = context.dispatcher
  private final implicit val DefaultTimeout: Timeout = Timeout(5, TimeUnit.SECONDS)
  override def receive: Receive = {
    case couponTransfer: CouponTransfer => sender ! couponTransfer.transfer()
    case _ => sender ! Future(TransferStatus.Failed)
  }
}