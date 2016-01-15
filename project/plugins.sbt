libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.13"
resolvers += Resolver.url(
  "tut-plugin",
  url("http://dl.bintray.com/content/tpolecat/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.sbt"   % "sbt-git"         % "0.8.5")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"      % "1.5.0")
addSbtPlugin("com.jsuereth"       % "sbt-pgp"         % "1.0.0")
addSbtPlugin("com.github.gseitz"  % "sbt-release"     % "1.0.0")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"   % "1.3.3")
addSbtPlugin("org.xerial.sbt"     % "sbt-sonatype"    % "1.0")
addSbtPlugin("com.eed3si9n"       % "sbt-unidoc"      % "0.3.3")
