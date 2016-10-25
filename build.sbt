name := "thrift-demo"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.apache.thrift" % "libthrift" % "0.9.3",
  "com.twitter" % "scrooge-core_2.11" % "4.11.0",
  "com.twitter" % "twitter-server_2.11" % "1.24.0",
  "com.twitter" % "finagle-thrift_2.11" % "6.39.0"
)