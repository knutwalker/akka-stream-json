[![Build Status][ci-img]][ci]
[![Coverage][coverage-img]][coverage]
[![Maven][maven-img]][maven]
[![Apache License][license-img]][license]

# Akka Streams Json Support

This library provides Json support for stream based applications using [jawn](https://github.com/non/jawn)
as a parser. It supports all backends that jawn supports with support for [circe](https://github.com/travisbrown/circe) provided as a convience example.


## Installation

There are two main modules, `akka-stream-json` and `akka-http-json`.
`akka-stream-json` is the basis and provides the stream-based parser while
`akka-http-json` enabled support to use the desired json library as an Unmarshaller.


```
libraryDependencies ++= List(
  "de.knutwalker" %% "akka-stream-json" % "3.0.0",
  "de.knutwalker" %% "akka-http-json" % "3.0.0"
)
```

## Usage

The parser lives at `de.knutwalker.akka.stream.JsonStreamParser`

Use one of the constructor methods in the companion object to create the parser at 
various levels of abstraction, either a Stage, a Flow, or a Sink.
You just add the [jawn support facade](https://github.com/non/jawn#supporting-external-asts-with-jawn)
of your choice and you will can parsed into their respective Json AST.


For Http support, either `import de.knutwalker.akka.http.JsonSupport._`
or mixin `... with de.knutwalker.akka.http.JsonSupport`.

Given an implicit jawn facade, this enable you to decode into the respective Json AST
using the Akka HTTP marshalling framework. As jawn is only about parsing and does not abstract
over rendering, you'll only get an Unmarshaller.


### Circe

```
libraryDependencies ++= List(
  "de.knutwalker" %% "akka-stream-circe" % "3.0.0",
  "de.knutwalker" %% "akka-http-circe" % "3.0.0"
)
```

(Using circe 0.3.0)

Adding support for a specific framework is
[quite](support/stream-circe/src/main/scala/de/knutwalker/akka/stream/support/CirceStreamSupport.scala)
[easy](support/http-circe/src/main/scala/de/knutwalker/akka/http/support/CirceHttpSupport.scala).

These support modules allow you to directly marshall from/unmarshall into your data types
using circes `Decoder` and `Encoder` type classes.

Just mixin or import `de.knutwalker.akka.http.support.CirceHttpSupport` for Http
or pipe your `Source[ByteString, _].via(de.knutwalker.akka.stream.CirceStreamSupport.decode[A])`
to get a `Source[A, _]`.
 
This flow even supports parsing multiple json documents in whatever
fragmentation they may arrive, which is great for consuming stream/sse based APIs.


## Why jawn?

Jawn provides a nice interface for asynchronous parsing.
Most other Json marshalling provider will consume the complete entity
at first, convert it to a string and then start to parse.
With jawn, the json is incrementally parsed with each arriving data chunk,
using directly the underlying ByteBuffers without conversion.

## License

This code is open source software licensed under the Apache 2.0 License.

[ci-img]: https://img.shields.io/travis/knutwalker/akka-stream-json/master.svg
[coverage-img]: https://img.shields.io/codecov/c/github/knutwalker/akka-stream-json/master.svg
[maven-img]: https://img.shields.io/maven-central/v/de.knutwalker/akka-stream-json_2.11.svg?label=latest
[license-img]: https://img.shields.io/badge/license-APACHE_2-green.svg

[ci]: https://travis-ci.org/knutwalker/akka-stream-json
[coverage]: https://codecov.io/github/knutwalker/akka-stream-json
[maven]: http://search.maven.org/#search|ga|1|g%3A%22de.knutwalker%22%20AND%20%28a%3Aakka-stream-*_2.11%20OR%20a%3Aakka-http-*_2.11%29
[license]: https://www.apache.org/licenses/LICENSE-2.0
