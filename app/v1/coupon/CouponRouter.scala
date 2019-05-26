package v1.coupon

import javax.inject.Inject
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

class CouponRouter @Inject()(controller: CouponController) extends SimpleRouter {
  val prefix = "/v1/coupon"

  override def routes: Routes = {
    case POST(p"/transfer") => controller.process
    case POST(p"/add") => controller.addCoupon
    case POST(p"/pay") => controller.payToUser
    case POST(p"/payment") => controller.payment

    case GET(p"/balance") => controller.balance()
    case GET(p"/transactions") => controller.transactions()
    case GET(p"/total_kuber") => controller.totalKuber()
  }
}
