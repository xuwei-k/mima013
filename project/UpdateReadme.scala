import sbt._, Keys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep
import sbtrelease.Git

object UpdateReadme {

  val updateReadmeTask = { state: State =>
    val extracted = Project.extract(state)
    val v = extracted get version
    val org = extracted get organization
    val n = "mima013-plugin"
    val readme = "README.md"
    val readmeFile = file(readme)
    val newReadme = Predef
      .augmentString(IO.read(readmeFile))
      .lines
      .map { line =>
        val matchReleaseOrSnapshot = line.contains("SNAPSHOT") == v.contains("SNAPSHOT")
        if (line.startsWith("addSbtPlugin") && matchReleaseOrSnapshot && line.contains(n)) {
          s"""addSbtPlugin("${org}" % "${n}" % "$v")"""
        } else line
      }
      .mkString("", "\n", "\n")
    IO.write(readmeFile, newReadme)
    val git = new Git(extracted get baseDirectory)
    git.add(readme) ! state.log
    git.commit(message = "update " + readme, sign = false, signOff = false) ! state.log
    sys.process.Process("git diff HEAD^") ! state.log
    state
  }

  val updateReadmeProcess: ReleaseStep = updateReadmeTask
}
