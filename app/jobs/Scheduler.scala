package jobs

import akka.actor.{ActorRef, ActorSystem}
import javax.inject.{Inject, Named}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class Scheduler @Inject() (val system: ActorSystem, @Named("scheduler-actor") val schedulerActor: ActorRef)(implicit ec: ExecutionContext) {
  system.scheduler.scheduleOnce(0.microseconds, schedulerActor, "creteMongoDBIndex")
}
