package jobs

import akka.actor.Actor
import javax.inject.{Inject, Singleton}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

@Singleton
class SchedulerActor @Inject()(ws: WSClient)(implicit ec: ExecutionContext) extends Actor {
  def receive: PartialFunction[Any, Unit] = {
    case "creteMongoDBIndex" => MongoDBIndexer.createIndices()
  }
}

