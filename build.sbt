organization := "ch.unibas.cs.gravis"

name := """shape-priors-from-pieces"""
version := "0.1"

scalaVersion := "2.12.12"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

resolvers += Opts.resolver.sonatypeSnapshots


libraryDependencies ++= Seq(
  "ch.unibas.cs.gravis" % "scalismo-native-all" % "4.0.+",
  "ch.unibas.cs.gravis" %% "scalismo-ui" % "0.90-RC1",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
).map(_.force())

libraryDependencies ~= {
  _.map(_.exclude("org.slf4j", "slf4j-nop"))
}
