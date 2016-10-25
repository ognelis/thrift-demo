package sumServer

import com.twitter.finagle.{Service, Thrift}
import com.twitter.util.{Await, Future}
import sumServer.futureImpl.SummatorFutureImpl
import sumServer.rpc.Summator
import sumServer.rpc.Summator.ServiceIface

object sumServerServer {
  def main(args: Array[String]) {
    //#thriftserverapi
    val server = Thrift.server.serveIface("localhost:8080", new SummatorFutureImpl)
    Await.ready(server)

    //#thriftserverapi
  }

}
