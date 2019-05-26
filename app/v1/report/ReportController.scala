package v1.report

import java.text.SimpleDateFormat
import java.util.Date

import javax.inject.Inject
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Configuration, Logger}
import reportprocessor.ReportHandler
import v1.auth.{AuthBaseController, AuthControllerComponents, AuthRequest}

import scala.concurrent.{ExecutionContext, Future}

class ReportController @Inject()(cc: AuthControllerComponents)(ws: WSClient)(config: Configuration)(implicit ec: ExecutionContext)
  extends AuthBaseController(cc){

  private val logger = Logger(getClass)
  private val authURL = config.get[String]("cashm.authserver")

  def hourlyTransactions(): Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("hourlyTransactions: ")
    val params = request.queryString.map { case (k, v) => k -> v.mkString }
    val dateFormat = new SimpleDateFormat("dd-MM-yyyy")
    val today =  new Date()
    val date = params.getOrElse("date", dateFormat.format(today))
    val startHour = params.getOrElse("start_hour", "0").toInt
    val endHour = params.getOrElse("end_hour", "23").toInt

    getAuth()
      .map {
        case Some(user) =>
          for {
            transactions <- ReportHandler.getHourly(user._1, date, startHour, endHour)
          } yield {
            Ok(Json.obj("hourly_report" -> transactions))
          }
        case _ => Future(Unauthorized)
      }.flatten
  }

  def dailyTransactions(): Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("dailyTransactions: ")
    val params = request.queryString.map { case (k, v) => k -> v.mkString }
    val dateFormat = new SimpleDateFormat("dd-MM-yyyy")
    val today =  new Date()
    val date = dateFormat.format(today)
    val startDate = params.getOrElse("start_date", date)
    val endDate = params.getOrElse("end_date", date)
    getAuth()
      .map {
        case Some(user) =>
          for {
            transactions <- ReportHandler.getDaily(user._1, startDate, endDate)
          } yield {
            Ok(Json.obj("daily_report" -> transactions))
          }
        case _ => Future(Unauthorized)
      }.flatten
  }

  def monthlyTransactions(): Action[AnyContent] = AuthAction.async { implicit request =>
    logger.trace("dailyTransactions: ")
    val params = request.queryString.map { case (k, v) => k -> v.mkString }
    val dateFormat = new SimpleDateFormat("MM-yyyy")
    val today = new Date()
    val month = dateFormat.format(today)
    val startMonth = params.getOrElse("start_month", month)
    val endMonth = params.getOrElse("end_month", month)
    getAuth()
      .map {
        case Some(user) =>
          for {
            transactions <- ReportHandler.getMonthly(user._1, startMonth, endMonth)
          } yield {
            Ok(Json.obj("monthly_report" -> transactions))
          }
        case _ => Future(Unauthorized)
      }.flatten
  }

  def allTransactionSummary(): Action[AnyContent] = AuthAction.async { implicit request =>
    getAuth()
      .map {
        case Some(user) =>
          for {
            transactions <- ReportHandler.getAllTime(user._1)
          } yield {
            Ok(Json.obj("all_report" -> transactions))
          }
        case _ => Future(Unauthorized)
      }.flatten
  }

  def totalUser(): Action[AnyContent] = AuthAction.async { implicit request =>
    getAuth()
      .map {
        case Some(user) =>
          for {
            totalUser <- ReportHandler.getUniqueUserCount(user._1)
          } yield {
            Ok(Json.obj("total_user" -> totalUser))
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
    {
      {
        for {
          auth <- request.headers.get("Authorization")
        } yield {
          val url = s"$authURL/v1/user/user_id/$mobile"
          ws.url(url).withHttpHeaders("Authorization" -> auth)
            .get().map { response =>
            if (response.status == 200) {
              (response.json \ "user_id").asOpt[String]
            } else {
              None
            }
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
}
