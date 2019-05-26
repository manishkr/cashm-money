package v1.coupon

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import datamodel._
import javax.inject.Inject
import play.api.data.Form
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Configuration, Logger}
import utils.TransferStatus.TransferStatus
import utils.{CurrencyCode, TransferStatus}
import v1.auth.{AuthBaseController, AuthControllerComponents, AuthRequest}

import scala.concurrent.{ExecutionContext, Future}

class CouponController @Inject()(system: ActorSystem, cc: AuthControllerComponents)(ws: WSClient)(config: Configuration)(implicit ec: ExecutionContext)
  extends AuthBaseController(cc){

  lazy val actorRef: ActorRef = system.actorOf(
    Props(classOf[KuberTransferMain], (f: ActorRefFactory, accountId: String) => f.actorOf(Props[KuberTransfer], accountId))
  )

  private final implicit val DefaultTimeout: Timeout = Timeout(5, TimeUnit.SECONDS)
  private val logger = Logger(getClass)
  private val authURL = config.get[String]("cashm.authserver")
  private val form: Form[CouponSender] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "amount" -> longNumber,
        "currency_code" -> nonEmptyText,
        "payee_mobile" -> nonEmptyText
      )(CouponSender.apply)(CouponSender.unapply)
    )
  }

  private val couponAdderForm: Form[CouponAdder] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "amount" -> longNumber,
        "currency_code" -> nonEmptyText,
      )(CouponAdder.apply)(CouponAdder.unapply)
    )
  }


  private val paymentForm: Form[Payment] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "amount" -> longNumber,
        "currency_code" -> nonEmptyText
      )(Payment.apply)(Payment.unapply)
    )
  }

  def process: Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("process: ")
    processJsonPost()
  }

  def payToUser: Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("payToUser: ")
    processPayToUser()
  }


  def addCoupon: Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("addCoupon: ")
    processAddCoupon()
  }

  def payment: Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("payment: ")
    processPayment()
  }

  def balance(): Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("balance: ")
    getAuth()
      .map {
        case Some(user) =>
          for {
            availableBalance <- AvailableBalance.getBalanceAsJson(user._1)
          } yield {
            Ok(Json.obj("availble_balance" -> availableBalance, "book_balance" -> Json.obj("balance" -> 0, "currency_code" -> "INR")))
          }
        case _ => Future(Unauthorized)
      }.flatten
  }

  def transactions(): Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("transactions: ")
    val params = request.queryString.map { case (k, v) => k -> v.mkString.toInt }
    val size = params.getOrElse("size", 10)
    val page = params.getOrElse("page", 1)

    getAuth()
      .map {
        case Some(user) =>
          for {
            transactions <- Transaction.get(user._1, size, page)
            totalCount <- Transaction.getTotalCount(user._1)
          } yield {
            Ok(Json.obj("transactions" -> transactions, "total" -> totalCount))
          }
        case _ => Future(Unauthorized)
      }.flatten
  }

  def totalKuber(): Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("totalKuber: ")
    getAuth()
      .map {
        case Some(user) =>
          for {
            total <- AvailableBalance.getTotal()
          } yield {
            Ok(Json.obj("total_kuber" -> total.toString(), "currency_code" -> CurrencyCode.INR))
          }
        case _ => Future(Unauthorized)
      }.flatten
  }

  private def getAuth[A]()(implicit request: AuthRequest[A]) = {
    {
      {
        for {
          auth <- request.headers.get("Authorization")
        } yield {
          val url = s"$authURL/v1/auth"
          ws.url(url).withHttpHeaders("Authorization" -> auth)
            .get()
            .map { response =>
              if(response.status == 200) {
                val userId = (response.json \ "userId").asOpt[String]
                val mobile = (response.json \ "mobile").asOpt[String]
                (userId, mobile)
              }else{
                (None, None)
              }
            }.recover {
            case _ => (None, None)
            }
            .map {
              case (Some(userId), Some(mobile)) => Some((userId, mobile))
              case _ => None
            }
        }
      } match {
        case Some(f) => f.map(Some(_))
        case None => Future(None)
      }
    }
      .map{
        case Some(Some(f)) => Some(f)
        case _ => None
      }
  }

  private def getUserId[A](mobile : String)(implicit request: AuthRequest[A]) = {
    val url = s"$authURL/v1/user/user_id/$mobile"
    ws.url(url).addHttpHeaders(createHeaders().toList: _*)
      .get().map { response =>
      if (response.status == 200) {
        (response.json \ "user_id").asOpt[String]
      } else {
        None
      }
    }.recover {
      case _ => None
    }
  }

  private def createHeaders[A]()(implicit request: AuthRequest[A]) = {
    request.headers.toSimpleMap.filter(x => Seq("Authorization", "auth_token", "auth_secret").contains(x._1))
  }

  private def processPayment[A]()(implicit request: AuthRequest[A]): Future[Result] = {
    def failure(badForm: Form[Payment]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(payment: Payment) = {
      getAuth()
        .map {
          case Some(user) =>
            getClientUserId()
              .map {
                case Some(payeeUserId) => doTransfer(Account(user._1, user._2), Account(payeeUserId, "merchant_mobile"),
                  Amount(payment.amount, CurrencyCode.withName(payment.currencyCode)))
                case _ => Future(Unauthorized)
              }
          case _ => Future(Future(Unauthorized))
        }.flatten.flatten
    }

    paymentForm.bindFromRequest().fold(failure, success)
  }

  private def doTransfer[A](sender: Account, receiver: Account, amount: Amount) = {
    (actorRef ? CouponTransfer(sender, receiver, amount)).map {
      _.asInstanceOf[Future[TransferStatus]].map(status => Ok(transferStatusMap(status)))
    }.flatten
  }

  private def processJsonPost[A]()(implicit request: AuthRequest[A]): Future[Result] = {
    def failure(badForm: Form[CouponSender]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(couponSender: CouponSender) = {
      getAuth()
        .map {
          case Some(user) =>
            getUserId(mobile = couponSender.payeeMobile)
            .map {
              case Some(payeeUserId) => doTransfer(Account(user._1, user._2), Account(payeeUserId, couponSender.payeeMobile),
                Amount(couponSender.amount, CurrencyCode.withName(couponSender.currencyCode)))
              case _ => payeeNotRegistered
            }
          case _ => Future(Future(Unauthorized))
        }.flatten.flatten
    }

    form.bindFromRequest().fold(failure, success)
  }

  private def payeeNotRegistered[A] = {
    Future(BadRequest(Json.obj("status" -> "mobile_not_registered", "message" -> "Mobile not registered with kuber")))
  }

  private def processPayToUser[A]()(implicit request: AuthRequest[A]): Future[Result] = {
    def failure(badForm: Form[CouponSender]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(couponSender: CouponSender) = {
      getClientUserId()
        .map {
          case Some(user) =>
            getUserId(mobile = couponSender.payeeMobile)
              .map {
                case Some(payeeUserId) =>
                  doTransfer(Account(user, "test_merchant"), Account(payeeUserId, couponSender.payeeMobile),
                    Amount(couponSender.amount, CurrencyCode.withName(couponSender.currencyCode)))
                case _ => payeeNotRegistered
              }
          case _ => Future(Future(Unauthorized))
        }.flatten.flatten
    }

    form.bindFromRequest().fold(failure, success)
  }

  private def processAddCoupon[A]()(implicit request: AuthRequest[A]): Future[Result] = {
    def failure(badForm: Form[CouponAdder]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(coupon: CouponAdder) = {
      getAuth()
        .map {
          case Some(user) => CouponAdd(Account(user._1, user._2), Amount(coupon.amount, CurrencyCode.withName(coupon.currencyCode)), "test").save()
                    .map { status => Ok(transferStatusMap(status))}
          case _ => Future(Unauthorized)
        }.flatten
    }

    couponAdderForm.bindFromRequest().fold(failure, success)
  }

  private def getClientUserId[A]()(implicit request: AuthRequest[A]) = {
    {
      {
        for {
          authToken <- request.headers.get("auth_token")
          authSecret <- request.headers.get("auth_secret")
        } yield {
          println(authToken)
          println(authSecret)
          val url = s"$authURL/v1/merchant_auth/token_user"
          ws.url(url).withHttpHeaders("auth_token" -> authToken, "auth_secret" -> authSecret)
            .get().map { response =>
            if (response.status == 200) {
              println(response)
              (response.json \ "user_id").asOpt[String]
            } else {
              None
            }
          }.recover {
            case _ => None
          }
        }
      } match {
        case Some(f) => f.map(Some(_))
        case None => Future(None)
      }
    }
      .map {
        case Some(Some(f)) => Some(f)
        case _ => None
      }
  }

  private def transferStatusMap(status : TransferStatus) = status match {
    case TransferStatus.Success => Json.obj("status" -> "success", "message" -> status.toString)
    case TransferStatus.InsufficentFund => Json.obj("status" -> "insufficent_fund", "message" -> status.toString)
    case TransferStatus.SameAccount => Json.obj("status" -> "same_account", "message" -> status.toString)
    case TransferStatus.Failed => Json.obj("status" -> "failed", "message" -> status.toString)
  }
}
