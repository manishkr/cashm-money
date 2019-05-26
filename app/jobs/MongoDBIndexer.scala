package jobs

import datamodel.Transaction
import reportprocessor.ReportHandler

import scala.concurrent.ExecutionContext

object MongoDBIndexer {
  def createIndices()(implicit ec: ExecutionContext): Unit = {
    Transaction.createIndex()
    ReportHandler.createIndex()
  }
}
