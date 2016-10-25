package sumServer.futureImpl

import com.twitter.util.Future
import sumServer.rpc.Summator

class SummatorFutureImpl extends Summator[Future] {
  override def sum(first: Int, second: Int): Future[Int] = Future.value(first+second)
}
