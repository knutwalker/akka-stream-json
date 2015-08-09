# Akka HTTP Jawn+Argonaut Support

```
libraryDependencies ++= List(
  "de.knutwalker" %% "akka-http-jawn-argonaut" % "1.0.0"
)
```

## Usage

Either `import de.knutwalker.akkahttp.JawnArgonautSupport._`
or mixin `... with de.knutwalker.akkahttp.JawnArgonautSupport`.
Your entities still need an implicit `EncodeJson`, `DecodeJson`, or `CodecJson`.

### Prettify

Per default, the resulting json does not contain any unnecessary whitespace.
If you want to control the prettiness, bring an implicit `argonaut.PrettyParams` in scope.

```
implicit val lookIRenderWithSpaces = Argonaut.spaces2

// ...
// marshal(...)
```

## Why jawn?

Jawn provides a nice interface for asynchronous parsing.
Most other Json marshalling provider will consume the complete entity
at first, convert it to a string and then start to parse.
With jawn, the json is incrementally parsed with each arriving data chunk,
using directly the underlying ByteBuffers without conversion.
After the first value is successfully decoded, the parser will stop to
consume any further chunks.

## License

This code is open source software licensed under the Apache 2.0 License.
