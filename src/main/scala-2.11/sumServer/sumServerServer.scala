package sumServer

import com.twitter.finagle.{Service, Thrift}
import com.twitter.util.{Await, Future}
import sumServer.futureImpl.SummatorFutureImpl
import sumServer.rpc.Summator
import sumServer.rpc.Summator.ServiceIface

object sumServerServer {
  def main(args: Array[String]) {
    //#thriftserverapi
    val server = Thrift.server
    val iface1 = server.serveIface("localhost:8080", new SummatorFutureImpl)
    val iface2 = server.serveIface("localhost:8081", new SummatorFutureImpl)

    Await.ready(iface2)
    Await.ready(iface1.close())
    //#thriftserverapi
  }

}
