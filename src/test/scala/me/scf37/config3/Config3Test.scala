package me.scf37.config3

import java.util.Properties

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigParseOptions
import me.scf37.config3.Config3.{ArgumentsParseError, ConfigValidationError, Help, HelpParam}
import org.scalatest.FreeSpec

import scala.collection.mutable

class Config3Test extends FreeSpec {
  private def filterKey(pathPrefixes: Set[String]): String => Boolean = p =>
    pathPrefixes.isEmpty || pathPrefixes.exists(pp => p == pp || p.startsWith(pp + "."))
  "walk" - {
    val c = ConfigFactory.parseString("""a.c = 1, a.b.c = 2, b.c = 3, aa = 4""")

    def doWalk(restrictToPath: Set[String]): Seq[String] = {
      val result = mutable.Buffer.empty[String]
      Config3.walk(c.root(),filterKey(restrictToPath)).map { case (path, v) =>
        result += (v match {
          case _: ConfigObject => s"$path -> {}"
          case v => s"$path -> ${v.unwrapped()}"
        })
      }
      result.toSeq
    }

    "collects all values w/o filtering" in {
      val result = doWalk(Set.empty)
      assert(result.sorted == Seq(
        " -> {}",
        "a -> {}",
        "a.b -> {}",
        "a.b.c -> 2",
        "a.c -> 1",
        "aa -> 4",
        "b -> {}",
        "b.c -> 3",
      ))
    }

    "filters by path of one segment" in {
      val result = doWalk(Set("a"))
      assert(result.sorted == Seq(
        "a -> {}",
        "a.b -> {}",
        "a.b.c -> 2",
        "a.c -> 1",
      ))
    }

    "filters by path of multiple segments" in {
      val result = doWalk(Set("a.b"))
      assert(result.sorted == Seq(
        "a.b -> {}",
        "a.b.c -> 2",
      ))

      val result2 = doWalk(Set("a.c"))
      assert(result2.sorted == Seq(
        "a.c -> 1"
      ))
    }
  }

  "help" - {
    "works" in {
      val c = ConfigFactory.parseResources("test.conf")
      val s = Config3.help(c, filterKey(Set.empty)).toString
      assert(s.contains("app.env"))
      assert(s.contains("web.dev"))
      assert(s.contains("[1,2,3"))
      assert(s.contains("horribly wrong"))
    }
  }

  "argument parsing" - {
    def check(expected: String, args: String*): Unit =
      assert(Config3.parse(args.toArray) == Right(ConfigFactory.parseString(expected)))

    def checkFail(expected: ArgumentsParseError, args: String*): Unit =
      assert(Config3.parse(args.toArray) == Left(expected))

    "works for correct simple arguments" in {
      check("a=42", "-a", "42")
      check("a=1,b.c=2", "-a", "1", "-b.c", "2")
    }
    "works for correct arguments with spaces" in {
      check("a=4 2", "-a", "4 2")
    }
    "works for correct arrays" in {
      check("a=[1,2,3]", "-a", "[1, 2, 3]")
    }
    "fails if argument value is not provided" in {
      checkFail(ArgumentsParseError(0, "-a", "No value provided"), "-a")
      checkFail(ArgumentsParseError(2, "-b", "No value provided"), "-a", "1", "-b")
    }

    "fails if argument key does not start with '-'" in {
      checkFail(ArgumentsParseError(0, "a", "Parameter name must start with '-'"), "a", "1")
      checkFail(ArgumentsParseError(2, "b", "Parameter name must start with '-'"), "-a", "1", "b", "2")
    }
  }

  "validate" - {
    def check0(referenceConf: Config, appConf: String, restrictToPath: Set[String], errors: ConfigValidationError*): Unit =
      assert(
        Config3.validate(referenceConf, ConfigFactory.parseString(appConf), filterKey(restrictToPath)).sortBy(_.inputParameter).toList ==
          errors.sortBy(_.inputParameter).toList)
    "should not allow unknown parameters" - {
      val referenceConf = ConfigFactory.parseString("a=1")
      def check(appConf: String, errors: ConfigValidationError*): Unit = check0(referenceConf, appConf, Set.empty, errors: _*)

      "single error" in {
        check("a=1,b=1", ConfigValidationError(Some("b"), None, "Provided parameter 'b' is not known"))
      }
      "multiple errors" in {
        check("a=1,b=1,c=1",
          ConfigValidationError(Some("b"), None, "Provided parameter 'b' is not known"),
          ConfigValidationError(Some("c"), None, "Provided parameter 'c' is not known"))
      }
    }
    "should not allow unset required parameters (nulls)" - {
      val referenceConf = ConfigFactory.parseString("a=1,b=null,c=null")
      def check(appConf: String, errors: ConfigValidationError*): Unit = check0(referenceConf, appConf, Set.empty, errors: _*)

      "single error" in {
        check("c=1", ConfigValidationError(None, Some("b"), "Parameter 'b' is required"))
      }
      "multiple errors" in {
        check("",
          ConfigValidationError(None, Some("b"), "Parameter 'b' is required"),
          ConfigValidationError(None, Some("c"), "Parameter 'c' is required"))
      }
    }

    "should ignore errors out of restrictToPath paths" in {
      val referenceConf = ConfigFactory.parseString("a.a=null,b.a=null")
      check0(referenceConf, "a.b=1, b.b=1", Set("b"),
        ConfigValidationError(None, Some("b.a"), "Parameter 'b.a' is required"),
        ConfigValidationError(Some("b.b"), None, "Provided parameter 'b.b' is not known")
      )
    }
    "should work for valid input" in {
      val referenceConf = ConfigFactory.parseString("a=null,b=1")
      check0(referenceConf, "a=1", Set.empty)
      check0(referenceConf, "a=1, b=2", Set.empty)
    }

    "should work for mixed input" in {
      val referenceConf = ConfigFactory.parseString("a.a=1")
      val badConf = ConfigFactory.parseProperties({
        val p = new Properties()
        p.put("a.aa", "2")
        p
      })

      val goodConf = ConfigFactory.parseProperties({
        val p = new Properties()
        p.put("a.a", "2")
        p
      })

      assert(Config3.validate(referenceConf, badConf, filterKey(Set.empty)).length == 1)
      assert(Config3.validate(referenceConf, goodConf, filterKey(Set.empty)).length == 0)
    }
  }

  "print" in {
    val referenceConf = ConfigFactory.parseResources("test.conf")
    val conf = ConfigFactory.parseString(
      """
        myapp.env = dev
        myapp.map.value = {x:1, y:2, z:3}
      """, ConfigParseOptions.defaults().setOriginDescription("inline origin"))
    val printedConfig = Config3.printConfig(referenceConf, conf, _ => true, _ => false).toString
    assert(printedConfig.contains("myapp.env"))
    assert(printedConfig.contains("web.dev"))
    assert(printedConfig.contains("[1,2,3]"))
    assert(printedConfig.contains("test.conf"))
    assert(printedConfig.contains("inline origin"))
    ConfigFactory.systemProperties()
      .withFallback(ConfigFactory.load("app.conf"))
  }
}
