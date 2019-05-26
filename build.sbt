scalaVersion := "2.12.6"

name := "cashm-money"

version := "0.1"

libraryDependencies += guice
libraryDependencies += ws
libraryDependencies += "org.joda" % "joda-convert" % "1.9.2"
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % "4.11"
libraryDependencies += "net.debasishg" %% "redisreact" % "0.9"
libraryDependencies += "com.netaporter" %% "scala-uri" % "0.4.16"
libraryDependencies += "net.codingwell" %% "scala-guice" % "4.1.1"
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "2.3.0"
libraryDependencies += "org.mindrot" % "jbcrypt" % "0.3m"
libraryDependencies += "com.nulab-inc" %% "scala-oauth2-core" % "1.3.0"
libraryDependencies += "com.typesafe.play" %% "play-slick" % "3.0.0"
libraryDependencies += "com.typesafe.play" %% "play-slick-evolutions" % "3.0.0"
libraryDependencies += "com.typesafe.play" %% "play-slick" % "3.0.0"
libraryDependencies += "com.h2database" % "h2" % "1.4.197" % Test

enablePlugins(PlayScala)
enablePlugins(DockerPlugin)
