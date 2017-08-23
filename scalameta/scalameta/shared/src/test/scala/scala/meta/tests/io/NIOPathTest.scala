package scala.meta.tests.io

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.scalatest.FunSuite
import scala.meta.internal.io.PlatformPathIO
import scala.meta.internal.io.PathIO

class NIOPathTest extends FunSuite {

  def rootUri = File.listRoots()(0).toURI

  def file: Path = Paths.get("build.sbt")
  def project: Path = Paths.get("project")
  def abs: Path = Paths.get(rootUri).resolve("bar").resolve("foo")
  def cwd: Path = Paths.get(PlatformPathIO.workingDirectoryString)
  val nonNormalizedFile: Path = Paths.get("project", "..", "bin", "scalafmt")

  test(".toString") {
    assert(file.toString == "build.sbt")
    assert(project.toString == "project")
    assert(nonNormalizedFile.toString == PathIO.fromUnix("project/../bin/scalafmt"))
  }

  test(".isAbsolute") {
    assert(!file.isAbsolute)
    assert(abs.isAbsolute)
    assert(cwd.isAbsolute)
  }
  test(".getRoot") {
    assert(file.getRoot == null)
    assert(Paths.get("").toAbsolutePath.getRoot != null)
  }
  test(".getFileName") {
    assert(file.getFileName.toString == "build.sbt")
    assert(abs.getFileName.toString == "foo")
    assert(nonNormalizedFile.getFileName.toString == "scalafmt")
  }
  test(".getParent") {
    assert(abs.getParent.getFileName.toString == "bar")
  }
  test(".getNameCount") {
    assert(Paths.get("/").getNameCount == 0)
    assert(Paths.get("").getNameCount == 1)
    assert(abs.getNameCount == 2)
    assert(nonNormalizedFile.getNameCount == 4)
  }
  test(".getName(index)") {
    assert(file.getName(0).toString == "build.sbt")
    assert(abs.getName(0).toString == "bar")
    assert(abs.getName(1).toString == "foo")
  }
  test(".subpath") {
    assert(abs.subpath(0, 1).toString == "bar")
  }
  test(".startsWith(Path)") {
    assert(!abs.startsWith("bar"))
    assert(!file.startsWith("build"))
    assert(file.startsWith("build.sbt"))
  }
  test(".endsWith(Path)") {
    assert(abs.endsWith("foo"))
    assert(!file.endsWith("build"))
    assert(file.endsWith("build.sbt"))
  }
  test(".normalize") {
    assert(file.resolve("foo").resolve("..").normalize().toString == "build.sbt")
    assert(nonNormalizedFile.normalize().toString == PathIO.fromUnix("bin/scalafmt"))
  }
  test(".resolve") {
    assert(!file.resolve("bar").isAbsolute)
    assert(abs.resolve("bar").isAbsolute)
  }
  test(".resolveSibling(Path)") {
    assert(file.resolveSibling("build.scala").toString == "build.scala")
    assert(abs.resolveSibling("foobar") == abs.getParent.resolve("foobar"))
  }
  test(".relativize(Path)") {
    assert(abs.relativize(abs.resolve("qux")) == Paths.get("qux"))
  }
  test(".toUri") {
    assert(file.toUri.getPath.endsWith("build.sbt"))
    assert(project.toUri.getPath.endsWith("project/"))
  }
  test(".toAbsolutePath") {
    assert(file.toAbsolutePath.endsWith(file))
    assert(abs.toAbsolutePath == abs)
    assert(cwd == Paths.get("").toAbsolutePath)
  }
  test(".toFile") {
    assert(file.toFile.isFile)
    assert(project.toFile.isDirectory)
    assert(cwd.toFile.isDirectory)
  }
}
