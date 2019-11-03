package me.scf37.config3

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueType

import scala.collection.mutable

/**
  * Configuration library API.
  *
  * Available configuration parameters are defined in reference configuration in following format:
  * - one of configuration *objects* should have comment that goes to app description in help text
  * - comment on configuration parameter goes to parameter description in help text
  * - if parameter is null, it is required parameter and must be provided by application configuration
  *
  * `validate()` can be called to ensure all passed in parameters are mentioned in reference configuration AND
  *   all required parameters in reference configuration are overridden.
  *
  *  Since reference configuration can be collected from many files on classpath, `restrictToPath` is useful
  *  to limit to application-wide configuration only.
  *
  */
trait Config3 {
  case class ConfigValidationError(inputParameter: Option[String], referenceParameter: Option[String], message: String) {
    override def toString: String = message
  }
  case class ArgumentsParseError(number: Int, argument: String, message: String) {
    override def toString: String = s"Error parsing argument #${number + 1} ('$argument'): $message"
  }

  /**
    * Parse config from command line arguments. Expected format is space-delimited
    * -<parameter name> <parameter value>
    *
    * @param args command line arguments
    * @return parsed Config or error description
    */
  def parse(args: Array[String]): Either[ArgumentsParseError, Config]

  /**
    * Print help message from given reference configuration
    *
    * @param referenceConf reference configuration
    * @param filterKey paths to consider
    * @return formatted help as object with toString method
    */
  def help(referenceConf: Config, filterKey: String => Boolean): Help

  /**
    * Validate loaded configuration. In detail, `applicationConf` must be subset of `referenceConf` AND
    *   all null parameters from `referenceConf` must be overridden to non-null in `applicationConf`
    *
    * @param referenceConf reference configuration
    * @param applicationConf loaded configuration
    * @param filterKey paths to consider
    * @return list of validation errors
    */
  def validate(
    referenceConf: Config,
    applicationConf: Config,
    filterKey: String => Boolean
  ): Seq[ConfigValidationError]

  /**
    * Print loaded configuration. referenceConf is used as a source of valid configuration parameter names
    * and applicationConf provides values
    *
    * @param referenceConf
    * @param applicationConf
    * @param filterKey
    * @return assembled PrintedConfig with toString
    */
  def printConfig(
    referenceConf: Config,
    applicationConf: Config,
    filterKey: String => Boolean,
    isParamSecret: String => Boolean
  ): PrintedConfig

  /**
    * Application help, consists of textual header and list of parameters
    *
    * @param help help lines
    * @param params
    *
    */
  case class Help(
    help: Seq[String],
    params: Seq[HelpParam]
  ) {
    override def toString = Config3.renderHelp(this)
  }

  /**
    * Application config parameter
    *
    * @param name parameter name, e.g. myapp.db.login
    * @param value default parameter value, if any. None means parameter is required
    * @param description parameter description, as collection of lines
    */
  case class HelpParam(
    name: String,
    value: Option[String],
    description: Seq[String]
  )

  case class PrintedConfig(
    lines: Seq[PrintedConfigParam],
    isParamSecret: String => Boolean
  ) {
    override def toString = Config3.renderConfig(this)
  }

  case class PrintedConfigParam(
    name: String,
    value: ConfigValue
  )
}

/**
  * Implementation of Config3 API. See trait scaladoc.
  */
object Config3 extends Config3 {
  private def maxOpt(coll: Iterable[Int], ifEmpty: Int): Int = coll.reduceOption(_ max _).getOrElse(ifEmpty)
  private def fill(len: Int): String = new String(Array.fill(len)(' '))
  private def padRight(s: String, to: Int): String = s + fill(Math.max(0, to - s.length))

  private def renderConfig(config: Config3#PrintedConfig): String = {
    val table = config.lines.map { line =>
      (
        line.name,
        if (config.isParamSecret(line.name)) "******" else line.value.render(ConfigRenderOptions.concise()),
        line.value.origin().description
      )
    }
    val maxColnum1Len = maxOpt(table.map(_._1.length), 0)
    val maxColnum2Len = maxOpt(table.map(_._2.length), 0)

    table.map { row =>
      padRight(row._1, maxColnum1Len) + padRight(row._2, maxColnum2Len) + row._3
    }.mkString("\n")
  }

  private def renderHelp(help: Config3#Help): String = {
    val maxParamLen = maxOpt(help.params.map(_.name.length), 0)
    val maxValueLen = Math.min(30, maxOpt(help.params.map(_.value.map(_.length).getOrElse(0)), 0))

    def renderParameter(p: Config3#HelpParam): String = {

      val namevalue = padRight(p.name, maxParamLen) +
        " " + padRight(p.value.getOrElse(""), maxValueLen) + " "

      namevalue + p.description.headOption.getOrElse("") +
        p.description.drop(1).map("\n" + fill(namevalue.length) + _).mkString

    }

    val sb = new mutable.StringBuilder()
    sb ++= help.help.mkString("\n")
    sb ++= "\n"
    sb ++= padRight("Name", maxParamLen + 1) + padRight("Value", maxValueLen) + " Description\n"

    sb ++= help.params.sortBy(p => (p.value.isDefined, p.name)).map(renderParameter).mkString("\n")

    sb.mkString
  }

  override def parse(args: Array[String]): Either[ArgumentsParseError, Config] = {
    var i = 0
    val sb = new mutable.StringBuilder()

    while (i < args.length) {
      val key = args(i)
      if (!key.startsWith("-")) return Left(ArgumentsParseError(i, key, "Parameter name must start with '-'"))

      if (i + 1 == args.length) return Left(ArgumentsParseError(i, key, "No value provided"))
      val value = args(i + 1)
      sb ++= s"${key.drop(1)}=$value\n"
      i += 2
    }

    Right(ConfigFactory.parseString(sb.mkString, ConfigParseOptions.defaults().setOriginDescription("cmdline")))
  }

  override def help(referenceConf: Config, filterKey: String => Boolean): Help = {
    import scala.collection.JavaConverters._

    val helpText = mutable.Buffer.empty[String]

    val params = walk(referenceConf.root(), filterKey).flatMap { case (path, v) =>
      v match {
        case o: ConfigObject =>
          helpText ++= o.origin().comments().asScala
          None

        case vv =>
          val value = if (vv.valueType() == ConfigValueType.NULL)
            None
          else Some(vv.render(ConfigRenderOptions.concise().setJson(false)))

          val help = vv.origin().comments().asScala.toSeq

          Some(HelpParam(path, value, help))
      }
    }

    Help(
      help = helpText.toSeq,
      params = params
    )

  }

  override def validate(
    referenceConf: Config,
    applicationConf: Config,
    filterKey: String => Boolean
  ): Seq[ConfigValidationError] = {
    val errors = mutable.Buffer.empty[ConfigValidationError]


    val reference = walk(referenceConf.root(), filterKey).toMap
    val application = walk(applicationConf.root(), filterKey).toMap

    //look for missing parameters
    reference.filter(_._2.valueType() == ConfigValueType.NULL).keys.foreach { requiredKey =>
      val hasError = application.get(requiredKey) match {
        case None => true
        case Some(v) if v.valueType() == ConfigValueType.NULL => true
        case _ => false
      }

      if (hasError) {
        errors += ConfigValidationError(None, Some(requiredKey), s"Parameter '$requiredKey' is required")
      }
    }

    //look for unknown parameters
    application.keys.foreach { applicationKey =>
      if (!reference.contains(applicationKey)) {
        errors += ConfigValidationError(Some(applicationKey), None, s"Provided parameter '$applicationKey' is not known")
      }
    }


    errors.toSeq
  }

  private[config3] def walk(root: ConfigValue, filterKey: String => Boolean): Seq[(String, ConfigValue)] = {
    val result = mutable.Buffer.empty[(String, ConfigValue)]

    def doWalk(path: String, r: ConfigValue): Unit = {
      if (filterKey(path)) {
        result += path -> r
      }

      r match {
        case o: ConfigObject => o.keySet().forEach { key =>
          val nextPath = if (path.isEmpty) key else path + "." + key
          doWalk(nextPath, o.get(key))
        }
        case _ =>
      }
    }

    doWalk("", root)

    result.toSeq
  }

  override def printConfig(
    referenceConf: Config,
    applicationConf: Config,
    filterKey: String => Boolean,
    isParamSecret: String => Boolean
  ): PrintedConfig = {
    val ref = walk(referenceConf.root(), filterKey).filterNot(_._2.isInstanceOf[ConfigObject])

    val lines = ref.map(_._1).sorted.map { key =>
      PrintedConfigParam(
        name = key,
        value = if (applicationConf.hasPath(key))
          applicationConf.getValue(key)
        else
          referenceConf.getValue(key)
      )
    }

    PrintedConfig(
      lines = lines,
      isParamSecret = isParamSecret
    )
  }
}
