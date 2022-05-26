> **DISCLAIMER**: We use Google Analytics for sending anonymous usage information such as total time saved by tests.
> This information might help us to improve both Drill4J backend and client sides. It is used by the
> Drill4J team only and is not supposed for sharing with 3rd parties.
> You are able to turn off by set system property `analytic.disable = true` or send PATCH request `/api/analytic/toggle`.

[![Check](https://github.com/Drill4J/test2code-plugin/actions/workflows/check.yml/badge.svg)](https://github.com/Drill4J/test2code-plugin/actions/workflows/check.yml)
[![Release](https://github.com/Drill4J/test2code-plugin/actions/workflows/release.yml/badge.svg)](https://github.com/Drill4J/test2code-plugin/actions/workflows/release.yml)
[![Publish](https://github.com/Drill4J/test2code-plugin/actions/workflows/publish.yml/badge.svg)](https://github.com/Drill4J/test2code-plugin/actions/workflows/publish.yml)
[![License](https://img.shields.io/github/license/Drill4J/test2code-plugin)](LICENSE)
[![Visit the website at drill4j.github.io](https://img.shields.io/badge/visit-website-green.svg?logo=firefox)](https://drill4j.github.io/)
[![Telegram Chat](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/drill4j)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/Drill4J/test2code-plugin)
![GitHub contributors](https://img.shields.io/github/contributors/Drill4J/test2code-plugin)
![Lines of code](https://img.shields.io/tokei/lines/github/Drill4J/test2code-plugin)
![YouTube Channel Views](https://img.shields.io/youtube/channel/views/UCJtegUnUHr0bO6icF1CYjKw?style=social)

# Test2Code Plugin Overview

Minimize your regression suite via Test Impact Analytics.
For more information see in [documentation](https://drill4j.github.io/) or [core concepts](https://drill4j.github.io/docs/core-concepts)

# Development
## Modules

- **agent-part** & **agent-api** for working with [java-agent](https://github.com/Drill4J/java-agent).
  Probes are put down with the help of instrumentation in the source code and sends to **admin-part**
- **admin-part** & **api** for working with [admin](https://github.com/Drill4J/admin). 
  It analyzes probes and send metrics & statistics.
- **jacoco** - extend functionality of [JaCoCo](https://www.jacoco.org/jacoco/trunk/index.html)
- **load-tests**, **tests**, **benchmarks** for getting fast feedbacks
- **plugin-runner** - for local run by task 'application.run' 
- **cli** - Command line interface for testing class parsing. See more in [here](cli/README.md)
## Local Development

### How to Run

1. run [database](https://github.com/Drill4J/admin#database)
2. run With Gradle:
`./gradlew run`

### How to Debug

Change [params](https://drill4j.github.io/docs/configuration/launch-parameters)
, for example DRILL_AGENTS_SOCKET_TIMEOUT, for your needs.

#### Admin part

by remote debug with port 5006

#### Agent part

by remote debug with port 
