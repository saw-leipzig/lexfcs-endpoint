# LexFCS Solr Endpoint

This project contains a LexFCS endpoint Java servlet that relies on the project [`lexfcs-solr`](../lexfcs-solr/) as data backend.

## Build

Build [`fcs-endpoint.war`](target/fcs-endpoint.war) file for webapp deployment:

```bash
mvn clean package
```

## Run with docker

### Building

```bash
docker build -t lexfcs-solr-endpoint .
```

### Deploying

```bash
docker run --rm -it -p 8080:8080 --env-file .env lexfcs-solr-endpoint
```

## Run with docker compose

See configuration at [Configuration](#configuration).

Building:

```bash
docker compose build
```

... and deploying:

```bash
docker compose up [-d]
```

### Sample query

- `curl "http://localhost:8189/?query=*&queryType=lex&x-indent-response=2"`

## Configuration

Some Solr/resource configurations are being set using environment variables. See [`jetty-env.xml`](src/main/webapp/WEB-INF/jetty-env.xml) for details. You can set default values there.
For production set values in the `.env` file that is then loaded with the `docker-compose.yml` configuration.

| Name                   | Description                             |
| ---------------------- | --------------------------------------- |
| `DEFAULT_RESOURCE_PID` | the default resource pid to search with |
| `SOLR_URL`             | HTTP(s) URL to the Solr server          |
| `SOLR_USER`            | Solr user name                          |
| `SOLR_PASSWORD`        | Solr password                           |

Note that the `DEFAULT_RESOURCE_PID` should match with a resource PID in the [`endpoint-description.xml`](src/main/webapp/WEB-INF/endpoint-description.xml).

For further configuration of the SRU/FCS CLARIN libraries, see the [`web.xml`](src/main/webapp/WEB-INF/web.xml) that allows to change default result set sizes and other output flags.

The default SRU output can be configured in the [`sru-server-config.xml`](src/main/webapp/WEB-INF/sru-server-config.xml) in combination with [`web.xml`](src/main/webapp/WEB-INF/web.xml) (see parameter `eu.clarin.sru.server.database`).

### Endpoint Description

An important configuration is the [_Endpoint Description_ (in file `endpoint-description.xml`)](src/main/webapp/WEB-INF/endpoint-description.xml) that describes the FCS endpoint, i.e. supported search capabilities, result formats (Data Views) and resources with their description. It is being used by FCS clients to detect what resources are available and how to query them.

The current [description](src/main/webapp/WEB-INF/endpoint-description.xml) for this endpoint is already configured to (a) work with the example Solr Core `test_wiktionary-en` and (b) contain a full LexFCS compatible description (listing all available `<SupportedLexField>`, result formats `<SupportedDataView>`, the search capability `.../lex-search`) that shouldn't require any changes.

Additional resources can be based on the `<Resource pid="test_wiktionary-en">` entry.

- The `@pid` of the `<Resource>` should be a persistent and importantly unique identifier in the FCS infrastructure so that there are no conflicts with other endpoints and their resources.
- General information can be set in `<Title>`, `<Description>` and `<Institution>` fields. An entry with language `xml:lang="en"` is mandatory, with more information in other languages being optional.
- The `<LandingPageURI>` should point to the webpage showing or describing the resource.
- The languages in `<Languages>` describe the content languages of the resources that can be searched through.
- The `<AvailableLexFields>` should refer to the `@id`s of the `<SupportedLexField>` elements. They should match the data used in the Solr Core, i.e. documents with `_type = value_*`. Only fields listed here will be considered for searches. Simply listing all available Lex fields on the other hand is bad practice if there is not data for them and will result in unnecessary queries that likely will never have results. (And confuse users.)

## Development

### Run for local testing

Uses Jetty 10. See [`pom.xml`](pom.xml) --> plugin `jetty-maven-plugin`.

```bash
DEFAULT_RESOURCE_PID="test_wiktionary-en" mvn [package] jetty:run-war
```

NOTE: `jetty:run-war` uses built war file in [`target/`](target/) folder.

### Debug with VSCode

Add default debug setting `Attach by Process ID`, then start the jetty server with the following command, and start debugging in VSCode while it waits to attach.

```bash
# export configuration values, see section #Configuration
MAVEN_OPTS="-Xdebug -Xnoagent -Djava.compiler=NONE -agentlib:jdwp=transport=dt_socket,server=y,address=5005" mvn jetty:run-war
```
