package v1.report

import javax.inject.Inject
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

class ReportRouter @Inject()(controller: ReportController) extends SimpleRouter {
  val prefix = "/v1/report"

  override def routes: Routes = {
    case GET(p"/transactions/hourly") => controller.hourlyTransactions()
    case GET(p"/transactions/daily") => controller.dailyTransactions()
    case GET(p"/transactions/monthly") => controller.monthlyTransactions()
    case GET(p"/transactions/all_summary") => controller.allTransactionSummary()
    case GET(p"/total_user") => controller.totalUser()
  }
}
