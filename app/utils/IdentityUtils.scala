package utils

import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import org.bson.types.ObjectId

import scala.util.Random

object IdentityUtils {
  private val random = Random
  private val minOTP = Math.pow(10, 5).asInstanceOf[Int]
  private val maxOTP = Math.pow(10, 6).asInstanceOf[Int] - 1

  def generateUniqueId(salt: String): String = new ObjectId().toString + salt

  def generteOTP(): String = (random.nextInt(maxOTP - minOTP + 1) + minOTP).toString

  def generateTransactionId(): String = {
    //TODO : Change it to cluster
    val time = Instant.now.toEpochMilli.toString
    val inc = new ObjectId().getCounter.toString
    time + inc
  }
}
