package databases

import scala.concurrent.ExecutionContext

trait MongoDBIndex {
  def createIndex()(implicit ec: ExecutionContext): Unit
}
