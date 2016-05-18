resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.3")

// Add stage command to sbt
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.0")
