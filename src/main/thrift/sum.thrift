namespace scala sumServer.rpc

typedef i32 typeToSum

service Summator {
  typeToSum sum(1:typeToSum first, 2:typeToSum second)
}