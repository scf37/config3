# Config3
![Build status](https://travis-ci.org/scf37/config3.svg?branch=master)

This is small configuration library for Scala utilizing Typesafe Config.

## Design
- use typesafe config as backend
- ability to fail when provided config key is unknown/misspelled
- ability to print help on available parameters
- ability to define required and optional parameters
- ability to load parameters from: cmd line arguments, env, java props, files (typesafe)
- ability to print loaded parameters with origins on startup 

## Example output
#### Help on available parameters
```
This is myapp project
Parameters:

Name                  Value         Description
myapp.env                           environment to use. Try "dev" or "prod"
                                    if no env specified, things will go horribly wrong!
myapp.list            [1,2,3]       the list
myapp.listmap         [a=1,a=2,b=3] the listmap
myapp.map.value                     required map parameter
myapp.storage_root    "./files"     file storage root
myapp.web.dev         true          enable development mode
myapp.web.password                  application password
```
#### Loaded parameters with origins
```
myapp.env            "dev"                    system properties: 1
myapp.list           [1,2,3]                  reference.conf @ file:/opt/app/conf/reference.conf: 30
myapp.listmap        [{"a":1},{"a":2},{"b":3}]reference.conf @ file:/opt/app/conf/reference.conf: 33
myapp.map.value      {"x":1,"y":2,"z":3}      system properties: 2
myapp.storage_root   "./files"                reference.conf @ file:/opt/app/conf/reference.conf: 10
myapp.web.dev        true                     reference.conf @ file:/opt/app/conf/reference.conf: 20
myapp.web.password   ******                   system properties: 3
```

## Usage
### Update build.sbt
```
resolvers += "Scf37" at "https://dl.bintray.com/scf37/maven/"
libraryDependencies += "me.scf37.config3" %% "config3" % "1.0.0"
```

### Define reference.conf
`reference.conf` contains known configuration keys along with defaults and descriptions.

Example:
```hocon
#application description
#it will be shown on help screen
myapp {
  #application port with default value 8080
  port = 8080
  
  #environment name, required parameter, must be provided
  env = null
}
```
### Write some code
```scala
import com.typesafe.config.ConfigFactory
import me.scf37.config3.Config3

object Main {
  def main(args: Array[String]): Unit = {
    // load reference configuration
    // ConfigFactory.defaultReference() includes system properties and therefore unwanted
    val reference = ConfigFactory.parseResources("reference.conf")

    // there can be many reference.conf classpath-wise so we process keys for our application only
    def isAppConfigKey(key: String) = key.startsWith("myapp.")

    // print help if --help requested
    if (args.sameElements(Seq("--help"))) {
      println(Config3.help(reference, isAppConfigKey))
      System.exit(1)
    }

    // parse command line args -<arg name> <arg value>
    val cmdlineConfig = Config3.parse(args) match {
      case Left(error) =>
        println(error)
        System.exit(2)
        ???
      case Right(config) => config
    }

    // load config, falling back to system properties, environment, myapp.conf and reference config
    val config =
      cmdlineConfig
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.systemEnvironment())
        .withFallback(ConfigFactory.load("myapp.conf"))
        .withFallback(reference)

    // ensure all required parameters are set and we have no unknown/misspelled keys starting with myapp.
    val errors = Config3.validate(reference, config, isAppConfigKey)
    if (errors.nonEmpty) {
      errors.foreach(println)
      System.exit(2)
    }

    // print resolved configuration
    println(Config3.printConfig(reference, config, isAppConfigKey, _.contains("password")))
  }
}
```
