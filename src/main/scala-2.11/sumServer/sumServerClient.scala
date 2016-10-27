package sumServer

import com.twitter.finagle.service._
import com.twitter.finagle.util.DefaultTimer
import com.twitter.conversions.time._
import com.twitter.finagle.service.FailFastFactory.FailFast
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.thrift.ThriftServiceIface
import com.twitter.finagle._
import com.twitter.finagle.client.Transporter.ConnectTimeout
import com.twitter.finagle.param.HighResTimer
import com.twitter.util._
import sumServer.rpc.Summator
import sumServer.rpc.Summator.Sum

object sumServerClient  {
  def main(args: Array[String]) {
    //#thriftclientapi
    val client = Thrift.client

    val ifaceSum = client.newServiceIface[Summator.ServiceIface]("localhost:8080", "sum")
    def timeoutFilter[Req, Rep](duration: Duration) = {
      val timer = DefaultTimer.twitter
      new TimeoutFilter[Req, Rep](duration, timer)
    }


    val multiplyTheSecond = new SimpleFilter[Sum.Args, Sum.Result] {
      def apply(req: Sum.Args, service: Service[Sum.Args, Sum.Result]): Future[Sum.Result] = {
        val newRequest = req.copy(first = req.first*5)
        service(newRequest)
      }
    }


    val test = timeoutFilter(1.microseconds) andThen multiplyTheSecond andThen ifaceSum.sum


    val retryCondition: PartialFunction[Try[Nothing], Boolean] = {
      case Throw(error) => error match {
        case e: CancelledConnectionException => true
        case e: FailedFastException => true
        case e: com.twitter.util.TimeoutException => println("timeout"); true
        case e: com.twitter.finagle.IndividualRequestTimeoutException => true
        case e => false
      }
      case _ =>  false
    }


    val retryPolicy = RetryPolicy.backoff(Backoff.equalJittered(2.seconds, 60.minutes))(retryCondition)
    def retryFilter[Req, Rep] = new RetryExceptionsFilter[Req, Rep](retryPolicy, HighResTimer.Default)


    val retriedSum = retryFilter andThen timeoutFilter(1.milliseconds).andThen(ifaceSum.sum)


    def selectFirsts[A](fs: Seq[Future[A]], n: Int): Future[Seq[Try[A]]] = {
      def helper(currentFutures: Seq[Future[A]], acc: Seq[Try[A]], counter: Int): Future[Seq[Try[A]]] = {
        if (counter>0 && currentFutures.nonEmpty) {
          Future.select(currentFutures) map { case (first, others) =>
            helper(others, first +: acc, counter - 1)
          }
        }.flatten else {
          currentFutures foreach {x => x.interruptible(); x.raise(new CancelledRequestException)}
          Future.value(acc)
        }
      }
      helper(fs, Seq(), n)
    }


    val futures: Seq[Sum.Args] = (0 to 1).map(_=> Sum.Args(scala.util.Random.nextInt(),scala.util.Random.nextInt()))
    val requests = futures.map(arg => retriedSum(arg))
    println(Await.result(Future.collect(requests)))
  }
}
