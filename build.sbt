import sbt.Keys._
import sbt._


val defaultSettings = Defaults.coreDefaultSettings ++ xerial.sbt.Sonatype.sonatypeSettings ++ Seq(
  organization := "com.github.spinalhdl",
  version      := SpinalVersion.all,
  scalaVersion := SpinalVersion.compiler,
  scalacOptions ++= Seq("-unchecked","-target:jvm-1.7"/*, "-feature" ,"-deprecation"*/),
  javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
  baseDirectory in test := file("/out/"),
  fork := true,

  libraryDependencies += "org.scala-lang" % "scala-library" % SpinalVersion.compiler,

  dependencyOverrides += "net.java.dev.jna" % "jna" % "4.2.2",
  dependencyOverrides += "net.java.dev.jna" % "jna-platform" % "4.2.2",
  dependencyOverrides += "org.slf4j" % "slf4j-api" % "1.7.25",
  dependencyOverrides += "org.scala-lang.modules" % "scala-xml" % "1.2.0",

//sbt clean reload publishSigned
  //https://oss.sonatype.org
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := (_ => false),
  pomExtra := {
    <url>github.com/SpinalHDL/SpinalHDL</url>
      <licenses>
        <license>
          <name>LGPL3</name>
          <url>https://www.gnu.org/licenses/lgpl.html</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:github.com/SpinalHDL/SpinalHDL</connection>
        <developerConnection>scm:git:git@github.com:SpinalHDL/SpinalHDL</developerConnection>
        <url>github.com/SpinalHDL/SpinalHDL</url>
      </scm>
      <developers>
        <developer>
          <id>Dolu1990</id>
          <name>Dolu1990</name>
          <url>none</url>
        </developer>
      </developers>
  },

  publishTo := {
    val v = version.value
    val nexus = "https://oss.sonatype.org/"
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
 )

lazy val all = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .settings(
    defaultSettings,
    name := "SpinalHDL-all",
    version := SpinalVersion.all,
    publishArtifact := false,
    publishLocal := {},
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(lib, core)
  )
  .aggregate(sim, core, lib, debugger, tester)


import sys.process._
def gitHash = (try {
  "git rev-parse HEAD".!!
} catch{
  case e : java.io.IOException => "???"
}).linesIterator.next()


lazy val sim = (project in file("sim"))
  .settings(
    defaultSettings,
    name := "SpinalHDL-sim",
    libraryDependencies += "commons-io" % "commons-io" % "2.4",
    libraryDependencies += "net.openhft" % "affinity" % "3.1.11",
    libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.25",
    libraryDependencies += "com.github.dblock" % "oshi-core" % "3.4.0",
    version := SpinalVersion.sim
  )

lazy val core = (project in file("core"))
  .settings(
    defaultSettings,
    name := "SpinalHDL-core",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.0",
    resolvers += Resolver.sonatypeRepo("public"),
    version := SpinalVersion.core,
    sourceGenerators in Compile += Def.task {
      val dir = (sourceManaged in Compile).value
      val file = dir / "Info.scala"
      IO.write(file, """package spinal.core
                       |object Info {
                       |  val version = "%s"
                       |  val name = "%s"
                       |  val gitHash = "%s"
                       |}
                       |""".stripMargin.format(SpinalVersion.core, name, gitHash))
      Seq(file)
    }.taskValue
  )
  .dependsOn(sim)

lazy val lib = (project in file("lib"))
  .settings(
    defaultSettings,
    name := "SpinalHDL-lib",
    libraryDependencies += "commons-io" % "commons-io" % "2.4",
    version := SpinalVersion.lib
  )
  .dependsOn (sim, core)


lazy val debugger = (project in file("debugger"))
  .settings(
    defaultSettings,
    name := "SpinalHDL Debugger",
    version := SpinalVersion.debugger,
    resolvers += "sparetimelabs" at "http://www.sparetimelabs.com/maven2/",
    libraryDependencies += "com.github.purejavacomm" % "purejavacomm" % "1.0.2.RELEASE",
    libraryDependencies += "net.liftweb" %% "lift-json" % "3.1.0-M2",
    publishArtifact := false,
    publishLocal := {}
  )
.dependsOn(sim, core, lib/*, ip*/)

lazy val demo = (project in file("demo"))
  .settings(
    defaultSettings,
    name := "SpinalHDL-demo",
    version := SpinalVersion.demo,
    publishArtifact := false,
    publishLocal := {}
  )
  .dependsOn(sim, core, lib, debugger)


lazy val tester = (project in file("tester"))
  .settings(
    defaultSettings,
    name := "SpinalHDL-tester",
    version := SpinalVersion.tester,
    baseDirectory in (Test) := file("./"),

    libraryDependencies += "org.scalatest" % "scalatest_2.12" % "3.1.0",
    publishArtifact := false,
    publishLocal := {}
  )
  .dependsOn(sim, core, lib, debugger,demo)

// Assembly

assemblyJarName in assembly := "spinalhdl.jar"

test in assembly := {}

assemblyOutputPath in assembly := file("./release/spinalhdl.jar")

//To publish the scala doc :
//rm -rf ghpages
//sbt clean compile unidoc
//git clone https://github.com/SpinalHDL/SpinalHDL.git -b gh-pages --depth=1 ghpages
//rm -rf ghpages/*
//cp -r target/scala-2.11/unidoc/* ghpages
//cd ghpages
//git add *
//git commit -m "publish doc"
//git push
//cd ..
//rm -rf ghpages
