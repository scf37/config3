package me.scf37.config3

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import me.scf37.config3.Config3.PrintedConfig

/**
  * Example implementation of configuration loader, used in my projects
  */
object Config3Main {

  /**
    * Represents process early exit
    *
    * @param message error message
    * @param code exit code
    */
  case class EarlyExit(message: String, code: Int)

  /**
    * Load configuration, from all the sources, validate it and print result
    *
    * @param args command line arguments
    * @param appName application name
    * @param isParamSecret true if provided param should be masked when printing
    * @param isAppConfigKey true if provided param belongs to the app (should be validated)
    * @param appConfigFile application config file, if any. Will be ignored if missing
    * @return loaded configuration and printed configuration to output to logs
    */
  def config(
    args: Array[String],
    appName: String,
  )(
    isParamSecret: String => Boolean = _.contains("password"),
    isAppConfigKey: String => Boolean = _.startsWith(s"$appName."),
    appConfigFile: String = s"$appName.conf",

  ): Either[EarlyExit, (Config, PrintedConfig)] = {
    // load reference configuration
    // ConfigFactory.defaultReference() includes system properties and therefore unwanted
    val reference = ConfigFactory.parseResources("reference.conf")

    // print help if --help requested
    if (args.sameElements(Seq("--help"))) {
      return Left(EarlyExit(Config3.help(reference, isAppConfigKey).toString, 1))
    }

    // parse command line args -<arg name> <arg value>
    val cmdlineConfig = Config3.parse(args) match {
      case Left(error) => return Left(EarlyExit(error.toString, 2))
      case Right(config) => config
    }

    // load config, falling back to system properties, environment, appConfigFile and reference config
    val config =
      cmdlineConfig
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.systemEnvironment())
        .withFallback(ConfigFactory.parseResources(appConfigFile))
        .withFallback(reference)

    // ensure all required parameters are set and we have no unknown/misspelled keys.
    val errors = Config3.validate(reference, config, isAppConfigKey)
    if (errors.nonEmpty) {
      return Left(EarlyExit(errors.mkString(","), 2))
    }

    // print resolved configuration
    Right(config -> Config3.printConfig(reference, config, isAppConfigKey, isParamSecret))
  }
}
