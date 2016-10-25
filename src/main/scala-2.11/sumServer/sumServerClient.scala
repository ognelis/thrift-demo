package sumServer

import com.twitter.finagle.service._
import com.twitter.finagle.util.DefaultTimer
import com.twitter.conversions.time._
import com.twitter.finagle.http.{Response, Status}
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.thrift.ThriftServiceIface
import com.twitter.finagle.{IndividualRequestTimeoutException, Service, SimpleFilter, Thrift}
import com.twitter.util._
import sumServer.rpc.Summator
import sumServer.rpc.Summator.Sum

import scala.collection.mutable.ArrayBuffer

object sumServerClient {
  def main(args: Array[String]) {
    //#thriftclientapi
    val client = Thrift.client.newServiceIface[Summator.ServiceIface]("localhost:8080", "sum")

    def timeoutFilter[Req, Rep](duration: Duration) = {
      val exc = new IndividualRequestTimeoutException(duration)
      val timer = DefaultTimer.twitter
      new TimeoutFilter[Req, Rep](duration, exc, timer)
    }

    val multiplyTheSecond = new SimpleFilter[Sum.Args, Sum.Result] {
      def apply(req: Sum.Args, service: Service[Sum.Args, Sum.Result]): Future[Sum.Result] = {
        val newRequest = req.copy(first = req.first*5)
        service(newRequest)
      }
    }

    val test = timeoutFilter(2.seconds) andThen multiplyTheSecond andThen client.sum


    val retryPolicy = RetryPolicy.tries[Try[Sum.Result]](3,
      {
        case Throw(_) => true
      })

    val retriedGetLogSize = new RetryExceptionsFilter(retryPolicy, DefaultTimer.twitter) andThen
      ThriftServiceIface.resultFilter(Sum) andThen
      client.sum

    val request: Seq[Sum.Args] = Seq(Sum.Args(5,5),Sum.Args(4,4),Sum.Args(3,3),Sum.Args(2,2),Sum.Args(1,1))

    val futures = request.map(arg => test(arg))


    def selectN[A](fs: Seq[Future[A]], n: Int): Future[Seq[Try[A]]] = {
      def helper(currentFutures: Seq[Future[A]], acc: Seq[Try[A]], counter: Int): Future[Seq[Try[A]]] = {
        if (counter>0 && currentFutures.nonEmpty) {
          val result = Future.select(currentFutures)
          result map { case (first, others) =>
            helper(others, acc :+ first, counter - 1)
          }
        }.flatten else Future.value(acc)
      }
      helper(fs, Seq(), n)
    }

    println(Await.result(selectN(futures,3)))




    println(Await.result(test(Sum.Args(5,5))))

    println(Await.result(retriedGetLogSize(Sum.Args(3,3))))




  }
}
