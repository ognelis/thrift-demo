package sumServer

import com.twitter.finagle.context.{Contexts, Deadline}
import com.twitter.finagle.service.TimeoutFilter
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.finagle.tracing.Trace
import com.twitter.finagle.{IndividualRequestTimeoutException, RequestTimeoutException, Service, SimpleFilter}
import com.twitter.util.{Duration, Future, Timer}

class MyTimeoutFilter[Req, Rep](
                           timeout: Duration,
                           exception: RequestTimeoutException,
                           timer: Timer,
                           statsReceiver: StatsReceiver)
  extends SimpleFilter[Req, Rep] {

  def this(timeout: Duration, exception: RequestTimeoutException, timer: Timer) =
    this(timeout, exception, timer, NullStatsReceiver)

  def this(timeout: Duration, timer: Timer) =
    this(timeout, new IndividualRequestTimeoutException(timeout), timer)

  private[this] val expiredDeadlineStat = statsReceiver.stat("expired_deadline_ms")

  def apply(request: Req, service: Service[Req, Rep]): Future[Rep] = {
    val timeoutDeadline = Deadline.ofTimeout(timeout)

    // If there's a current deadline, we combine it with the one derived
    // from our timeout.
    val deadline = Deadline.current match {
      case Some(current) => Deadline.combined(timeoutDeadline, current)
      case None => timeoutDeadline
    }

    if (deadline.expired) {
      expiredDeadlineStat.add(-deadline.remaining.inMillis)
    }

    Contexts.broadcast.let(Deadline, deadline) {
      val res = service(request)
      res.within(timer, timeout).rescue {
        case exc: java.util.concurrent.TimeoutException =>
            //Зачем это нужно???
//          res.raise(exc)
          Trace.record(TimeoutFilter.TimeoutAnnotation)
          Future.exception(exception)
      }
    }
  }
}
