# Aiven's HTTP Sink Connector for Apache Kafka

![HTTP logo](docs/IETF-Badge-HTTP.png)

This is a sink [Kafka Connect](https://kafka.apache.org/documentation/#connect) connector that sends Kafka records over HTTP.

## How it works

The connector uses the POST HTTP method to deliver records.

The connector supports:
- authorization (static, OAuth2);
- setting HTTP headers;
- batching;
- delivery retries.

See the [configuration options](docs/sink-connector-config-options.rst) for details.

## Development

The connector supports Java 11 or later. It uses [Gradle](https://gradle.org/) for building and automating tasks.

To build a distribution:
```bash
./gradlew clean check distTar distZip
```
(check `build/distributions/` then).

Integration tests can be run with 
```bash
./gradlew integrationTest
```
and require [Docker](https://www.docker.com/) installed.

## License

This project is licensed under the [Apache License, Version 2.0](LICENSE).
