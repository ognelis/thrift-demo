package sumServer

import com.twitter.finagle.service._
import com.twitter.finagle.util.{DefaultTimer, HashedWheelTimer}
import com.twitter.conversions.time._
import com.twitter.finagle._
import com.twitter.finagle.param.HighResTimer
import com.twitter.finagle.service.exp.FailureAccrualPolicy
import com.twitter.util._
import sumServer.rpc.Summator
import sumServer.rpc.Summator.{ServiceIface, Sum}

object sumServerClient  {
  def main(args: Array[String]): Unit = {
    //#thriftclientapi
    val client = Thrift.client

    val ifaceSum: ServiceIface = client
      .configured(FailureAccrualFactory.Param(() => FailureAccrualPolicy.successRate(
      requiredSuccessRate = 0.10,
      window = 100,
      markDeadFor = Backoff.const(1.milliseconds)
      ))).newServiceIface[Summator.ServiceIface]("localhost:8080", "sum")



    def timeoutFilter[Req, Rep](duration: Duration) = {
      val timer = DefaultTimer.twitter
      new MyTimeoutFilter[Req, Rep](duration, timer)
    }


    val multiplyTheSecond = new SimpleFilter[Sum.Args, Sum.Result] {
      def apply(req: Sum.Args, service: Service[Sum.Args, Sum.Result]): Future[Sum.Result] = {
        val newRequest = req.copy(first = req.first*5)
        service(newRequest)
      }
    }


    val test = timeoutFilter(100.microseconds) andThen multiplyTheSecond andThen ifaceSum.sum


    val retryCondition: PartialFunction[Try[Nothing], Boolean] = {
      case Throw(error) => error match {
        case e: CancelledConnectionException => true
        case e: FailedFastException => true
        case e: com.twitter.util.TimeoutException => true
        case e: com.twitter.finagle.IndividualRequestTimeoutException => true
        case e => false
      }
      case _ =>  println("No exception here"); false
    }

    val retryPolicy = RetryPolicy.backoff(Backoff.equalJittered(100.milliseconds, 10.minutes))(retryCondition)
    def retryFilter[Req, Rep] =new RetryExceptionsFilter[Req, Rep](retryPolicy, HighResTimer.Default)


    val retriedSum = retryFilter.andThen(timeoutFilter(1.milliseconds).andThen(ifaceSum.sum))


    def selectFirsts[A](fs: Seq[Future[A]], n: Int): Future[Seq[Try[A]]] = {
      def helper(currentFutures: Seq[Future[A]], acc: Seq[Try[A]], counter: Int): Future[Seq[Try[A]]] = {
        if (counter>0 && currentFutures.nonEmpty) {
          Future.select(currentFutures) map { case (first, others) =>
            helper(others, first +: acc, counter - 1)
          }
        }.flatten else {
          currentFutures foreach (_.cancel())
          Future.value(acc)
        }
      }
      helper(fs, Seq(), n)
    }

    val futures: Seq[Sum.Args] = (0 to 100000).map(_=> Sum.Args(scala.util.Random.nextInt(),scala.util.Random.nextInt()))
    val requests = futures.map(arg => retriedSum(arg))
    println(Await.result(selectFirsts(requests,10)))
  }
}
